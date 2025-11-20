package co.casterlabs.quark.protocol.webrtc.ingress;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;

import co.casterlabs.flv4j.flv.FLVFileHeader;
import co.casterlabs.flv4j.flv.muxing.NonSeekableFLVDemuxer;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.flv4j.flv.tags.FLVTagType;
import co.casterlabs.flv4j.flv.tags.video.FLVVideoCodec;
import co.casterlabs.flv4j.flv.tags.video.FLVVideoFrameType;
import co.casterlabs.flv4j.flv.tags.video.FLVVideoPayload;
import co.casterlabs.flv4j.flv.tags.video.data.AVCVideoData;
import co.casterlabs.flv4j.flv.tags.video.data.AVCVideoData.AVCPacketType;
import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.quark.core.session.SessionProvider;
import co.casterlabs.quark.core.util.ArrayView;
import co.casterlabs.quark.core.util.PublicPortRange;
import co.casterlabs.quark.core.util.RandomIdGenerator;
import co.casterlabs.quark.protocol.webrtc.AVCDecoderConfigurationRecord;
import co.casterlabs.quark.protocol.webrtc.WebRTCBinPrep;
import co.casterlabs.quark.protocol.webrtc.WebRTCEnv;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.element.JsonObject;
import lombok.SneakyThrows;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public class WebRTCProvider implements SessionProvider {
    private static final ThreadFactory TF = Quark.HEAVY_IO_THREAD_BUILDER.name("WebRTC Provider", 0).factory();

    public static final Map<String, WebRTCProvider> providers = new ConcurrentHashMap<>();

    private final int assignedPort = PublicPortRange.acquirePort();
    public final String resourceId = RandomIdGenerator.generate(24);

    public final FastLogger logger;
    private final Process proc;
    private Session session;

    private long dtsOffset;
    private boolean jammed = false;

    private JsonObject metadata;

    public final CompletableFuture<JsonObject> sdpAnswer = new CompletableFuture<>();

    private final Demuxer demuxer = new Demuxer();

    public WebRTCProvider(String sdpOffer) throws IOException {
        providers.put(this.resourceId, this);
        this.logger = new FastLogger(String.format("WebRTC Ingress [%s]", this.resourceId));

        this.metadata = new JsonObject()
            .put("type", "WEBRTC")
            .put("dtsOffset", this.dtsOffset)
            .put("state", "checking");

        this.proc = new ProcessBuilder()
            .command(
                WebRTCBinPrep.INGEST_BINARY.getAbsolutePath(),
                WebRTCEnv.WEBRTC_OVERRIDE_ADDRESS,
                String.valueOf(this.assignedPort)
            )
            .redirectOutput(Redirect.PIPE)
            .redirectError(Redirect.PIPE)
            .redirectInput(Redirect.PIPE)
            .start();

        TF.newThread(() -> {
            try {
                this.demuxer.start(this.proc.getInputStream());
            } catch (IOException ignored) {} finally {
                this.close(true);
            }
        }).start();

        TF.newThread(() -> {
            try (Scanner scanner = new Scanner(this.proc.getErrorStream())) {
                boolean hasGottenAnswer = false;

                String line;
                while ((line = scanner.nextLine()) != null) {
                    if (line.startsWith("state:")) {
                        String state = line.substring("state:".length());
                        this.metadata.put("state", state);
                    } else if (!hasGottenAnswer && line.startsWith("answer:")) {
                        hasGottenAnswer = true;
                        String answer = line.substring("answer:".length());
                        this.sdpAnswer.complete(Rson.DEFAULT.fromJson(answer, JsonObject.class));
                    } else {
                        this.logger.debug(line);
                    }
                }
            } catch (IOException ignored) {} finally {
                this.close(true);
            }
        }).start();

        this.proc.onExit().thenRun(() -> {
            PublicPortRange.releasePort(this.assignedPort);
        });

        this.proc.getOutputStream().write(
            new JsonObject()
                .put("type", "offer")
                .put("sdp", sdpOffer)
                .toString()
                .getBytes(StandardCharsets.UTF_8)
        );
        this.proc.getOutputStream().write('\n');
        this.proc.getOutputStream().flush();
    }

    public void init(Session session) {
        this.session = session;
        this.dtsOffset = session.prevDts;
        this.metadata
            .put("dtsOffset", this.dtsOffset);
    }

    @Override
    public JsonObject metadata() {
        return this.metadata;
    }

    @Override
    public void jam() {
        this.jammed = true;
        this.close(true);
    }

    @Override
    public void close(boolean graceful) {
        providers.remove(this.resourceId);

        if (!this.sdpAnswer.isDone()) {
            this.sdpAnswer.completeExceptionally(new IOException("Connection closed or timed out before SDP answer was received."));
        }

        this.proc.destroy();

        if (!this.jammed && this.session != null) {
            this.session.close(graceful);
        }
    }

    private class Demuxer extends NonSeekableFLVDemuxer {
        private ArrayView lastSPS = ArrayView.EMPTY;
        private ArrayView lastPPS = ArrayView.EMPTY;

        @Override
        protected void onHeader(FLVFileHeader header) {} // ignore.

        @SneakyThrows
        @Override
        protected void onTag(long previousTagSize, FLVTag tag) {
            if (jammed) return; // Just in case.

            long newTagTimestamp = (tag.timestamp() + dtsOffset) & 0xFFFFFFFFL; // rewrite with our offset

            if (WebRTCEnv.EXP_WHIP_AVC_AUTO_RECONFIG && // We have a pretty Naive implementation for this feature.
                tag.data() instanceof FLVVideoPayload video &&
                video.codec() == FLVVideoCodec.H264 &&
                video.frameType() == FLVVideoFrameType.KEY_FRAME &&
                video.data() instanceof AVCVideoData avc &&
                avc.type() == AVCPacketType.NALU &&
                avc.data().length > 5) {
                this.checkNALUs(newTagTimestamp, avc.data());
            }

            tag = new FLVTag(
                tag.type(),
                newTagTimestamp,
                tag.streamId(),
                tag.data()
            );

            session.tag(tag);
        }

        private void checkNALUs(long timestamp, byte[] nalus) {
            // We need to sniff NALUs to see if it's an SPS/PPS packet. If so, we need to
            // check our known SPS/PPS to see if it's different, and if so, send a new
            // AVCDecoderConfigurationRecord.

            ArrayView foundSPS = null;
            ArrayView foundPPS = null;

            for (int idx = 0; idx < nalus.length;) {
                int size = ((nalus[idx] & 0xFF) << 24) |
                    ((nalus[idx + 1] & 0xFF) << 16) |
                    ((nalus[idx + 2] & 0xFF) << 8) |
                    (nalus[idx + 3] & 0xFF);

                int naluType = nalus[idx + 4] & 0x1F;

                if (naluType == 7) { // SPS
                    foundSPS = new ArrayView(nalus, idx + 4, size);
                } else if (naluType == 8) { // PPS
                    foundPPS = new ArrayView(nalus, idx + 4, size);
                }

                idx += 4; // advance past size
                idx += size; // advance past NALU
            }

            if (foundSPS == null || foundPPS == null) {
                return; // No SPS/PPS found.
            }
            if (foundSPS.equals(this.lastSPS) && foundPPS.equals(this.lastPPS)) {
                return; // No change.
            }

            logger.debug("sps==sps? %b", foundSPS.equals(this.lastSPS));
            logger.debug("pps==pps? %b", foundPPS.equals(this.lastPPS));

            this.lastSPS = foundSPS;
            this.lastPPS = foundPPS;

            byte[] config = AVCDecoderConfigurationRecord.from(foundSPS, foundPPS);

            logger.debug("Found new SPS/PPS, sending a new sequence header...");
            session.tag(
                new FLVTag(
                    FLVTagType.VIDEO, timestamp, 0, new FLVVideoPayload(
                        FLVVideoFrameType.KEY_FRAME.id,
                        FLVVideoCodec.H264.id,
                        new AVCVideoData(
                            AVCPacketType.SEQUENCE_HEADER.id,
                            0,
                            config
                        )
                    )
                )
            );
        }

    }

}
