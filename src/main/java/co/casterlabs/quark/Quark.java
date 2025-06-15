package co.casterlabs.quark;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.commons.async.LockableResource;
import co.casterlabs.quark.session.QuarkSession;
import co.casterlabs.quark.session.QuarkSessionListener;
import co.casterlabs.quark.session.listeners.FFPlaySessionListener;
import xyz.e3ndr.fastloggingframework.FastLoggingFramework;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class Quark {
    public static final boolean DEBUG = "true".equalsIgnoreCase(System.getenv("QUARK_DEBUG"));

    static {
        System.setProperty("fastloggingframework.wrapsystem", "true");

        if (DEBUG) {
            FastLoggingFramework.setDefaultLevel(LogLevel.ALL);
            FastLogger.logStatic("Debug enabled");
        }
    }

    public static void init() {} // dummy

    private static final LockableResource<Map<String, QuarkSession>> sessions = new LockableResource<>(new HashMap<>());

    public static void forEachSession(Consumer<QuarkSession> session) {
        Map<String, QuarkSession> map = sessions.acquire();
        try {
            map.values().forEach(session);
        } finally {
            sessions.release();
        }
    }

    public static @Nullable QuarkSession session(String id, boolean createIfNotExists) {
        Map<String, QuarkSession> map = sessions.acquire();
        try {
            if (map.containsKey(id)) return map.get(id);
            if (!createIfNotExists) return null;

            QuarkSession session = new QuarkSession(id);
            map.put(id, session);

            session.addListener(new CloseListener());

            if (DEBUG) {
                try {
                    session.addListener(new FFPlaySessionListener(session));
                } catch (IOException e) {
                    FastLogger.logStatic(LogLevel.WARNING, "Unable to start FFplay:\n%s", e);
                }
            }

            return session;
        } finally {
            sessions.release();
        }
    }

    public static QuarkSession authenticateSession(QuarkSessionListener listener, String url, String key) throws IOException {
        if (url == null || key == null) return null;

        // TODO

        QuarkSession session = Quark.session(key, true);
        session.jam();
        session.addListener(listener);
        return session;
    }

    private static class CloseListener extends QuarkSessionListener {

        @Override
        public void onClose(QuarkSession session) {
            Map<String, QuarkSession> map = sessions.acquire();
            try {
                map.remove(session.id);
            } finally {
                sessions.release();
            }
        };

        @Override
        public boolean async() {
            return false;
        }

    }

}
