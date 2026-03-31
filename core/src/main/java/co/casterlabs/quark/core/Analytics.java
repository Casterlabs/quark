package co.casterlabs.quark.core;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rakurai.json.element.JsonObject;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Analytics {
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(180, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build();

    public static final boolean ENABLED = Quark.EXP_ANALYTICS_URL != null;

    private static final ThreadFactory TF = Threads.misc("Analytics");

    private static final List<Usage> pendingUsage = new LinkedList<>();

    static {
        if (ENABLED) {
            Thread thread = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(Quark.EXP_ANALYTICS_INTERVAL);
                        drain();
                    } catch (Throwable t) {
                        if (Quark.DEBUG) {
                            t.printStackTrace();
                        }
                    }
                }
            });

            thread.setName("Analytics Drainer");
            thread.setDaemon(true);
            thread.start();
        }
    }

    private static void post(JsonObject data) throws IOException {
        JsonObject payload = new JsonObject()
            .put("type", "ANALYTICS")
            .put("data", data);

        Call call = client.newCall(
            new Request.Builder()
                .url(Quark.EXP_ANALYTICS_URL)
                .post(
                    RequestBody.create(
                        payload.toString(true),
                        MediaType.parse("application/json")
                    )
                )
                .build()
        );
        try (Response res = call.execute()) {
            String body = res.body() != null ? res.body().string() : "";

            if (!res.isSuccessful()) {
                throw new IOException(res.code() + ": " + body);
            }
        }
    }

    private static void drain() {
        synchronized (pendingUsage) {
            if (pendingUsage.isEmpty()) return;

            JsonObject payload = new JsonObject()
                .put("usages", Rson.DEFAULT.toJson(pendingUsage));

            try {
                post(payload);
                pendingUsage.clear();
            } catch (IOException e) {
                if (Quark.DEBUG) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void record(@Nullable Usage usage) {
        if (!ENABLED) return;
        if (usage == null) return;

        synchronized (pendingUsage) {
            pendingUsage.add(usage);
        }
    }

    /**
     * @param shouldContinue When this returns false, the analytics thread will
     *                       stop.
     * @param provider       A function that will be called periodically, and should
     *                       return Usage to be sent to be collected.
     */
    public static void startCollecting(Supplier<Boolean> shouldContinue, UsageProvider provider) {
        if (!ENABLED) return;

        TF.newThread(() -> {
            long lastUpdated = System.currentTimeMillis();

            while (shouldContinue.get()) {
                try {
                    long deltaDuration = System.currentTimeMillis() - lastUpdated;
                    lastUpdated = System.currentTimeMillis();

                    Usage usage = provider.get(deltaDuration);
                    record(usage);

                    Thread.sleep(Quark.EXP_ANALYTICS_INTERVAL);
                } catch (Throwable t) {
                    if (Quark.DEBUG) {
                        t.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public static interface UsageProvider {

        public @Nullable Usage get(long deltaDuration);

    }

    @JsonClass(exposeAll = true)
    public static record Usage(
        String sessionId,
        @Nullable String foreignId,
        String type,
        boolean isEgress,
        long deltaDuration,
        long deltaBytes
    ) {

    }

}
