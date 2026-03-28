package co.casterlabs.quark.protocol.rtmp;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import co.casterlabs.flv4j.EndOfStreamException;
import co.casterlabs.quark.core.Threads;
import co.casterlabs.quark.core.extensibility.QuarkEntrypoint;
import co.casterlabs.quark.core.util.SocketConnection;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

@QuarkEntrypoint
public class RTMPDaemon {
    private static final FastLogger LOGGER = new FastLogger();
    private static final int SO_TIMEOUT = (int) TimeUnit.MINUTES.toMillis(1);

    private static final ThreadFactory RTMP_CONNECTION_TF = Threads.heavyIo("RTMP Connection");
    private static final ThreadFactory RTMP_MISC_TF = Threads.misc("RTMP Misc");

    public static void start() {
        if (RTMPEnv.RTMP_PORT <= 0) return; // Disabled

        Threads.MISC_THREAD_BUILDER.name("RTMP Server").start(() -> {
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
