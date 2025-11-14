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
import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.quark.core.session.SessionProvider;
import co.casterlabs.quark.core.util.PortRange;
import co.casterlabs.quark.core.util.RandomIdGenerator;
import co.casterlabs.quark.protocol.webrtc.WebRTCBinPrep;
import co.casterlabs.quark.protocol.webrtc.WebRTCEnv;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.element.JsonObject;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public class WebRTCProvider implements SessionProvider {
    private static final ThreadFactory TF = Quark.HEAVY_IO_THREAD_BUILDER.name("WebRTC Provider", 0).factory();

    public static final Map<String, WebRTCProvider> providers = new ConcurrentHashMap<>();

    private final int assignedPort = PortRange.acquirePort();
    public final String resourceId = RandomIdGenerator.generate(24);

    public final FastLogger logger;
    private final Process proc;
    private Session session;

    private long dtsOffset;
    private boolean jammed = false;

    private JsonObject metadata;

    public final CompletableFuture<JsonObject> sdpAnswer = new CompletableFuture<>();

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
                WebRTCEnv.WHIP_OVERRIDE_ADDRESS,
                String.valueOf(this.assignedPort)
            )
            .redirectOutput(Redirect.PIPE)
            .redirectError(Redirect.PIPE)
            .redirectInput(Redirect.PIPE)
            .start();

        TF.newThread(() -> {
            try {
                new NonSeekableFLVDemuxer() {
                    @Override
                    protected void onHeader(FLVFileHeader header) {} // ignore.

                    @Override
                    protected void onTag(long previousTagSize, FLVTag tag) {
                        if (jammed) return; // Just in case.

                        tag = new FLVTag(
                            tag.type(),
                            (tag.timestamp() + dtsOffset) & 0xFFFFFFFFL, // rewrite with our offset
                            tag.streamId(),
                            tag.data()
                        );

                        session.tag(tag);
                    }
                }.start(this.proc.getInputStream());
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
            PortRange.releasePort(this.assignedPort);
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

}
