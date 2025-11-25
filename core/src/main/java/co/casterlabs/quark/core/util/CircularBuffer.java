package co.casterlabs.quark.core.util;

import java.io.Closeable;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.quark.core.Quark;
import lombok.NonNull;

public class CircularBuffer<T> implements Closeable {
    private final Object[] buffer;

    private int head = 0; // oldest element
    private int tail = 0; // next write position
    private int size = 0; // current size

    private volatile boolean isClosed = false;

    public CircularBuffer(int capacity) {
        this.buffer = new Object[capacity];
    }

    public synchronized void submit(@NonNull T item) {
        if (this.isClosed) {
            return; // Don't accept new items if closed
        }

        this.buffer[this.tail] = item;
        this.tail = (this.tail + 1) % this.buffer.length;

        if (this.size == this.buffer.length) {
            // Overwrote the oldest element, advance head to next oldest
            this.head = this.tail;
        } else {
            this.size++;
        }

        // Wake up any waiting readers
        this.notifyAll();
    }

    /**
     * @return                      the oldest element, blocking if necessary until
     *                              an element is available. Returns null if the
     *                              buffer is closed
     * 
     * @throws InterruptedException if the calling thread is interrupted while
     *                              waiting
     */
    @SuppressWarnings("unchecked")
    public synchronized @Nullable T read() throws InterruptedException {
        while (this.size == 0 && !this.isClosed) {
            this.wait();
        }

        if (this.isClosed) {
            return null;
        }

        T item = (T) this.buffer[this.head];
        this.buffer[this.head] = null; // Help GC

        this.head = (this.head + 1) % this.buffer.length;
        this.size--;

        return item;
    }

    public synchronized int size() {
        return this.size;
    }

    public boolean isClosed() {
        return this.isClosed;
    }

    @Override
    public synchronized void close() {
        this.isClosed = true;
        this.notifyAll();
    }

    public void readAsync(ThreadFactory factory, Consumer<T> consumer) {
        Thread thread = factory.newThread(() -> {
            try {
                while (!this.isClosed) {
                    T item = this.read();
                    if (item == null) {
                        // We've been closed.
                        break;
                    }

                    consumer.accept(item);
                }
            } catch (InterruptedException e) {
                if (Quark.DEBUG) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

}
