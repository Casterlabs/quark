package co.casterlabs.quark.session;

import java.io.Closeable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import co.casterlabs.commons.async.LockableResource;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.flv4j.flv.tags.FLVTagType;
import co.casterlabs.quark.Quark;
import co.casterlabs.quark.session.info.SessionInfo;
import co.casterlabs.quark.util.ModifiableArray;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Session implements Closeable {
    // Array for fast/efficient iteration, map for lookups
    private final LockableResource<Map<Integer, SessionListener>> listenerMap = new LockableResource<>(new HashMap<>());
    private final ModifiableArray<SessionListener> listeners = new ModifiableArray<>((count) -> new SessionListener[count]);

    public final SessionInfo info = new SessionInfo();
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

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;

        if (this.provider != null) {
            try {
                this.provider.close();
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
    }

    public void addAsyncListener(SessionListener listener) {
        SessionListener async = new _AsyncSessionListener(listener);

        // NB: We don't want to use the async listener's hash code
        // because that would make it unremovable!
        this._addListener(listener.hashCode(), async);
    }

    public void addSyncListener(SessionListener listener) {
        this._addListener(listener.hashCode(), listener);
    }

    private void _addListener(int hashCode, SessionListener listener) {
        if (this.provider != null) {
            FLVSequence seq = new FLVSequence(this.sequenceTags.toArray(new FLVTag[0]));
            listener.onSequence(this, seq);
        }

        Map<Integer, SessionListener> map = this.listenerMap.acquire();
        try {
            map.put(hashCode, listener);
            this.listeners.add(listener);
        } finally {
            this.listenerMap.release();
        }
    }

    public void removeListener(SessionListener listener) {
        Map<Integer, SessionListener> map = this.listenerMap.acquire();
        try {
            SessionListener removed = map.remove(listener.hashCode());
            this.listeners.remove(removed);
        } finally {
            this.listenerMap.release();
        }
        listener.onClose(this);
    }

}
