package co.casterlabs.quark.session;

import java.util.UUID;

public abstract class SessionListener {
    public final String id = UUID.randomUUID().toString();
    public final long createdAt = System.currentTimeMillis();

    public void onSequence(Session session, FLVSequence seq) {}

    public void onData(Session session, FLVData data) {}

    public abstract void onClose(Session session);

    /**
     * @return null, if internal.
     */
    public abstract Type type();

    public abstract String fid();

    public static enum Type {
        HTTP_PLAYBACK,
        RTMP
    };

}
