package co.casterlabs.quark.core;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import co.casterlabs.commons.async.LockableResource;
import co.casterlabs.quark.core.QuarkSession.SessionListener;
import co.casterlabs.quark.core.flux.LocalFluxSessionListener;
import co.casterlabs.quark.core.flux.RemoteFluxSessionListener;
import co.casterlabs.quark.core.util.FFPlaySessionListener;
import xyz.e3ndr.fastloggingframework.FastLoggingFramework;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class Quark {
    public static final boolean DEBUG = "true".equalsIgnoreCase(System.getenv("QUARK_DEBUG"));

    public static final String FLUX_PREFIX = "quark:";

    public static final int FLUX_PORT = Integer.parseInt(System.getenv("FLUX_PORT"));
    public static final String FLUX_HOST = System.getenv("FLUX_HOST");
    public static final String FLUX_TOKEN = System.getenv("FLUX_TOKEN");

    static {
        System.setProperty("fastloggingframework.wrapsystem", "true");

        if (DEBUG) {
            FastLoggingFramework.setDefaultLevel(LogLevel.ALL);
        }
    }

    private static final LockableResource<Map<String, QuarkSession>> sessions = new LockableResource<>(new HashMap<>());

    public static void forEachSession(Consumer<QuarkSession> session) {
        Map<String, QuarkSession> map = sessions.acquire();
        try {
            map.values().forEach(session);
        } finally {
            sessions.release();
        }
    }

    public static QuarkSession session(String id, boolean remote) {
        Map<String, QuarkSession> map = sessions.acquire();
        try {
            if (map.containsKey(id)) return map.get(id);

            QuarkSession session = new QuarkSession(id);
            map.put(id, session);

            session.listeners.add(new CloseListener());

            if (remote) {
                session.listeners.add(new RemoteFluxSessionListener(session));
            } else {
                session.listeners.add(new LocalFluxSessionListener(session));

                if (DEBUG) {
                    try {
                        session.listeners.add(new FFPlaySessionListener(session));
                    } catch (IOException e) {
                        FastLogger.logStatic(LogLevel.WARNING, "Unable to start FFplay:\n%s", e);
                    }
                }
            }

            return session;
        } finally {
            sessions.release();
        }
    }

    private static class CloseListener implements SessionListener {

        @Override
        public void onClose(QuarkSession session) {
            Map<String, QuarkSession> map = sessions.acquire();
            try {
                map.remove(session.id);
            } finally {
                sessions.release();
            }
        };

    }

}
