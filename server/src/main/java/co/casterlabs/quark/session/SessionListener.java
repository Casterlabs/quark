package co.casterlabs.quark.session;

public interface SessionListener {

    public default void onSequence(Session session, FLVSequence seq) {}

    public default void onData(Session session, FLVData data) {}

    public void onClose(Session session);

    /**
     * @return null, if internal.
     */
    public Type type();

    public String fid();

    public static enum Type {
        HTTP_PLAYBACK,
        RTMP
    };

}
