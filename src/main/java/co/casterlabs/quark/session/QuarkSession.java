package co.casterlabs.quark.session;

import java.io.Closeable;

import co.casterlabs.quark.util.ModifiableArray;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class QuarkSession implements Closeable {
    private final ModifiableArray<QuarkSessionListener> listeners = new ModifiableArray<>((count) -> new QuarkSessionListener[count]);

    public final String id;

    private void sequenceRequest() {
        this.listeners.forEach((listener) -> listener.onSequenceRequest(this));
    }

    public void sequence(FLVSequence seq) {
        this.listeners.forEach((listener) -> {
            listener.packetQueue.submit(() -> {
                listener.onSequence(this, seq);
            });
        });
    }

    public void data(FLVData data) {
        this.listeners.forEach((listener) -> {
            listener.packetQueue.submit(() -> {
                listener.onData(this, data);
            });
        });
    }

    @Override
    public void close() {
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
