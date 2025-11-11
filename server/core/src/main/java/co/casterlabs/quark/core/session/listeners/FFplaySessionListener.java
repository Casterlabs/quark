package co.casterlabs.quark.core.session.listeners;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;

import co.casterlabs.quark.core.Quark;

public class FFplaySessionListener extends FLVProcessSessionListener {

    public FFplaySessionListener() throws IOException {
        super(
            StreamFilter.ALL_AUDIO,
            Redirect.DISCARD, Redirect.INHERIT,
            "ffplay",
            "-hide_banner",
            "-loglevel", Quark.FFLL,
            "-x", "1280",
            "-y", "720",
            "-volume", "50",
            "-f", "flv",
            "-"
        );
    }

    @Override
    public String type() {
        return null;
    }

}
