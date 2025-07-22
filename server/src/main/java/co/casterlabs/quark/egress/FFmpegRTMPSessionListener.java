package co.casterlabs.quark.egress;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;

import co.casterlabs.quark.Quark;
import co.casterlabs.quark.session.listeners.FLVProcessSessionListener;
import co.casterlabs.rakurai.json.element.JsonObject;

public class FFmpegRTMPSessionListener extends FLVProcessSessionListener {
    private final String fid;
    private final JsonObject metadata;

    public FFmpegRTMPSessionListener(String address, String fid) throws IOException {
        super(
            Redirect.DISCARD, Redirect.INHERIT,
            "ffmpeg",
            "-hide_banner",
            "-loglevel", Quark.FFLL,
            "-f", "flv",
            "-i", "-",
            "-c", "copy",
            "-f", "fifo",
            "-fifo_format", "flv",
            "-drop_pkts_on_overflow", "1",
            "-attempt_recovery", "1",
            "-recovery_wait_time", "1",
            "-rtmp_enhanced_codecs", "hvc1,av01,vp09",
            "-map", "0",
            address
        );
        this.fid = fid;

        this.metadata = new JsonObject()
            .put("address", address);
    }

    @Override
    public Type type() {
        return Type.RTMP_EGRESS;
    }

    @Override
    public String fid() {
        return this.fid;
    }

    @Override
    public JsonObject metadata() {
        return this.metadata;
    }

}
