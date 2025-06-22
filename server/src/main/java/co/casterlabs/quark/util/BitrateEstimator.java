package co.casterlabs.quark.util;

import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rakurai.json.annotating.JsonSerializer;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonNumber;
import lombok.NonNull;

@JsonClass(serializer = BitrateEstimatorSerializer.class)
public class BitrateEstimator {
    private static final int WINDOW_SIZE = 120;

    private final Sample[] samples = new Sample[WINDOW_SIZE];
    private int sampleWriteIdx = 0;

    {
        // initialize the array
        for (int i = 0; i < WINDOW_SIZE; i++) {
            this.samples[i] = new Sample();
        }
    }

    public void sample(int sizeBytes, long timestampMillis) {
        Sample sample = this.samples[this.sampleWriteIdx];
        sample.sizeBytes = sizeBytes;
        sample.timestampMillis = timestampMillis;
        this.sampleWriteIdx = (this.sampleWriteIdx + 1) % WINDOW_SIZE; // circular
    }

    public long estimate() {
        long totalBytes = 0;
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;

        for (Sample sample : this.samples) {
            if (sample.sizeBytes == -1) continue;

            totalBytes += sample.sizeBytes;

            long ts = sample.timestampMillis;
            minTime = Math.min(minTime, ts);
            maxTime = Math.max(maxTime, ts);
        }

        if (totalBytes == 0) return 0; // avoid math.

        long windowDuration = maxTime - minTime;
        if (windowDuration <= 0) return 0;

        return (totalBytes * 8 * 1000 /* bits/ms */) / windowDuration; // dividing by window duration changes the unit to bits/second :^)
    }

    private static class Sample {
        private int sizeBytes = -1; // -1 = uninitialized
        private long timestampMillis;
    }

}

class BitrateEstimatorSerializer implements JsonSerializer<BitrateEstimator> {

    @Override
    public JsonElement serialize(@NonNull Object value, @NonNull Rson rson) {
        BitrateEstimator est = (BitrateEstimator) value;

        return new JsonNumber(est.estimate());
    }

}
