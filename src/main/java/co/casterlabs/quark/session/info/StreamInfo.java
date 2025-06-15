package co.casterlabs.quark.session.info;

import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rakurai.json.annotating.JsonExclude;
import co.casterlabs.rakurai.json.element.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@JsonClass(exposeAll = true)
public abstract class StreamInfo {
    public final int id;
    public final String codec;

    public final BitrateEstimator bitrate = new BitrateEstimator();

    public @JsonExclude boolean isUpdatingFF = false;

    public abstract void apply(JsonObject ff);

    @JsonClass(exposeAll = true)
    public static class AudioStreamInfo extends StreamInfo {
        public double sampleRate = -1;
        public int channels = -1;
        public String layout;

        public AudioStreamInfo(int id, String codec) {
            super(id, codec);
        }

        @Override
        public void apply(JsonObject ff) {
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

        public VideoStreamInfo(int id, String codec) {
            super(id, codec);
        }

        @Override
        public void apply(JsonObject ff) {
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
