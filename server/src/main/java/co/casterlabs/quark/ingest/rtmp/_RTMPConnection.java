package co.casterlabs.quark.ingest.rtmp;

import java.io.IOException;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.flv4j.actionscript.amf0.AMF0Type;
import co.casterlabs.flv4j.actionscript.amf0.AMF0Type.ObjectLike;
import co.casterlabs.flv4j.actionscript.amf0.Object0;
import co.casterlabs.flv4j.actionscript.io.ASReader;
import co.casterlabs.flv4j.actionscript.io.ASWriter;
import co.casterlabs.flv4j.rtmp.RTMPReader;
import co.casterlabs.flv4j.rtmp.RTMPWriter;
import co.casterlabs.flv4j.rtmp.net.ConnectArgs;
import co.casterlabs.flv4j.rtmp.net.NetStatus;
import co.casterlabs.flv4j.rtmp.net.rpc.CallError;
import co.casterlabs.flv4j.rtmp.net.server.ServerNetConnection;
import co.casterlabs.flv4j.rtmp.net.server.ServerNetStream;
import co.casterlabs.quark.util.SocketConnection;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

class _RTMPConnection extends ServerNetConnection implements AutoCloseable {
    private final _RTMPSessionProvider provider = new _RTMPSessionProvider(this);
    private final _RTMPSessionListener listener = new _RTMPSessionListener(this);

    final SocketConnection conn;
    final FastLogger logger;

    ConnectArgs connectArgs;
    _RTMPState state = _RTMPState.INITIALIZING;

    @Nullable
    ServerNetStream stream;

    _RTMPConnection(SocketConnection conn) throws IOException {
        super(new RTMPReader(new ASReader(conn.in())), new RTMPWriter(new ASWriter(conn.out())));
        this.conn = conn;
        this.logger = new FastLogger(conn.socket().toString());

        this.onCall = (method, args) -> {
            if (method.equals("FCUnpublish")) {
                this.logger.debug("Stream closed by client.");
                close(true);
            }
            return null;
        };
    }

    /* ---------------- */
    /*       RTMP       */
    /* ---------------- */

    @Override
    public ObjectLike connect(ConnectArgs args) throws IOException, InterruptedException, CallError {
        this.logger.debug(args);
        this.connectArgs = args;

        // "Allow" the url as long as it's present, we'll validate it during publish().
        this.state = _RTMPState.AUTHENTICATING;

        return Object0.EMPTY;
    }

    @Override
    public ServerNetStream createStream(AMF0Type arg) throws IOException, InterruptedException, CallError {
        if (this.streams().size() > 0) {
            throw new CallError(NetStatus.NS_CONNECT_FAILED);
        }

        return this.stream = new ServerNetStream() {
            @Override
            public void publish(String key, String type) throws IOException, InterruptedException {
                provider.publish(key, type);
            }

            @Override
            public void play(String name, double start, double duration, boolean reset) throws IOException, InterruptedException {
                listener.play(name);
            }

            @Override
            public void deleteStream() throws IOException, InterruptedException {
                logger.debug("Stream closed by client.");
                close(true);
            }
        };
    }

    public void close(boolean graceful) {
        if (this.state == _RTMPState.CLOSING) return;

        this.logger.debug("Closing...");
        this.state = _RTMPState.CLOSING;

        this.provider.closeConnection(graceful);
        this.listener.closeConnection();

        this.setStatus(NetStatus.NC_CONNECT_CLOSED);

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

}
