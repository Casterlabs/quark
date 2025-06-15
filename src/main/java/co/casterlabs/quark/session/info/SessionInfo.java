package co.casterlabs.quark.session.info;

import co.casterlabs.quark.session.info.StreamInfo.AudioStreamInfo;
import co.casterlabs.quark.session.info.StreamInfo.VideoStreamInfo;
import co.casterlabs.rakurai.json.annotating.JsonClass;

@JsonClass(exposeAll = true)
public class SessionInfo {
    public VideoStreamInfo[] video = {};
    public AudioStreamInfo[] audio = {};

}
