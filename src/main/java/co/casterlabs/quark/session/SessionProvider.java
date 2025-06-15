package co.casterlabs.quark.session;

import java.io.Closeable;

public interface SessionProvider extends Closeable {

    public void jam();

}
