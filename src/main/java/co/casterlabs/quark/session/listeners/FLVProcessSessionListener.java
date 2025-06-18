package co.casterlabs.quark.session.listeners;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;

import co.casterlabs.quark.session.Session;

public abstract class FLVProcessSessionListener extends FLVSessionListener {
    private final Process proc;

    public FLVProcessSessionListener(Redirect out, Redirect err, String... command) throws IOException {
        this.proc = new ProcessBuilder()
            .command(command)
            .redirectOutput(out)
            .redirectError(err)
            .redirectInput(Redirect.PIPE)
            .start();

        this.init(this.proc.getOutputStream());
    }

    protected InputStream stdout() {
        return this.proc.getInputStream();
    }

    protected InputStream stderr() {
        return this.proc.getErrorStream();
    }

    @Override
    public void onClose(Session session) {
        this.proc.destroy();
    }

}
