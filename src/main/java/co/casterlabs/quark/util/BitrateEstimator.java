package co.casterlabs.quark.util;

import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rakurai.json.annotating.JsonSerializer;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonNumber;
import lombok.NonNull;

@JsonClass(serializer = BitrateEstimatorSerializer.class)
public class BitrateEstimator {
    private static final int WINDOW_SIZE = 60;

    private record Sample(int sizeBytes, long timestampMillis) {
    }

    private final Sample[] samples = new Sample[WINDOW_SIZE];
    private int index = 0;
    private boolean isFull = false;

    public void sample(int sizeBytes, long timestampMillis) {
        this.samples[this.index] = new Sample(sizeBytes, timestampMillis);
        this.index = (this.index + 1) % WINDOW_SIZE;
        if (this.index == 0) {
            this.isFull = true;
        }
    }

    public long estimate() {
        int count = this.isFull ? WINDOW_SIZE : this.index;
        if (count < 2) return 0;

        int totalBytes = 0;
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;

        for (int i = 0; i < count; i++) {
            Sample s = this.samples[i];
            if (s == null) continue;
            totalBytes += s.sizeBytes();
            long ts = s.timestampMillis();
            minTime = Math.min(minTime, ts);
            maxTime = Math.max(maxTime, ts);
        }

        long durationMillis = maxTime - minTime;
        if (durationMillis <= 0) return 0;

        return (totalBytes * 8L * 1000) / durationMillis; // bits/sec
    }

}

class BitrateEstimatorSerializer implements JsonSerializer<BitrateEstimator> {

    @Override
    public JsonElement serialize(@NonNull Object value, @NonNull Rson rson) {
        BitrateEstimator est = (BitrateEstimator) value;

        return new JsonNumber(est.estimate());
    }

}
