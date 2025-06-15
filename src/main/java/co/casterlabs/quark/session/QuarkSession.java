package co.casterlabs.quark.session;

import java.io.Closeable;
import java.util.HashMap;
import java.util.Map;

import co.casterlabs.quark.util.ModifiableArray;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class QuarkSession implements Closeable {
    private final ModifiableArray<QuarkSessionListener> listeners = new ModifiableArray<>((count) -> new QuarkSessionListener[count]);

    public final Map<String, Object> attachments = new HashMap<>();
    public final String id;

    public volatile long prevDts = 0;
    public volatile long prevPts = 0;

    private boolean closed = false;

    private void sequenceRequest() {
        this.listeners.forEach((listener) -> listener.onSequenceRequest(this));
    }

    public void jam() {
        this.listeners.forEach((listener) -> {
            listener.onJam(this);
        });
    }

    public void sequence(FLVSequence seq) {
        this.listeners.forEach((listener) -> {
            if (listener.async()) {
                listener.packetQueue.submit(() -> {
                    listener.onSequence(this, seq);
                });
            } else {
                listener.onSequence(this, seq);
            }
        });
    }

    public void data(FLVData data) {
        this.prevDts = data.tag().timestamp();
        this.prevPts = data.pts();

        this.listeners.forEach((listener) -> {
            if (listener.async()) {
                listener.packetQueue.submit(() -> {
                    listener.onData(this, data);
                });
            } else {
                listener.onData(this, data);
            }
        });
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.listeners.forEach((listener) -> listener.onClose(this));
    }

    public void addListener(QuarkSessionListener listener) {
        this.listeners.add(listener);
        this.sequenceRequest();
    }

    public void removeListener(QuarkSessionListener listener) {
        this.listeners.remove(listener);
        listener.packetQueue.shutdownNow();
        listener.onClose(this);
    }

}
