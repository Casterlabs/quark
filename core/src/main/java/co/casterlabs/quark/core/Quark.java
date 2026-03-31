package co.casterlabs.quark.core;

import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.Nullable;

import co.casterlabs.quark.core.util.EnvHelper;
import xyz.e3ndr.fastloggingframework.FastLoggingFramework;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class Quark {
    public static final boolean DEBUG = EnvHelper.bool("QUARK_DEBUG", false);
    public static final String FFLL = DEBUG ? "level+warning" : "level+fatal";

    /**
     * HMAC256 secret for signed JWTs.
     */
    public static final @Nullable String AUTH_SECRET = EnvHelper.string("QUARK_AUTH_SECRET", null);
    /**
     * The default playback regex for anonymous users. Null to disallow anonymous
     * viewership.
     */
    public static final @Nullable String AUTH_ANON_PREGEX = EnvHelper.string("QUARK_ANON_PREGEX", null);

    /**
     * Null to disable webhooks.
     */
    public static final @Nullable String WEBHOOK_URL = EnvHelper.string("QUARK_WEBHOOK_URL", null);

    /**
     * The interval (in seconds) in which a thumbnail should be rendered.
     */
    public static final long THUMBNAIL_INTERVAL = TimeUnit.SECONDS.toMillis(EnvHelper.integer("QUARK_THUMB_IT", 30));

    /**
     * Whether or not to use Virtual Threads for heavy-IO tasks.
     */
    static final @Experimental boolean EXP_VIRTUAL_THREAD_HEAVY_IO = EnvHelper.bool("QUARK_EXP_VIRTUAL_THREAD_HEAVY_IO", false);

    /**
     * The URL to send analytics data to. Null to disable analytics.
     */
    static final @Experimental @Nullable String EXP_ANALYTICS_URL = EnvHelper.string("QUARK_EXP_ANALYTICS_URL", null);

    /**
     * The frequency in seconds in which analytics data should be sent. Set to a
     * higher value to reduce load on the analytics server. This also affects the
     * collection rate internally.
     */
    static final @Experimental long EXP_ANALYTICS_INTERVAL = TimeUnit.SECONDS.toMillis(EnvHelper.integer("QUARK_EXP_ANALYTICS_INTERVAL", 60));

    static {
        System.setProperty("fastloggingframework.wrapsystem", "true");

        if (DEBUG) {
            FastLoggingFramework.setDefaultLevel(LogLevel.ALL);
            FastLogger.logStatic("Debug enabled");
        }
    }

    public static void init() {} // dummy

}
