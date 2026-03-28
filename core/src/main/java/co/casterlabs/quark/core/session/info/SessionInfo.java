package co.casterlabs.quark.core.session.info;

import co.casterlabs.quark.core.session.info.StreamInfo.AudioStreamInfo;
import co.casterlabs.quark.core.session.info.StreamInfo.VideoStreamInfo;
import co.casterlabs.rakurai.json.annotating.JsonClass;

@JsonClass(exposeAll = true)
public class SessionInfo {
    public volatile VideoStreamInfo[] video = {};
    public volatile AudioStreamInfo[] audio = {};

}
