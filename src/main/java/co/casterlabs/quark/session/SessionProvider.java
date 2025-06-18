package co.casterlabs.quark.session;

public interface SessionProvider {

    public void jam();

    public void close(boolean wasGraceful);

}
