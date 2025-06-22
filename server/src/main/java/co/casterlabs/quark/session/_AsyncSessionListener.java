package co.casterlabs.quark.session;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;

/**
 * This class wraps the listener and ensures that all calls to it are do not
 * block.
 */
@RequiredArgsConstructor
class _AsyncSessionListener implements SessionListener {
    private static final ThreadFactory THREAD_FACTORY = Thread.ofVirtual().name("Async Session Listener - Write Queue", 0).factory();
    private static final int MAX_OUTSTANDING_PACKETS = 1000;

    private final ExecutorService packetQueue = new ThreadPoolExecutor(
        1, 1,
        0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(MAX_OUTSTANDING_PACKETS),
        THREAD_FACTORY,
        new ThreadPoolExecutor.DiscardPolicy()
    );

    private final SessionListener delegate;

    @Override
    public void onSequence(Session session, FLVSequence seq) {
        this.packetQueue.submit(() -> this.delegate.onSequence(session, seq));
    }

    @Override
    public void onData(Session session, FLVData data) {
        this.packetQueue.submit(() -> this.delegate.onData(session, data));
    }

    @Override
    public void onClose(Session session) {
        this.packetQueue.shutdownNow();
        this.delegate.onClose(session);
    }

    @Override
    public Type type() {
        return this.delegate.type();
    }

    @Override
    public String fid() {
        return this.delegate.fid();
    }

}
