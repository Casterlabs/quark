package co.casterlabs.quark.core.util;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.Charset;

import co.casterlabs.commons.io.streams.StreamUtil;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class FF {
    public static final boolean canUseMpeg = check("ffmpeg");
    public static final boolean canUseProbe = check("ffprobe");
    public static final boolean canUsePlay = check("ffplay");

    public static void init() {} // dummy

    // Must be ran in the static initializer to ensure that we know whether we can
    // use ffmpeg or not before we try to use it.
    private static boolean check(String executable) {
        Process proc = null;
        try {
            proc = new ProcessBuilder(executable, "-version")
                .redirectInput(Redirect.PIPE)
                .redirectError(Redirect.DISCARD)
                .redirectOutput(Redirect.PIPE)
                .start();

            String output = StreamUtil.toString(proc.getInputStream(), Charset.defaultCharset()).replace("\r\n", "\n");
            int exitCode = proc.waitFor();

            if (exitCode == 0) {
                FastLogger.logStatic(LogLevel.INFO, output);
                return true;
            } else {
                FastLogger.logStatic(LogLevel.WARNING, "Got non-zero exit code whilst checking %s: %d\n%s", executable, exitCode, output);
            }
        } catch (IOException | InterruptedException e) {
            FastLogger.logStatic(LogLevel.WARNING, "An error occurred while checking for %s:\n%s", executable, e);
        } finally {
            if (proc != null) {
                proc.destroyForcibly();
            }
        }
        return false;
    }

}
