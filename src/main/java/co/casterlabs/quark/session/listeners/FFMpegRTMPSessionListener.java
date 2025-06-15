package co.casterlabs.quark.session.listeners;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;

public class FFMpegRTMPSessionListener extends FLVProcessSessionListener {

    public FFMpegRTMPSessionListener(String address) throws IOException {
        super(
            Redirect.INHERIT, Redirect.INHERIT,
            "ffmpeg",
            "-hide_banner",
            "-loglevel", "warning",
            "-f", "flv",
            "-i", "-",
            "-c", "copy",
            "-f", "flv",
            address
        );
    }

}
