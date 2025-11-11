package co.casterlabs.quark.protocol.hls;

import java.io.File;

import co.casterlabs.quark.core.extensibility.QuarkEntrypoint;
import co.casterlabs.quark.core.util.FileUtil;

@QuarkEntrypoint
public class HLSProtocol {
    public static final File HLS_ROOT = new File("hls");

    public static void start() {
        FileUtil.deleteRecursively(HLS_ROOT);
    }

}
