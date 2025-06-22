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
import co.casterlabs.rakurai.json.element.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Session {
    // Array for fast/efficient iteration, map for lookups
    private final LockableResource<Map<String, SessionListener>> listenerMap = new LockableResource<>(new HashMap<>());
    private final ModifiableArray<SessionListener> listeners = new ModifiableArray<>((count) -> new SessionListener[count]);

    public final SessionInfo info = new SessionInfo();
    public final long createdAt = System.currentTimeMillis();
    public final String id;

    public volatile long prevDts = 0;
    public volatile long prevPts = 0;

    private SessionProvider provider;
    private final List<FLVTag> sequenceTags = new LinkedList<>();

    private boolean closed = false;

    {
        this.addAsyncListener(new _CodecsSessionListener(this, this.info));
    }

    public void setProvider(SessionProvider provider) {
        if (this.provider != null) {
            this.provider.jam();
        }
        this.sequenceTags.clear();
        this.provider = provider;
    }

    public void data(FLVData data) {
        if (data.tag().data().isSequenceHeader() || data.tag().type() == FLVTagType.SCRIPT) {
            this.sequenceTags.add(data.tag());

            FLVSequence seq = new FLVSequence(data.tag()); // /shrug/
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

        this.prevDts = data.tag().timestamp();
        this.prevPts = data.pts();

        for (SessionListener listener : this.listeners.get()) {
            try {
                listener.onData(this, data);
            } catch (Throwable t) {
                if (Quark.DEBUG) {
                    t.printStackTrace();
                }
            }
        }
    }

    public void close(boolean graceful) {
        if (this.closed) return;

        if (Webhooks.sessionEnding(this, graceful)) {
            return; // We're getting jammed!
        }

        this.closed = true;

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

        Webhooks.sessionEnded(this, graceful);
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
            );
        }
        return listeners;
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

    public void addAsyncListener(SessionListener listener) {
        SessionListener async = new _AsyncSessionListener(listener);

        // NB: We don't want to use the async listener's id
        // because that would make it unremovable!
        this._addListener(listener.id, async);
    }

    public void addSyncListener(SessionListener listener) {
        this._addListener(listener.id, listener);
    }

    private void _addListener(String id, SessionListener listener) {
        if (this.provider != null) {
            FLVSequence seq = new FLVSequence(this.sequenceTags.toArray(new FLVTag[0]));
            listener.onSequence(this, seq);
        }

        Map<String, SessionListener> map = this.listenerMap.acquire();
        try {
            map.put(id, listener);
            this.listeners.add(listener);
        } finally {
            this.listenerMap.release();
        }
    }

    public void removeListener(SessionListener listener) {
        Map<String, SessionListener> map = this.listenerMap.acquire();
        try {
            SessionListener removed = map.remove(listener.id);
            this.listeners.remove(removed);
        } finally {
            this.listenerMap.release();
        }
        listener.onClose(this);
    }

}
