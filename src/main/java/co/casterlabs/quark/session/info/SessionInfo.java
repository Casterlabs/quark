package co.casterlabs.quark.session.info;

import co.casterlabs.rakurai.json.annotating.JsonClass;

@JsonClass(exposeAll = true)
public class SessionInfo {
    public VideoStream[] video = {};
    public AudioStream[] audio = {};

}
