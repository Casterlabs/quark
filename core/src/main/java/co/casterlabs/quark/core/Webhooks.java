package co.casterlabs.quark.core;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.quark.core.egress.config.EgressConfiguration;
import co.casterlabs.quark.core.extensibility._Extensibility;
import co.casterlabs.quark.core.ingest.ffmpeg.FFmpegProvider;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.quark.core.session.info.SessionInfo;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonObject;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class Webhooks {
    private static final OkHttpClient client = new OkHttpClient();

    private static final ExecutorService ASYNC_WEBHOOKS = Executors.newCachedThreadPool();

    private static <T> T post(String type, Object data, Class<T> expected) throws IOException {
        JsonObject payload = new JsonObject()
            .put("type", type)
            .put("data", Rson.DEFAULT.toJson(data));

        Call call = client.newCall(
            new Request.Builder()
                .url(Quark.WEBHOOK_URL)
                .post(
                    RequestBody.create(
                        payload.toString(true),
                        MediaType.parse("application/json")
                    )
                )
                .build()
        );
        try (Response res = call.execute()) {
            String body = res.body().string();

            if (!res.isSuccessful()) {
                throw new IOException(res.code() + ": " + body);
            }

            if (expected == null) return null;

            return Rson.DEFAULT.fromJson(body, expected);
        }
    }

    /* ---------------- */
    /* Session Starting */
    /* ---------------- */

    /**
     * @return null, if the session was disallowed.
     */
    public static String sessionStarting(String protocol, String ip, @Nullable String url, @Nullable String app, String key, @Nullable JsonObject metadata) {
        if (Quark.WEBHOOK_URL == null || Quark.WEBHOOK_URL.isEmpty()) return key; // dummy mode.

        try {
            SessionStartingResponse res = post(
                "SESSION_STARTING",
                new SessionStartingRequest(protocol, ip, url, app, key, metadata),
                SessionStartingResponse.class
            );

            return res.id;
        } catch (IOException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return null;
        }
    }

    @JsonClass(exposeAll = true)
    private static record SessionStartingRequest(String protocol, String ip, @Nullable String url, @Nullable String app, String key, @Nullable JsonObject metadata) {
    }

    @JsonClass(exposeAll = true)
    private static class SessionStartingResponse {
        public String id = null;
    }

    /* ---------------- */
    /* Session Started  */
    /* ---------------- */

    public static void sessionStarted(Session session, @Nullable JsonObject metadata) {
        if (Quark.WEBHOOK_URL == null || Quark.WEBHOOK_URL.isEmpty()) return; // dummy mode.

        ASYNC_WEBHOOKS.submit(() -> {
            try {
                SessionStartedResponse res = post(
                    "SESSION_STARTED",
                    new SessionStartedRequest(session.id, session.info, metadata),
                    SessionStartedResponse.class
                );

                if (res.shouldTerminate) {
                    session.close(true);
                    return;
                }

                res.egresses.put("pipeline", res.pipelineEgresses);
                res.egresses.put("rtmp", res.rtmpEgresses);

                for (Map.Entry<String, JsonElement> entry : res.egresses.entrySet()) {
                    String type = entry.getKey();
                    JsonElement configs = entry.getValue();
                    if (configs == null) continue;

                    Class<? extends EgressConfiguration> configurationClass = _Extensibility.egressConfigurations.get(type);
                    if (configurationClass == null) {
                        if (Quark.DEBUG) {
                            FastLogger.logStatic(LogLevel.WARNING, "Unknown egress type from webhook: " + type);
                        }
                        continue;
                    } else if (!configs.isJsonArray()) {
                        if (Quark.DEBUG) {
                            FastLogger.logStatic(LogLevel.WARNING, "Egress configs is not an array for type: " + type);
                        }
                        continue;
                    }

                    for (JsonElement configElem : configs.getAsArray()) {
                        try {
                            EgressConfiguration config = Rson.DEFAULT.fromJson(configElem, configurationClass);
                            config.create(session);
                        } catch (Exception e) {
                            if (Quark.DEBUG) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } catch (IOException e) {
                if (Quark.DEBUG) {
                    e.printStackTrace();
                }
            }
        });
    }

    @JsonClass(exposeAll = true)
    private static record SessionStartedRequest(String id, SessionInfo info, @Nullable JsonObject metadata) {
    }

    @JsonClass(exposeAll = true)
    private static class SessionStartedResponse {
        private Map<String, JsonElement> egresses = new HashMap<>();
        private boolean shouldTerminate = false;

        @Deprecated
        private JsonArray rtmpEgresses = JsonArray.EMPTY_ARRAY;
        @Deprecated
        private JsonArray pipelineEgresses = JsonArray.EMPTY_ARRAY;

    }

    /* ---------------- */
    /*  Session Ending  */
    /* ---------------- */

    /**
     * @return whether or not the session is being jammed.
     */
    public static boolean sessionEnding(Session session, boolean wasGraceful, JsonElement metadata) {
        if (Quark.WEBHOOK_URL == null || Quark.WEBHOOK_URL.isEmpty()) return false; // dummy mode.

        try {
            SessionEndingResponse res = post(
                "SESSION_ENDING",
                new SessionEndingRequest(session.id, wasGraceful, metadata),
                SessionEndingResponse.class
            );

            if (res.source == null) return false; // Do not jam.

            new FFmpegProvider(session, res.source, res.loop); // Jelly!
            return true;
        } catch (IOException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return false;
        }
    }

    @JsonClass(exposeAll = true)
    private static record SessionEndingRequest(String id, boolean wasGraceful, JsonElement metadata) {
    }

    @JsonClass(exposeAll = true)
    private static class SessionEndingResponse {
        public String source = null;
        public boolean loop = false;
    }

    /* ---------------- */
    /*  Session Ended   */
    /* ---------------- */

    public static void sessionEnded(String id) {
        if (Quark.WEBHOOK_URL == null || Quark.WEBHOOK_URL.isEmpty()) return; // dummy mode.

        ASYNC_WEBHOOKS.submit(() -> {
            try {
                post(
                    "SESSION_ENDED",
                    new SessionEndedRequest(id),
                    null
                );
            } catch (IOException e) {
                if (Quark.DEBUG) {
                    e.printStackTrace();
                }
            }
        });
    }

    @JsonClass(exposeAll = true)
    private static record SessionEndedRequest(String id) {
    }

}
