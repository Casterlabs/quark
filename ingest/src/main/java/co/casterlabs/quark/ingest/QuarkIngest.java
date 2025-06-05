package co.casterlabs.quark.ingest;

import java.io.IOException;

import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.QuarkSession;
import co.casterlabs.quark.core.QuarkSession.SessionListener;

public class QuarkIngest {

    public static QuarkSession authenticateSession(SessionListener listener, String url, String key) throws IOException {
        if (url == null || key == null) return null;

        // TODO

        QuarkSession session = Quark.session(key, false);
        session.listeners.add(listener);
        return session;
    }

}
