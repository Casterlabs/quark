package co.casterlabs.quark.session;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class QuarkSessionListener {
    private static final ThreadFactory THREAD_FACTORY = Thread.ofVirtual().name("Quark Session Listener - Write Queue", 0).factory();
    private static final int MAX_OUTSTANDING_PACKETS = 100;

//    final ExecutorService packetQueue = Executors.newSingleThreadExecutor(THREAD_FACTORY);
    final ExecutorService packetQueue = new ThreadPoolExecutor(
        1, 1,
        0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(MAX_OUTSTANDING_PACKETS),
        THREAD_FACTORY,
        new ThreadPoolExecutor.DiscardPolicy()
    );

    public void onSequenceRequest(QuarkSession session) {}

    public void onPacket(QuarkSession session, Object data) {}

    public abstract void onClose(QuarkSession session);

}
