package co.casterlabs.quark.protocol.rtmp;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import co.casterlabs.flv4j.EndOfStreamException;
import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.extensibility.QuarkEntrypoint;
import co.casterlabs.quark.core.util.SocketConnection;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

@QuarkEntrypoint
public class RTMPDaemon {
    private static final FastLogger LOGGER = new FastLogger();
    private static final int SO_TIMEOUT = (int) TimeUnit.MINUTES.toMillis(1);

    private static final ThreadFactory RTMP_CONNECTION_TF = Quark.HEAVY_IO_THREAD_BUILDER.name("RTMP Connection", 0).factory();
    private static final ThreadFactory RTMP_MISC_TF = Thread.ofVirtual().name("RTMP Misc", 0).factory();

    public static void start() {
        if (RTMPEnv.RTMP_PORT <= 0) return; // Disabled

        Thread.ofPlatform().name("RTMP Server").start(() -> {
            try (ServerSocket serverSocket = new ServerSocket()) {
                serverSocket.bind(new InetSocketAddress(RTMPEnv.RTMP_PORT));
                LOGGER.info("Listening on port %d...", RTMPEnv.RTMP_PORT);

                while (true) {
                    Socket sock = serverSocket.accept();

                    RTMP_CONNECTION_TF.newThread(() -> {
                        try (sock) {
                            sock.setTcpNoDelay(true);
                            sock.setSoTimeout(SO_TIMEOUT);

                            try (
                                SocketConnection conn = new SocketConnection(sock);
                                RTMPConnection rtmp = new RTMPConnection(conn)) {
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
