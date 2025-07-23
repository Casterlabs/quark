package co.casterlabs.quark;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.commons.async.LockableResource;
import co.casterlabs.quark.session.Session;
import co.casterlabs.quark.session.SessionListener;
import co.casterlabs.quark.session.SessionProvider;
import co.casterlabs.quark.session.listeners.FFplaySessionListener;
import co.casterlabs.quark.util.FF;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class Sessions {

    private static final LockableResource<Map<String, Session>> sessions = new LockableResource<>(new HashMap<>());

    public static void forEachSession(Consumer<Session> session) {
        Map<String, Session> map = sessions.acquire();
        try {
            map.values().forEach(session);
        } finally {
            sessions.release();
        }
    }

    public static @Nullable Session getSession(String id, boolean createIfNotExists) {
        Map<String, Session> map = sessions.acquire();
        try {
            if (map.containsKey(id)) return map.get(id);
            if (!createIfNotExists) return null;

            Session session = new Session(id);
            map.put(id, session);

            session.addSyncListener(new CloseListener());

            if (Quark.DEBUG && FF.canUsePlay) {
                try {
                    session.addAsyncListener(new FFplaySessionListener());
                } catch (IOException e) {
                    FastLogger.logStatic(LogLevel.WARNING, "Unable to start FFplay:\n%s", e);
                }
            }

            Webhooks.sessionStarted(id);

            return session;
        } finally {
            sessions.release();
        }
    }

    public static Session authenticateSession(SessionProvider provider, String ip, String url, String app, String key) throws IOException {
        if (url == null || key == null) return null;

        String sessionId = Webhooks.sessionStarting(ip, url, app, key);
        if (sessionId == null) return null;

        Session session = getSession(sessionId, true);
        session.setProvider(provider);

        return session;
    }

    private static class CloseListener extends SessionListener {

        @Override
        public void onClose(Session session) {
            Map<String, Session> map = sessions.acquire();
            try {
                map.remove(session.id);
            } finally {
                sessions.release();
            }
        };

        @Override
        public Type type() {
            return null;
        }

    }

}
