package co.casterlabs.quark.session;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.rakurai.json.element.JsonObject;

public abstract class SessionListener {
    public final String id;
    public final long createdAt = System.currentTimeMillis();

    public SessionListener() {
        this(UUID.randomUUID().toString());
    }

    SessionListener(String id) {
        this.id = id;
    }

    public void onSequence(Session session, FLVSequence seq) {}

    public void onTag(Session session, FLVTag tag) {}

    public abstract void onClose(Session session);

    /**
     * @return null, if internal.
     */
    public abstract @Nullable Type type();

    public @Nullable String fid() {
        return null;
    }

    public @Nullable JsonObject metadata() {
        return null;
    }

    public static enum Type {
        HTTP_PLAYBACK,
        RTMP_PLAYBACK,

        RTMP_EGRESS,
    };

}
