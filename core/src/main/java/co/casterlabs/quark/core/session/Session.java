package co.casterlabs.quark.core.session;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import co.casterlabs.commons.async.LockableResource;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.flv4j.flv.tags.FLVTagType;
import co.casterlabs.flv4j.flv.tags.script.FLVScriptTagData;
import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.Webhooks;
import co.casterlabs.quark.core.extensibility._Extensibility;
import co.casterlabs.quark.core.session.info.SessionInfo;
import co.casterlabs.quark.core.util.ModifiableArray;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonNull;
import co.casterlabs.rakurai.json.element.JsonObject;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class Session {
    // Array for fast/efficient iteration, map for lookups
    private final LockableResource<Map<String, SessionListener>> listenerMap = new LockableResource<>(new HashMap<>());
    private final ModifiableArray<SessionListener> listeners = new ModifiableArray<>((count) -> new SessionListener[count]);

    public final SessionInfo info = new SessionInfo();
    public final long createdAt = System.currentTimeMillis();
    public final String id;

    /**
     * @apiNote Must not be modified.
     */
    public volatile long prevDts = 0;
    private volatile SessionProvider provider;

    private final List<FLVTag> videoSequenceTags = new LinkedList<>();
    private final List<FLVTag> audioSequenceTags = new LinkedList<>();
    private final List<FLVTag> scriptSequenceTags = new LinkedList<>();

    private final _ThumbnailSessionListener thumbnailGenerator = new _ThumbnailSessionListener();

    private volatile State state = State.STARTING;

    public Session(String id) {
        this.id = id;

        // Add built-in listeners
        this.addAsyncListener(this.thumbnailGenerator);
        this.addAsyncListener(new _CodecsSessionListener(this, this.info));

        for (Function<Session, SessionListener> factory : _Extensibility.syncStaticSessionListeners) {
            SessionListener listener = factory.apply(this);
            if (listener == null) continue;
            this.addSyncListener(listener);
        }
        for (Function<Session, SessionListener> factory : _Extensibility.asyncStaticSessionListeners) {
            SessionListener listener = factory.apply(this);
            if (listener == null) continue;
            this.addAsyncListener(listener);
        }
    }

    public synchronized void setProvider(SessionProvider provider) {
        if (this.provider != null) {
            this.provider.jam();
        }
        this.videoSequenceTags.clear();
        this.audioSequenceTags.clear();
        this.scriptSequenceTags.clear();
        this.provider = provider;
    }

    public JsonElement metadata() {
        if (this.provider == null) return JsonNull.INSTANCE;
        return this.provider.metadata();
    }

    /**
     * @apiNote This is the direct thumbnail data, it must NOT be modified _ever_.
     *          If you want to modify it, make a copy first. Modifying this data
     *          will cause undefined behavior in the session and may cause crashes.
     */
    public byte[] thumbnail() {
        return this.thumbnailGenerator.thumbnail;
    }

    /**
     * @apiNote This must only be called by the provider. This is not thread safe.
     *          Breaking these contracts will cause undefined behavior in the
     *          session and may cause crashes.
     */
    public void tag(FLVTag tag) {
        boolean isScriptSequence = tag.type() == FLVTagType.SCRIPT && ((FLVScriptTagData) tag.data()).methodName().equals("onMetaData");
        if (tag.data().isSequenceHeader() || isScriptSequence) {
            List<FLVTag> sequenceTags;
            if (tag.type() == FLVTagType.VIDEO) {
                sequenceTags = this.videoSequenceTags;
            } else if (tag.type() == FLVTagType.AUDIO) {
                sequenceTags = this.audioSequenceTags;
            } else {
                sequenceTags = this.scriptSequenceTags;
            }

            long currentSeqTs = sequenceTags.isEmpty() ? 0 : sequenceTags.get(0).timestamp();
            if (tag.timestamp() > currentSeqTs) {
                // We're getting a new list of sequence headers!
//                System.out.println("New sequence header detected at timestamp: " + tag.timestamp());
                sequenceTags.clear();
            }

            sequenceTags.add(tag);

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

        if (this.state == State.STARTING) {
            // We made it past the sequence tags, so we should transition to RUNNING and
            // send the STARTED webhook.
            this.state = State.RUNNING;
            Webhooks.sessionStarted(this, provider.metadata());
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

    public synchronized void close(boolean graceful) {
        if (this.state != State.STARTING && this.state != State.RUNNING) return;
        this.state = State.CLOSING;

        // We intentionally lock the session while we call the webhook.
        // This is to guarantee the state the session either enters CLOSED or goes back
        // to RUNNING safely.
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

        Map<String, SessionListener> map = this.listenerMap.acquire();
        try {
            for (SessionListener listener : this.listeners.get()) {
                try {
                    listener.onClose(this);
                } catch (Throwable t) {
                    if (Quark.DEBUG) {
                        t.printStackTrace();
                    }
                } finally {
                    map.remove(listener.id);
                }
            }
        } finally {
            this.listenerMap.release();
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
                    .put("type", listener.type())
                    .put("fid", listener.fid())
                    .put("metadata", listener.metadata())
            );
        }
        return listeners;
    }

    public synchronized void addAsyncListener(SessionListener listener) {
        this.addSyncListener(new _AsyncSessionListener(this, listener));
    }

    public synchronized void addSyncListener(SessionListener listener) {
        if (this.state == State.CLOSED) {
            listener.onClose(this);
            return;
        }

        if (this.provider != null) {
            List<FLVTag> sequenceTags = new LinkedList<>();
            sequenceTags.addAll(this.scriptSequenceTags);
            sequenceTags.addAll(this.videoSequenceTags);
            sequenceTags.addAll(this.audioSequenceTags);
            FLVSequence seq = new FLVSequence(sequenceTags.toArray(new FLVTag[0]));
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

    public synchronized void removeListener(SessionListener identifier) {
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

    public synchronized void removeById(String id) {
        for (SessionListener listener : this.listeners.get()) {
            if (id.equals(listener.id)) {
                this.removeListener(listener);
            }
        }
    }

    public synchronized void removeByFid(String fid) {
        for (SessionListener listener : this.listeners.get()) {
            if (fid.equals(listener.fid())) {
                this.removeListener(listener);
            }
        }
    }

    private static enum State {
        STARTING,
        RUNNING,
        CLOSING,
        CLOSED;
    }

}
