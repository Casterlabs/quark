package co.casterlabs.quark.protocol.webrtc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import co.casterlabs.commons.io.streams.StreamUtil;
import co.casterlabs.commons.platform.OSFamily;
import co.casterlabs.commons.platform.Platform;
import co.casterlabs.quark.core.extensibility.QuarkEntrypoint;
import co.casterlabs.quark.core.util.FF;

@QuarkEntrypoint
public class WebRTCBinPrep {
    private static final String FILE_EXTENSION = Platform.osFamily == OSFamily.WINDOWS ? ".exe" : "";

    public static final File INGEST_BINARY = new File(System.getProperty("java.io.tmpdir"), "webrtc-ingest" + FILE_EXTENSION);

    public static void start() throws FileNotFoundException, IOException {
        if (!WebRTCEnv.EXP_WHIP || !FF.canUseMpeg) {
            return; // Disabled
        }

        // Pretty terrible way to do this, but oh well.
        boolean isWindows = Platform.osFamily == OSFamily.WINDOWS;
        boolean isAmd64 = Platform.archTarget.equals("x86_64");

        String binRoot = "/bin";

        if (isWindows) {
            binRoot += "/windows-";
        } else {
            binRoot += "/linux-";
        }

        if (isAmd64) {
            binRoot += "amd64/";
        } else {
            binRoot += "arm64/";
        }

        INGEST_BINARY.delete();
        try (
            InputStream in = WebRTCBinPrep.class.getResourceAsStream(binRoot + "webrtc-ingest" + FILE_EXTENSION);
            OutputStream out = new FileOutputStream(INGEST_BINARY);) {
            if (in == null) {
                throw new FileNotFoundException("Could not find embedded webrtc-ingest binary for platform in: " + binRoot);
            }

            StreamUtil.streamTransfer(in, out, 8192);
            INGEST_BINARY.setExecutable(true);
        }
    }

}
