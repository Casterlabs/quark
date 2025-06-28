package co.casterlabs.quark.ingest.rtmp;

import java.io.IOException;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.flv4j.actionscript.amf0.AMF0Type;
import co.casterlabs.flv4j.actionscript.amf0.AMF0Type.ObjectLike;
import co.casterlabs.flv4j.actionscript.amf0.ECMAArray0;
import co.casterlabs.flv4j.actionscript.amf0.Object0;
import co.casterlabs.flv4j.actionscript.amf0.String0;
import co.casterlabs.flv4j.actionscript.io.ASReader;
import co.casterlabs.flv4j.actionscript.io.ASWriter;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.flv4j.flv.tags.FLVTagType;
import co.casterlabs.flv4j.flv.tags.script.FLVScriptTagData;
import co.casterlabs.flv4j.rtmp.RTMPReader;
import co.casterlabs.flv4j.rtmp.RTMPWriter;
import co.casterlabs.flv4j.rtmp.chunks.RTMPMessage;
import co.casterlabs.flv4j.rtmp.chunks.RTMPMessageAudio;
import co.casterlabs.flv4j.rtmp.chunks.RTMPMessageData0;
import co.casterlabs.flv4j.rtmp.chunks.RTMPMessageVideo;
import co.casterlabs.flv4j.rtmp.net.ConnectArgs;
import co.casterlabs.flv4j.rtmp.net.NetStatus;
import co.casterlabs.flv4j.rtmp.net.rpc.CallError;
import co.casterlabs.flv4j.rtmp.net.server.ServerNetConnection;
import co.casterlabs.flv4j.rtmp.net.server.ServerNetStream;
import co.casterlabs.quark.Quark;
import co.casterlabs.quark.session.FLVData;
import co.casterlabs.quark.session.Session;
import co.casterlabs.quark.session.SessionProvider;
import co.casterlabs.quark.util.SocketConnection;
import co.casterlabs.quark.util.WallclockTS;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

class _RTMPProvider extends ServerNetConnection implements SessionProvider, AutoCloseable {
    private final SocketConnection conn;

    private final FastLogger logger;

    private final WallclockTS dts = new WallclockTS();
    private long ptsOffset = 0;

    private @Nullable ServerNetStream stream;

    private State state = State.INITIALIZING;
    private Session session;
    private String handshakeUrl = null;
    private boolean jammed = false;

    _RTMPProvider(SocketConnection conn) throws IOException {
        super(new RTMPReader(new ASReader(conn.in())), new RTMPWriter(new ASWriter(conn.out())));
        this.conn = conn;
        this.logger = new FastLogger(conn.socket().toString());
    }

    /* ---------------- */
    /*       RTMP       */
    /* ---------------- */

    @Override
    public ObjectLike connect(ConnectArgs args) throws IOException, InterruptedException, CallError {
        this.logger.debug(args);
        this.handshakeUrl = args.tcUrl();

        // "Allow" the url as long as it's present, we'll validate it during publish().
        this.state = State.AUTHENTICATING;

        return Object0.EMPTY;
    }

    @Override
    public ServerNetStream createStream(AMF0Type arg) throws IOException, InterruptedException, CallError {
        if (this.streams().size() > 0) {
            throw new CallError(NetStatus.NS_CONNECT_FAILED);
        }

        return this.stream = new ServerNetStream() {
            {
                this.onMessage = _RTMPProvider.this::onMessage;
            }

            @Override
            public void publish(String key, String type) throws IOException, InterruptedException {
                if (state != State.AUTHENTICATING) {
                    logger.debug("Closing, client sent publish() during state %s", state);
                    this.setStatus(NetStatus.NS_PUBLISH_FAILED);
                    close(true);
                    return;
                }

                logger.debug("Authenticating with %s @ %s", key, handshakeUrl);
                session = Quark.authenticateSession(
                    _RTMPProvider.this,
                    conn.socket().getInetAddress().getHostAddress(),
                    handshakeUrl,
                    key
                );

                if (session == null) {
                    logger.debug("Closing, stream rejected.");
                    this.setStatus(NetStatus.NS_PUBLISH_BADNAME);
                    close(true);
                } else {
                    // Allow it!
                    dts.offset(session.prevDts);
                    ptsOffset = session.prevPts;

                    logger.debug("Stream allowed.");
                    state = State.RUNNING;
                    this.setStatus(NetStatus.NS_PUBLISH_START);
                }
            }

            @Override
            public void deleteStream() throws IOException, InterruptedException {
                logger.debug("Stream closed by client.");
                close(true);
            }

        };
    }

    private void onMessage(int timestamp, RTMPMessage message) {
        if (message instanceof RTMPMessageAudio audio) {
            this.handleAudio(timestamp, audio);
        } else if (message instanceof RTMPMessageVideo video) {
            this.handleVideo(timestamp, video);
        } else if (message instanceof RTMPMessageData0 data) {
            if (this.jammed) return; // Just in case.
            if (data.arguments().size() != 3) {
                return;
            }
            if (data.arguments().get(0) instanceof String0 str) {
                if (str.value().equals("@setDataFrame")) {
                    String0 method = (String0) data.arguments().get(1);
                    ECMAArray0 value = (ECMAArray0) data.arguments().get(2);

                    FLVScriptTagData payload = new FLVScriptTagData(method.value(), value);
                    FLVTag tag = new FLVTag(FLVTagType.SCRIPT, 0, 0, payload);
                    this.logger.debug("Got script sequence: %s", tag);
                    this.session.data(new FLVData(timestamp, tag));
                }
            }
            return;
        } else {
            this.logger.trace("Unhandled packet: %s", message);
        }

    }

    private void handleAudio(int timestamp, RTMPMessageAudio message) {
        if (this.jammed) return; // Just in case.

//        this.logger.trace("Audio packet: %s", read);

        if (this.session == null || this.state != State.RUNNING) {
            this.logger.debug("Closing, client sent tag during state %s", this.state);
            this.close(true);
            return;
        }

        FLVTag tag = new FLVTag(FLVTagType.AUDIO, this.dts.next(), 0, message.payload());

        this.session.data(new FLVData(timestamp + this.ptsOffset, tag));
    }

    private void handleVideo(int timestamp, RTMPMessageVideo message) {
        if (this.jammed) return; // Just in case.

//        this.logger.trace("Video packet: %s", read);

        if (this.session == null || this.state != State.RUNNING) {
            this.logger.debug("Closing, client sent tag during state %s", this.state);
            this.close(true);
            return;
        }

        FLVTag tag = new FLVTag(FLVTagType.VIDEO, this.dts.next(), 0, message.payload());

        this.session.data(new FLVData(timestamp + this.ptsOffset, tag));
    }

    @Override
    public void close(boolean graceful) {
        if (this.state == State.CLOSING) return;

        this.logger.debug("Closing...");
        this.state = State.CLOSING;

        if (this.stream != null) {
            this.stream.setStatus(NetStatus.NS_UNPUBLISH_SUCCESS);
        }
        this.setStatus(NetStatus.NC_CONNECT_CLOSED);

        if (this.session != null && !this.jammed) {
            try {
                this.session.close(graceful);
            } catch (Throwable t) {
                this.logger.warn("Exception whilst ending session, this could be bad!\n%s", t);
            }
        }

        try {
            this.conn.close();
        } catch (IOException e) {
            this.logger.debug(e);
        }
        this.logger.debug("Closed!");
    }

    @Override
    public void close() {
        this.close(false);
    }

    /* ---------------- */
    /*  Quark Session   */
    /* ---------------- */

    @Override
    public void jam() {
        this.jammed = true;
        this.logger.debug("Jammed!");
        this.close(true);
    }

    /* ---------------- */
    /*      Misc.       */
    /* ---------------- */

    private static enum State {
        INITIALIZING,
        AUTHENTICATING,
        RUNNING,
        CLOSING,
    }

}
