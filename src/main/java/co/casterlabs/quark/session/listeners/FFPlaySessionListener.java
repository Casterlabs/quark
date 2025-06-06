package co.casterlabs.quark.session.listeners;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;

import co.casterlabs.quark.session.QuarkSession;

public class FFPlaySessionListener extends FLVProcessSessionListener {

    public FFPlaySessionListener(QuarkSession session) throws IOException {
        super(
            session, Redirect.INHERIT, Redirect.INHERIT,
            "ffplay",
            "-hide_banner",
            "-loglevel", "warning",
            "-x", "1280",
            "-y", "720",
            "-volume", "50",
            "-"
        );
    }

}
