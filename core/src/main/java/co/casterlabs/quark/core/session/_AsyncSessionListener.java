package co.casterlabs.quark.core.session;

import java.util.concurrent.ThreadFactory;

import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.quark.core.util.CircularBuffer;

/**
 * This class wraps the listener and ensures that all calls to it are do not
 * block.
 */
class _AsyncSessionListener extends SessionListener {
    private static final ThreadFactory THREAD_FACTORY = Thread.ofVirtual().name("Async Session Listener - Write Queue", 0).factory();
    private static final int MAX_OUTSTANDING_PACKETS = 500; // at 30fps, this is ~16 seconds of buffer.

    private final CircularBuffer<Object> buffer = new CircularBuffer<>(MAX_OUTSTANDING_PACKETS);
    final SessionListener delegate;

    _AsyncSessionListener(Session session, SessionListener delegate) {
        super(delegate.id);
        this.delegate = delegate;

        this.buffer.readAsync(THREAD_FACTORY, (item) -> {
            if (item instanceof FLVTag tag) {
                this.delegate.onTag(session, tag);
            } else if (item instanceof FLVSequence seq) {
                this.delegate.onSequence(session, seq);
            }
        });
    }

    @Override
    public void onSequence(Session session, FLVSequence seq) {
        this.buffer.submit(seq);
    }

    @Override
    public void onTag(Session session, FLVTag tag) {
        this.buffer.submit(tag);
    }

    @Override
    public void onClose(Session session) {
        this.buffer.close();
        this.delegate.onClose(session);
    }

    @Override
    public String type() {
        return this.delegate.type();
    }

    @Override
    public String fid() {
        return this.delegate.fid();
    }

}
