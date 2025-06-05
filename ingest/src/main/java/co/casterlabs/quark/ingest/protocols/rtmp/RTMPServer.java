package co.casterlabs.quark.ingest.protocols.rtmp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import co.casterlabs.flv4j.EndOfStreamException;
import co.casterlabs.quark.ingest.util.SocketConnection;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public class RTMPServer {
    private static final FastLogger LOGGER = new FastLogger();

    public static void start(int port) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(port));
            LOGGER.info("Listening on port %d...", port);

            while (true) {
                Socket sock = serverSocket.accept();

                new Thread(() -> {
                    try (sock) {
                        sock.setTcpNoDelay(true);

                        try (
                            SocketConnection conn = new SocketConnection(sock);
                            _RTMPConnection rtmp = new _RTMPConnection(conn)) {
                            rtmp.run();
                        }
                    } catch (EndOfStreamException ignored) {
                        return;
                    } catch (Throwable t) {
                        LOGGER.warn("Uncaught:\n%s", t);
                    }
                }).start();
            }
        }
    }

}
