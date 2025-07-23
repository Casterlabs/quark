package co.casterlabs.quark;

import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.Nullable;

import xyz.e3ndr.fastloggingframework.FastLoggingFramework;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class Quark {
    public static final boolean DEBUG = "true".equalsIgnoreCase(System.getenv("QUARK_DEBUG"));
    public static final String FFLL = DEBUG ? "level+warning" : "level+fatal";

    /**
     * HMAC256 secret for signed JWTs.
     */
    public static final @Nullable String AUTH_SECRET = System.getenv("QUARK_AUTH_SECRET");
    /**
     * The default playback regex for anonymous users. Null to disallow anonymous
     * viewership.
     */
    public static final @Nullable String AUTH_ANON_PREGEX = System.getenv("QUARK_ANON_PREGEX");

    /**
     * -1 to disable.
     */
    public static final int HTTP_PORT = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "8080"));
    /**
     * -1 to disable.
     */
    public static final int RTMP_PORT = Integer.parseInt(System.getenv().getOrDefault("RTMP_PORT", "1935"));

    /**
     * Null to disable webhooks.
     */
    public static final @Nullable String WEBHOOK_URL = System.getenv("QUARK_WEBHOOK_URL");

    /**
     * The interval (in seconds) in which a thumbnail should be rendered.
     */
    public static final long THUMBNAIL_INTERVAL = TimeUnit.SECONDS.toMillis(Integer.parseInt(System.getenv().getOrDefault("QUARK_THUMB_IT", "30")));

    /**
     * Whether or not to use Virtual Threads for heavy-IO tasks.
     */
    public static final @Experimental boolean EXPR_VIRTUAL_THREAD_HEAVY_IO = "true".equalsIgnoreCase(System.getenv("QUARK_EXP_VIRTUAL_THREAD_HEAVY_IO"));

    static {
        System.setProperty("fastloggingframework.wrapsystem", "true");

        if (DEBUG) {
            FastLoggingFramework.setDefaultLevel(LogLevel.ALL);
            FastLogger.logStatic("Debug enabled");
        }
    }

    public static void init() {} // dummy

}
