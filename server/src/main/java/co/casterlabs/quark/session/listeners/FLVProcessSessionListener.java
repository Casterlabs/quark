package co.casterlabs.quark.session.listeners;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;

import co.casterlabs.quark.session.Session;

public abstract class FLVProcessSessionListener extends FLVSessionListener {
    private final Process proc;

    public FLVProcessSessionListener(StreamFilter filter, Redirect out, Redirect err, String... command) throws IOException {
        this(filter, out, err, null, command);
    }

    public FLVProcessSessionListener(StreamFilter filter, Redirect out, Redirect err, File dir, String... command) throws IOException {
        super(filter);

        this.proc = new ProcessBuilder()
            .command(command)
            .directory(dir)
            .redirectOutput(out)
            .redirectError(err)
            .redirectInput(Redirect.PIPE)
            .start();

        this.init(this.proc.getOutputStream());
    }

    protected void onExit(Runnable run) {
        this.proc.onExit().thenRun(run);
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

    protected void destroyProc() {
        this.proc.destroy();
    }

}
