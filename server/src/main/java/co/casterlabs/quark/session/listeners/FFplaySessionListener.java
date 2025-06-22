package co.casterlabs.quark.session.listeners;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;

import co.casterlabs.quark.Quark;

public class FFplaySessionListener extends FLVProcessSessionListener {

    public FFplaySessionListener() throws IOException {
        super(
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
    public Type type() {
        return null;
    }

}
