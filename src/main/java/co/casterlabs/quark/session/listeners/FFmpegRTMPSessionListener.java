package co.casterlabs.quark.session.listeners;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;

public class FFmpegRTMPSessionListener extends FLVProcessSessionListener {

    public FFmpegRTMPSessionListener(String address) throws IOException {
        super(
            Redirect.INHERIT, Redirect.INHERIT,
            "ffmpeg",
            "-hide_banner",
            "-loglevel", "warning",
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
    }

}
