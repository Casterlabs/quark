package co.casterlabs.quark.core;

import java.io.Closeable;

import co.casterlabs.quark.core.util.ModifiableArray;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class QuarkSession implements Closeable {
    public final ModifiableArray<SessionListener> listeners = new ModifiableArray<>((count) -> new SessionListener[count]);

    public final String id;

    public void sequenceRequest() {
        this.listeners.forEach((listener) -> listener.onSequenceRequest(this));
    }

    public void data(Object data) {
        this.listeners.forEach((listener) -> listener.onPacket(this, data));
    }

    @Override
    public void close() {
        this.listeners.forEach((listener) -> listener.onClose(this));
    }

    public static interface SessionListener {

        public default void onSequenceRequest(QuarkSession session) {}

        public default void onPacket(QuarkSession session, Object data) {}

        public void onClose(QuarkSession session);

    }

}
