package co.casterlabs.quark.protocol.rtmp;

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
import co.casterlabs.quark.core.util.SocketConnection;
import co.casterlabs.quark.protocol.rtmp.egress.RTMPPullSessionListener;
import co.casterlabs.quark.protocol.rtmp.ingress.RTMPSessionProvider;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public class RTMPConnection extends ServerNetConnection implements AutoCloseable {
    private final RTMPSessionProvider provider = new RTMPSessionProvider(this);
    private final RTMPPullSessionListener listener = new RTMPPullSessionListener(this);

    public final SocketConnection conn;
    public final FastLogger logger;

    public ConnectArgs connectArgs;
    public RTMPState state = RTMPState.INITIALIZING;

    @Nullable
    public ServerNetStream stream;

    RTMPConnection(SocketConnection conn) throws IOException {
        super(new RTMPReader(ASReader.from(conn.in())), new RTMPWriter(new ASWriter(conn.out())));
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
        this.state = RTMPState.AUTHENTICATING;

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
        if (this.state == RTMPState.CLOSING) return;

        this.logger.debug("Closing...");
        this.state = RTMPState.CLOSING;

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
