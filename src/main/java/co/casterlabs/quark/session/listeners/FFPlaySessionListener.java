package co.casterlabs.quark.session.listeners;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;

public class FFPlaySessionListener extends FLVProcessSessionListener {

    public FFPlaySessionListener() throws IOException {
        super(
            Redirect.INHERIT, Redirect.INHERIT,
            "ffplay",
            "-hide_banner",
            "-loglevel", "warning",
            "-x", "1280",
            "-y", "720",
            "-volume", "50",
            "-f", "flv",
            "-"
        );
    }

}
