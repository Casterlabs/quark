package co.casterlabs.quark.session;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import co.casterlabs.commons.async.LockableResource;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.flv4j.flv.tags.FLVTagType;
import co.casterlabs.quark.Quark;
import co.casterlabs.quark.Webhooks;
import co.casterlabs.quark.session.info.SessionInfo;
import co.casterlabs.quark.util.ModifiableArray;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonNull;
import co.casterlabs.rakurai.json.element.JsonObject;
import lombok.RequiredArgsConstructor;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

@RequiredArgsConstructor
public class Session {
    // Array for fast/efficient iteration, map for lookups
    private final LockableResource<Map<String, SessionListener>> listenerMap = new LockableResource<>(new HashMap<>());
    private final ModifiableArray<SessionListener> listeners = new ModifiableArray<>((count) -> new SessionListener[count]);

    public final SessionInfo info = new SessionInfo();
    public final long createdAt = System.currentTimeMillis();
    public final String id;

    public volatile long prevDts = 0;

    private SessionProvider provider;
    private final List<FLVTag> sequenceTags = new LinkedList<>();
    private final _ThumbnailSessionListener thumbnailGenerator = new _ThumbnailSessionListener();

    private volatile State state = State.RUNNING;

    {
        this.addAsyncListener(new _CodecsSessionListener(this, this.info));
        this.addAsyncListener(this.thumbnailGenerator);
    }

    public void setProvider(SessionProvider provider) {
        if (this.provider != null) {
            this.provider.jam();
        }
        this.sequenceTags.clear();
        this.provider = provider;
    }

    public JsonElement metadata() {
        if (this.provider == null) return JsonNull.INSTANCE;
        return this.provider.metadata();
    }

    public byte[] thumbnail() {
        return this.thumbnailGenerator.thumbnail;
    }

    public void tag(FLVTag tag) {
        if (tag.data().isSequenceHeader() || tag.type() == FLVTagType.SCRIPT) {
            this.sequenceTags.add(tag);

            FLVSequence seq = new FLVSequence(tag); // /shrug/
            for (SessionListener listener : this.listeners.get()) {
                try {
                    listener.onSequence(this, seq);
                } catch (Throwable t) {
                    if (Quark.DEBUG) {
                        t.printStackTrace();
                    }
                }
            }
            return;
        }

        this.prevDts = tag.timestamp();

        for (SessionListener listener : this.listeners.get()) {
            try {
                listener.onTag(this, tag);
            } catch (Throwable t) {
                if (Quark.DEBUG) {
                    t.printStackTrace();
                }
            }
        }
    }

    public void close(boolean graceful) {
        if (this.state != State.RUNNING) return;
        this.state = State.CLOSING;

        if (Webhooks.sessionEnding(this, graceful, this.metadata())) {
            this.state = State.RUNNING;
            return; // We're getting jammed!
        }

        this.state = State.CLOSED;

        FastLogger.logStatic(LogLevel.DEBUG, "Closing session: %s (wasGraceful: %b)", this.id, graceful);

        if (this.provider != null) {
            try {
                this.provider.close(graceful);
            } catch (Throwable t) {
                if (Quark.DEBUG) {
                    t.printStackTrace();
                }
            }
        }

        for (SessionListener listener : this.listeners.get()) {
            try {
                listener.onClose(this);
            } catch (Throwable t) {
                if (Quark.DEBUG) {
                    t.printStackTrace();
                }
            }
        }

        Webhooks.sessionEnded(this.id);
    }

    public JsonArray listeners() {
        JsonArray listeners = new JsonArray();
        for (SessionListener listener : this.listeners.get()) {
            if (listener.type() == null) continue;

            listeners.add(
                new JsonObject()
                    .put("id", listener.id)
                    .put("createdAt", listener.createdAt)
                    .put("type", listener.type().name())
                    .put("fid", listener.fid())
                    .put("metadata", listener.metadata())
            );
        }
        return listeners;
    }

    public void addAsyncListener(SessionListener listener) {
        this.addSyncListener(new _AsyncSessionListener(listener));
    }

    public void addSyncListener(SessionListener listener) {
        if (this.state == State.CLOSED) {
            listener.onClose(this);
            return;
        }

        if (this.provider != null) {
            FLVSequence seq = new FLVSequence(this.sequenceTags.toArray(new FLVTag[0]));
            listener.onSequence(this, seq);
        }

        Map<String, SessionListener> map = this.listenerMap.acquire();
        try {
            map.put(listener.id, listener);
            this.listeners.add(listener);
        } finally {
            this.listenerMap.release();
        }
    }

    public void removeListener(SessionListener identifier) {
        SessionListener removedFromMap;

        Map<String, SessionListener> map = this.listenerMap.acquire();
        try {
            removedFromMap = map.remove(identifier.id);
        } finally {
            this.listenerMap.release();
        }

        if (removedFromMap == null) return;

        this.listeners.remove(removedFromMap);
        removedFromMap.onClose(this);
    }

    public void removeById(String id) {
        for (SessionListener listener : this.listeners.get()) {
            if (id.equals(listener.id)) {
                this.removeListener(listener);
            }
        }
    }

    public void removeByFid(String fid) {
        for (SessionListener listener : this.listeners.get()) {
            if (fid.equals(listener.fid())) {
                this.removeListener(listener);
            }
        }
    }

    private static enum State {
        RUNNING,
        CLOSING,
        CLOSED;
    }

}
