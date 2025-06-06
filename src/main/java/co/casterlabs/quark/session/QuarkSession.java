package co.casterlabs.quark.session;

import java.io.Closeable;

import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.quark.util.FLVSequenceTag;
import co.casterlabs.quark.util.ModifiableArray;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class QuarkSession implements Closeable {
    private final ModifiableArray<QuarkSessionListener> listeners = new ModifiableArray<>((count) -> new QuarkSessionListener[count]);

    public final String id;

    private void sequenceRequest() {
        this.listeners.forEach((listener) -> listener.onSequenceRequest(this));
    }

    /**
     * @param data valid: {@link FLVTag}, {@link FLVSequenceTag}
     */
    public void data(Object data) {
        this.listeners.forEach((listener) -> {
            listener.packetQueue.submit(() -> {
                listener.onPacket(this, data);
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
