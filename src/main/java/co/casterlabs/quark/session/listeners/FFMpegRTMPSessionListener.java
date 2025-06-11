package co.casterlabs.quark.session.listeners;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;

import co.casterlabs.quark.session.QuarkSession;

public class FFMpegRTMPSessionListener extends FLVProcessSessionListener {

    public FFMpegRTMPSessionListener(QuarkSession session, String address) throws IOException {
        super(
            session, Redirect.INHERIT, Redirect.INHERIT,
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
