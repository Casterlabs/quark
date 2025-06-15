package co.casterlabs.quark.session;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class QuarkSessionListener {
    private static final ThreadFactory THREAD_FACTORY = Thread.ofVirtual().name("Quark Session Listener - Write Queue", 0).factory();
    private static final int MAX_OUTSTANDING_PACKETS = 1000;

//    final ExecutorService packetQueue = Executors.newSingleThreadExecutor(THREAD_FACTORY);
    final ExecutorService packetQueue = new ThreadPoolExecutor(
        1, 1,
        0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(MAX_OUTSTANDING_PACKETS),
        THREAD_FACTORY,
        new ThreadPoolExecutor.DiscardPolicy()
    );

    public void onJam(QuarkSession session) {}

    public void onSequenceRequest(QuarkSession session) {}

    public void onSequence(QuarkSession session, FLVSequence seq) {}

    public void onData(QuarkSession session, FLVData data) {}

    public abstract void onClose(QuarkSession session);

    public boolean async() {
        return true;
    }

}
