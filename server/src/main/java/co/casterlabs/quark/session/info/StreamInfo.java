package co.casterlabs.quark.session.info;

import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.quark.util.BitrateEstimator;
import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rakurai.json.annotating.JsonExclude;
import co.casterlabs.rakurai.json.element.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@JsonClass(exposeAll = true)
public abstract class StreamInfo {
    private static final long UPDATE_INTERVAL = TimeUnit.MINUTES.toMillis(15);

    public final int id;
    public final BitrateEstimator bitrate = new BitrateEstimator();
    public String codec;

    public @JsonExclude volatile long lastUpdated = 0;
    public @JsonExclude volatile boolean updating = false;

    public abstract void apply(JsonObject ff);

    public boolean needsUpdate() {
        return !updating && System.currentTimeMillis() - this.lastUpdated > UPDATE_INTERVAL;
    }

    @JsonClass(exposeAll = true)
    public static class AudioStreamInfo extends StreamInfo {
        public double sampleRate = -1;
        public int channels = -1;
        public String layout;

        public AudioStreamInfo(int id, @Nullable String codec) {
            super(id);
            this.codec = codec;
        }

        @Override
        public void apply(JsonObject ff) {
            this.lastUpdated = System.currentTimeMillis();
            this.updating = false;

            if (this.codec == null && ff.containsKey("codec_name")) {
                this.codec = ff.getString("codec_name");
            }

            this.sampleRate = Double.parseDouble(ff.getString("sample_rate"));
            this.channels = ff.getNumber("channels").intValue();

            if (ff.containsKey("channel_layout")) {
                this.layout = ff.getString("channel_layout");
            }
        }

    }

    @JsonClass(exposeAll = true)
    public static class VideoStreamInfo extends StreamInfo {
        public int width = -1;
        public int height = -1;
        public double frameRate = -1;
        public String pixelFormat;
        public String colorSpace;
        public String aspectRatio;

        public int keyFrameInterval = -1; // seconds
        public @JsonExclude long lastKeyFrame = -1L;

        public VideoStreamInfo(int id, String codec) {
            super(id);
            this.codec = codec;
        }

        @Override
        public void apply(JsonObject ff) {
            this.lastUpdated = System.currentTimeMillis();
            this.updating = false;

            if (this.codec == null && ff.containsKey("codec_name")) {
                this.codec = ff.getString("codec_name");
            }

            this.width = ff.getNumber("width").intValue();
            this.height = ff.getNumber("height").intValue();

            if (ff.containsKey("pix_fmt")) {
                this.pixelFormat = ff.getString("pix_fmt");
            }
            if (ff.containsKey("color_space")) {
                this.colorSpace = ff.getString("color_space");
            }

            if (ff.containsKey("avg_frame_rate")) {
                this.frameRate = parse(ff.getString("avg_frame_rate"));
            } else if (ff.containsKey("r_frame_rate")) {
                this.frameRate = parse(ff.getString("r_frame_rate"));
            }

            if (ff.containsKey("display_aspect_ratio")) {
                this.aspectRatio = ff.getString("display_aspect_ratio");
            }

            if (this.frameRate == Double.NaN) {
                this.frameRate = -1;
            }
        }

    }

    private static double parse(String timebase) {
        String[] split = timebase.split("/");

        if (split.length == 1) {
            return Double.parseDouble(split[0]);
        } else {
            double numerator = Double.parseDouble(split[0]);
            double denominator = Double.parseDouble(split[1]);
            return numerator / denominator;
        }
    }

}
