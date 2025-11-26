package co.casterlabs.quark.protocol.rtmp.egress;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.Arrays;

import javax.net.ssl.SSLSocketFactory;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.flv4j.actionscript.amf0.Null0;
import co.casterlabs.flv4j.actionscript.amf0.String0;
import co.casterlabs.flv4j.actionscript.io.ASReader;
import co.casterlabs.flv4j.actionscript.io.ASWriter;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.flv4j.flv.tags.audio.FLVAudioTagData;
import co.casterlabs.flv4j.flv.tags.script.FLVScriptTagData;
import co.casterlabs.flv4j.flv.tags.video.FLVStandardVideoTagData;
import co.casterlabs.flv4j.flv.tags.video.FLVVideoFrameType;
import co.casterlabs.flv4j.flv.tags.video.FLVVideoTagData;
import co.casterlabs.flv4j.rtmp.RTMPReader;
import co.casterlabs.flv4j.rtmp.RTMPWriter;
import co.casterlabs.flv4j.rtmp.chunks.RTMPMessageAudio;
import co.casterlabs.flv4j.rtmp.chunks.RTMPMessageData0;
import co.casterlabs.flv4j.rtmp.chunks.RTMPMessageVideo;
import co.casterlabs.flv4j.rtmp.net.ConnectArgs;
import co.casterlabs.flv4j.rtmp.net.NetStatus;
import co.casterlabs.flv4j.rtmp.net.NetStream;
import co.casterlabs.flv4j.rtmp.net.client.ClientNetConnection;
import co.casterlabs.flv4j.rtmp.net.rpc.CallError;
import co.casterlabs.flv4j.rtmp.net.rpc.RPCPromise;
import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.session.FLVSequence;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.quark.core.session.SessionListener;
import co.casterlabs.quark.core.session.listeners.StreamFilter;
import co.casterlabs.rakurai.json.element.JsonObject;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

/**
 * A sender-initiated (push) connection.
 */
public class RTMPPushSessionListener extends SessionListener {
    private final Session session;
    private final String fid;
    private final JsonObject metadata;
    private final StreamFilter filter;

    private final URI uri;
    private final String tcURL;
    private final String key;

    private Outbound outbound;
    private volatile boolean isClosed = false;

    public RTMPPushSessionListener(Session session, StreamFilter filter, String fid, String address, String key) {
        this.session = session;
        this.fid = fid;
        this.metadata = new JsonObject()
            .put("address", address);
        this.filter = filter;

        this.uri = URI.create(address);
        this.tcURL = address;
        this.key = key;

        Quark.HEAVY_IO_THREAD_BUILDER.name("RTMP Egress Kickstart", 0).start(() -> {
            try {
                this.reconnect();
            } catch (Throwable t) {
                if (Quark.DEBUG) {
                    t.printStackTrace();
                }
            }
        });
    }

    private void reconnect() throws IOException, InterruptedException, CallError {
        if (this.isClosed) return;

        final String app = this.uri.getPath().isEmpty() ? "app" : this.uri.getPath().substring(1); // strip leading `/`
        final String protocol = this.uri.getScheme().toLowerCase();
        final String host = this.uri.getHost();
        final int port = this.uri.getPort() == -1 ? //
            switch (protocol) {
                case "rtmp" -> 1935;
                case "rtmps" -> 443;
                default -> throw new IllegalArgumentException("Unsupported protocol: " + protocol);
            } : //
            this.uri.getPort();

        InetSocketAddress address = new InetSocketAddress(host, port);

        Socket sock = switch (protocol) {
            case "rtmp" -> new Socket();
            case "rtmps" -> SSLSocketFactory.getDefault().createSocket();
            default -> throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        };
        sock.connect(address);

        this.outbound = new Outbound(sock, app, this.tcURL, this.key);
    }

    @Override
    public String type() {
        return "RTMP_EGRESS";
    }

    @Override
    public String fid() {
        return this.fid;
    }

    @Override
    public JsonObject metadata() {
        return this.metadata;
    }

    @Override
    public void onClose(Session session) {
        this.isClosed = true;
        if (this.outbound != null) {
            this.outbound.close();
        }
    }

    private class Outbound extends ClientNetConnection {
        private final Socket socket;
        private NetStream ns;

        private final SessionListener listener = new SessionListener() {
            private static final String0 SET_DATA_FRAME = new String0("@setDataFrame");

            private boolean hasGottenSequence = false;
            private long offset = 0;

            private void writeTag(FLVTag tag) {
                tag = filter.transform(tag);
                if (tag == null) return; // tag should be dropped!

                try {
                    int ts24 = (int) (tag.timestamp() - this.offset) & 0xFFFFFF;

                    if (tag.data() instanceof FLVVideoTagData video) {
                        ns.sendMessage(
                            ts24,
                            new RTMPMessageVideo(video)
                        );
                    } else if (tag.data() instanceof FLVAudioTagData audio) {
                        ns.sendMessage(
                            ts24,
                            new RTMPMessageAudio(audio)
                        );
                    } else if (tag.data() instanceof FLVScriptTagData script) {
                        ns.sendMessage(
                            ts24,
                            new RTMPMessageData0(
                                Arrays.asList(
                                    SET_DATA_FRAME,
                                    script.method(),
                                    script.value()
                                )
                            )
                        );
                    }
                } catch (IOException | InterruptedException e) {
                    if (Quark.DEBUG) {
                        e.printStackTrace();
                    }
                    try {
                        socket.close();
                    } catch (IOException ignored) {}
                }
            }

            @Override
            public void onSequence(Session session, FLVSequence seq) {
                this.hasGottenSequence = true;

                for (FLVTag tag : seq.tags()) {
                    this.writeTag(tag);
                }
            }

            @Override
            public void onTag(Session session, FLVTag tag) {
                if (!this.hasGottenSequence) return;

                if (this.offset == -1) {
                    boolean sessionHasVideo = session.info.video.length > 0;
                    boolean isVideoKeyFrame = tag.data() instanceof FLVStandardVideoTagData video && video.frameType() == FLVVideoFrameType.KEY_FRAME;

                    if (!sessionHasVideo || isVideoKeyFrame) {
                        this.offset = tag.timestamp();
                        FastLogger.logStatic(LogLevel.DEBUG, "Got offset: %d", -this.offset);
                        // fall through and write it out.
                    } else {
//                        FastLogger.logStatic(LogLevel.DEBUG, "Discarding tag before offset: %s", tag);
                        return;
                    }
                }

                this.writeTag(tag);
            }

            @Override
            public @Nullable String type() {
                return null; // Internal :^)
            }

            @Override
            public void onClose(Session session) {} // Handled above.

        };

        private Outbound(Socket socket, @Nullable String app, String tcUrl, String key) throws IOException, InterruptedException, CallError {
            super(
                new RTMPReader(ASReader.from(socket.getInputStream())),
                new RTMPWriter(new ASWriter(socket.getOutputStream()))
            );

            this.socket = socket;

            this.connect(
                new ConnectArgs()
                    .app(app)
                    .type("nonprivate")
                    .flashVersion("FMLE/3.0 (compatible; FMSc/1.0)") // shrug?
                    .swfUrl(tcUrl)
                    .tcUrl(tcUrl)
            );

            this.call("releaseStream", Null0.INSTANCE, new String0(key));
            this.call("FCPublish", Null0.INSTANCE, new String0(key));

            this.ns = this.createStream().await();

            RPCPromise<Void> pubPromise = new RPCPromise<Void>((handle) -> {
                this.ns.onStatus = (status) -> {
                    if (status.code().equals(NetStatus.NS_PUBLISH_START.code())) {
                        handle.resolve(null);
                    } else {
                        handle.reject(new IllegalStateException("Got status: " + status.code()));
                    }
                };
            });

            this.ns.publish(key, "live");

            pubPromise.await();

            session.addAsyncListener(this.listener);
        }

        public void close() {
            session.removeListener(this.listener); // don't send video after calling deleteStream/FCUnpublish.

            try {
                this.ns.call("FCUnpublish", Null0.INSTANCE, new String0(key));
            } catch (Throwable ignored) {}
            try {
                this.ns.deleteStream();
            } catch (Throwable ignored) {}

            try {
                this.socket.close();
            } catch (Throwable ignored) {}
        }

        @Override
        public void onClose(@Nullable Throwable reason) {
            if (Quark.DEBUG) {
                reason.printStackTrace();
            }
            outbound = null;

            session.removeListener(this.listener);

            Quark.HEAVY_IO_THREAD_BUILDER.name("RTMP Egress Restart", 0).start(() -> {
                try {
                    Thread.sleep(5000); // Be gentle :)
                    reconnect();
                } catch (IOException | InterruptedException | CallError ignored) {}
            });
        }

    }

}
