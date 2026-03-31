package co.casterlabs.quark.core.util;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLong;

import lombok.RequiredArgsConstructor;

/**
 * An OutputStream that counts the number of bytes written to it.
 */
@RequiredArgsConstructor
public class CountingOutputStream extends OutputStream {
    private final OutputStream delegate;
    private final AtomicLong count = new AtomicLong(0);

    @Override
    public void write(int b) throws IOException {
        this.delegate.write(b);
        this.count.incrementAndGet();
    }

    @Override
    public void write(byte[] b) throws IOException {
        this.delegate.write(b);
        this.count.addAndGet(b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        this.delegate.write(b, off, len);
        this.count.addAndGet(len);
    }

    @Override
    public void flush() throws IOException {
        this.delegate.flush();
    }

    @Override
    public void close() throws IOException {
        this.delegate.close();
    }

    public long bytesWritten() {
        return this.count.get();
    }

    /**
     * Resets the byte count to 0, and returns the previous count.
     */
    public long resetBytesWritten() {
        return this.count.getAndSet(0);
    }

}
