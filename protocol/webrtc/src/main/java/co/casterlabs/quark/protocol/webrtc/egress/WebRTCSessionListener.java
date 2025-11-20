package co.casterlabs.quark.protocol.webrtc.egress;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;

import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.quark.core.session.listeners.FLVProcessSessionListener;
import co.casterlabs.quark.core.session.listeners.StreamFilter;
import co.casterlabs.quark.core.util.PrivatePortRange;
import co.casterlabs.quark.core.util.PublicPortRange;
import co.casterlabs.quark.core.util.RandomIdGenerator;
import co.casterlabs.quark.protocol.webrtc.WebRTCBinPrep;
import co.casterlabs.quark.protocol.webrtc.WebRTCEnv;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.element.JsonObject;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public class WebRTCSessionListener extends FLVProcessSessionListener implements Closeable {
    private static final ThreadFactory TF = Quark.HEAVY_IO_THREAD_BUILDER.name("WebRTC Listener", 0).factory();

    public static final Map<String, WebRTCSessionListener> listeners = new ConcurrentHashMap<>();

    public final String resourceId = RandomIdGenerator.generate(24);

    private final int[] assignedPorts;
    private final Session session;
    public final FastLogger logger;

    private final JsonObject metadata = new JsonObject().put("state", "checking");

    public final CompletableFuture<JsonObject> sdpAnswer = new CompletableFuture<>();

    public WebRTCSessionListener(Session session, String sdpOffer, int[] assignedPorts, StreamFilter filter) throws IOException {
        super(
            filter,
            Redirect.INHERIT, Redirect.PIPE,
            WebRTCBinPrep.EGRESS_BINARY.getAbsolutePath(),
            WebRTCEnv.WEBRTC_OVERRIDE_ADDRESS,
            String.valueOf(assignedPorts[0]),
            String.valueOf(assignedPorts[1]),
            String.valueOf(assignedPorts[2]),
            Base64.getEncoder().encodeToString(
                new JsonObject()
                    .put("type", "offer")
                    .put("sdp", sdpOffer)
                    .toString()
                    .getBytes(StandardCharsets.UTF_8)
            )
        );
        this.session = session;
        this.assignedPorts = assignedPorts;
        this.logger = new FastLogger(String.format("WebRTC Ingress [%s]", this.resourceId));

        listeners.put(this.resourceId, this);

        TF.newThread(() -> {
            try (Scanner scanner = new Scanner(this.stderr())) {
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
                this.close();
            }
        }).start();

        this.onExit(() -> {
            PublicPortRange.releasePort(this.assignedPorts[0]);
            PrivatePortRange.releasePort(this.assignedPorts[1]);
            PrivatePortRange.releasePort(this.assignedPorts[2]);
            this.close();
        });
    }

    @Override
    public String type() {
        return "WHEP_EGRESS";
    }

    @Override
    public String fid() {
        return this.resourceId;
    }

    @Override
    public JsonObject metadata() {
        return this.metadata;
    }

    @Override
    public void onClose(Session session) {
        this.close();
    }

    @Override
    public void close() {
        listeners.remove(this.resourceId);

        if (!this.sdpAnswer.isDone()) {
            this.sdpAnswer.completeExceptionally(new IOException("Connection closed or timed out before SDP answer was received."));
        }

        this.session.removeListener(this);
        this.destroyProc();
    }

}
