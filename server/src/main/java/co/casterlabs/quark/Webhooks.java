package co.casterlabs.quark;

import java.io.IOException;

import co.casterlabs.quark.ingest.protocols.ffmpeg.FFmpegProvider;
import co.casterlabs.quark.session.Session;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rakurai.json.element.JsonObject;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Webhooks {
    private static final OkHttpClient client = new OkHttpClient();

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
    /*  Session Start   */
    /* ---------------- */

    /**
     * @return null, if the session was disallowed.
     */
    public static String sessionStart(String url, String key) {
        if (Quark.WEBHOOK_URL == null) return key; // dummy mode.

        try {
            SessionStartResponse res = post(
                "SESSION_START",
                new SessionStartRequest(url, key),
                SessionStartResponse.class
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
    private static record SessionStartRequest(String url, String key) {
    }

    @JsonClass(exposeAll = true)
    private static class SessionStartResponse {
        public String id = null;
    }

    /* ---------------- */
    /*   Session End    */
    /* ---------------- */

    /**
     * @return whether or not the session is being jammed.
     */
    public static boolean sessionEnding(Session session, boolean wasGraceful) {
        if (Quark.WEBHOOK_URL == null) return false; // dummy mode.

        try {
            SessionEndingResponse res = post(
                "SESSION_ENDING",
                new SessionEndingRequest(session.id, wasGraceful),
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
    private static record SessionEndingRequest(String id, boolean wasGraceful) {
    }

    @JsonClass(exposeAll = true)
    private static class SessionEndingResponse {
        public String source = null;
        public boolean loop = false;
    }

    public static void sessionEnded(Session session, boolean wasGraceful) {
        if (Quark.WEBHOOK_URL == null) return; // dummy mode.

        try {
            post(
                "SESSION_ENDED",
                new SessionEndingRequest(session.id, wasGraceful),
                null
            );
        } catch (IOException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
        }
    }

}
