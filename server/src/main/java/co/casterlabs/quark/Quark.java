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
import xyz.e3ndr.fastloggingframework.FastLoggingFramework;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class Quark {
    public static final boolean DEBUG = "true".equalsIgnoreCase(System.getenv("QUARK_DEBUG"));
    public static final String FFLL = DEBUG ? "level+warning" : "level+fatal";

    public static final @Nullable String AUTH_SECRET = System.getenv("QUARK_AUTH_SECRET");
    public static final @Nullable String AUTH_ANON_PREGEX = System.getenv("QUARK_ANON_PREGEX");

    public static final int HTTP_PORT = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "8080"));
    public static final int RTMP_PORT = Integer.parseInt(System.getenv().getOrDefault("RTMP_PORT", "1935"));

    public static final @Nullable String WEBHOOK_URL = System.getenv("QUARK_WEBHOOK_URL");

    static {
        System.setProperty("fastloggingframework.wrapsystem", "true");

        if (DEBUG) {
            FastLoggingFramework.setDefaultLevel(LogLevel.ALL);
            FastLogger.logStatic("Debug enabled");
        }
    }

    public static void init() {} // dummy

    private static final LockableResource<Map<String, Session>> sessions = new LockableResource<>(new HashMap<>());

    public static void forEachSession(Consumer<Session> session) {
        Map<String, Session> map = sessions.acquire();
        try {
            map.values().forEach(session);
        } finally {
            sessions.release();
        }
    }

    public static @Nullable Session session(String id, boolean createIfNotExists) {
        Map<String, Session> map = sessions.acquire();
        try {
            if (map.containsKey(id)) return map.get(id);
            if (!createIfNotExists) return null;

            Session session = new Session(id);
            map.put(id, session);

            session.addSyncListener(new CloseListener());

            if (DEBUG) {
                try {
                    session.addAsyncListener(new FFplaySessionListener());
                } catch (IOException e) {
                    FastLogger.logStatic(LogLevel.WARNING, "Unable to start FFplay:\n%s", e);
                }
            }

            return session;
        } finally {
            sessions.release();
        }
    }

    public static Session authenticateSession(SessionProvider provider, String ip, String url, String key) throws IOException {
        if (url == null || key == null) return null;

        String sessionId = Webhooks.sessionStart(ip, url, key);
        if (sessionId == null) return null;

        Session session = Quark.session(sessionId, true);
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
