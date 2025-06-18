package co.casterlabs.quark.ingest.protocols.rtmp;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import co.casterlabs.flv4j.EndOfStreamException;
import co.casterlabs.quark.Quark;
import co.casterlabs.quark.util.SocketConnection;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public class RTMPServer {
    private static final FastLogger LOGGER = new FastLogger();

    public static void start() {
        if (Quark.RTMP_PORT <= 0) return; // Disabled

        Thread.ofPlatform().name("RTMP Server").start(() -> {
            try (ServerSocket serverSocket = new ServerSocket()) {
                serverSocket.bind(new InetSocketAddress(Quark.RTMP_PORT));
                LOGGER.info("Listening on port %d...", Quark.RTMP_PORT);

                while (true) {
                    Socket sock = serverSocket.accept();

                    new Thread(() -> {
                        try (sock) {
                            sock.setTcpNoDelay(true);

                            try (
                                SocketConnection conn = new SocketConnection(sock);
                                _RTMPProvider rtmp = new _RTMPProvider(conn)) {
                                rtmp.run();
                            }
                        } catch (EndOfStreamException ignored) {
                            return;
                        } catch (Throwable t) {
                            LOGGER.warn("Uncaught:\n%s", t);
                        }
                    }).start();
                }
            } catch (Throwable t) {
                LOGGER.warn("Uncaught:\n%s", t);
            }
        });
    }

}
