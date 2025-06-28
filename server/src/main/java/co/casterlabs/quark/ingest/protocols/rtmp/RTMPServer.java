package co.casterlabs.quark.ingest.protocols.rtmp;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ThreadFactory;

import co.casterlabs.flv4j.EndOfStreamException;
import co.casterlabs.quark.Quark;
import co.casterlabs.quark.util.SocketConnection;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public class RTMPServer {
    private static final FastLogger LOGGER = new FastLogger();

    private static final ThreadFactory RTMP_CONNECTION_TF = Thread.ofPlatform().name("RTMP Connection", 0).factory();
    private static final ThreadFactory RTMP_MISC_TF = Thread.ofVirtual().name("RTMP Misc", 0).factory();

    public static void start() {
        if (Quark.RTMP_PORT <= 0) return; // Disabled

        Thread.ofPlatform().name("RTMP Server").start(() -> {
            try (ServerSocket serverSocket = new ServerSocket()) {
                serverSocket.bind(new InetSocketAddress(Quark.RTMP_PORT));
                LOGGER.info("Listening on port %d...", Quark.RTMP_PORT);

                while (true) {
                    Socket sock = serverSocket.accept();

                    RTMP_CONNECTION_TF.newThread(() -> {
                        try (sock) {
                            sock.setTcpNoDelay(true);

                            try (
                                SocketConnection conn = new SocketConnection(sock);
                                _RTMPProvider rtmp = new _RTMPProvider(conn)) {
//                                rtmp.run();
                                rtmp.handle(RTMP_MISC_TF);
                            } catch (Throwable t) {
                                if ("Socket closed".equals(t.getMessage())) {
                                    throw new EndOfStreamException(t);
                                }
                                if ("The pipe has been ended".equals(t.getMessage())) {
                                    throw new EndOfStreamException(t);
                                }
                                if ("An established connection was aborted by the software in your host machine".equals(t.getMessage())) {
                                    throw new EndOfStreamException(t);
                                }

                                throw t;
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
