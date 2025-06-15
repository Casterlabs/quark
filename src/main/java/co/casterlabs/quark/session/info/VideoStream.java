package co.casterlabs.quark.session.info;

import co.casterlabs.rakurai.json.annotating.JsonClass;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@JsonClass(exposeAll = true)
public class VideoStream {
    public final int id;
    public final String codec; // fourcc

    public final BitrateEstimator bitrate = new BitrateEstimator();

    public int keyFrameInterval = -1; // seconds

}
