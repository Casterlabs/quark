package co.casterlabs.quark;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import co.casterlabs.quark.ingest.ffmpeg.FFmpegProvider;
import co.casterlabs.quark.session.Session;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonObject;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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
    public static String sessionStarting(String ip, String url, String app, String key) {
        if (Quark.WEBHOOK_URL == null || Quark.WEBHOOK_URL.isEmpty()) return key; // dummy mode.

        try {
            SessionStartingResponse res = post(
                "SESSION_STARTING",
                new SessionStartingRequest(ip, url, app, key),
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
    private static record SessionStartingRequest(String ip, String url, String app, String key) {
    }

    @JsonClass(exposeAll = true)
    private static class SessionStartingResponse {
        public String id = null;
    }

    /* ---------------- */
    /* Session Started  */
    /* ---------------- */

    public static void sessionStarted(String id) {
        if (Quark.WEBHOOK_URL == null || Quark.WEBHOOK_URL.isEmpty()) return; // dummy mode.

        ASYNC_WEBHOOKS.submit(() -> {
            try {
                post(
                    "SESSION_STARTED",
                    new SessionStartedRequest(id),
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
    private static record SessionStartedRequest(String id) {
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
