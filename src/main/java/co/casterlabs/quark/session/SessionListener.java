package co.casterlabs.quark.session;

public interface SessionListener {

    public default void onSequence(Session session, FLVSequence seq) {}

    public default void onData(Session session, FLVData data) {}

    public void onClose(Session session);

}
