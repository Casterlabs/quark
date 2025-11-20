package co.casterlabs.quark.protocol.hls.session;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.function.Function;

import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.extensibility.QuarkStaticSessionListener;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.quark.core.session.SessionListener;
import co.casterlabs.quark.core.session.listeners.FLVProcessSessionListener;
import co.casterlabs.quark.core.session.listeners.StreamFilter;
import co.casterlabs.quark.core.util.FileUtil;
import co.casterlabs.quark.protocol.hls.HLSEnv;
import co.casterlabs.quark.protocol.hls.HLSProtocol;

@QuarkStaticSessionListener(async = true)
public class HLSSessionListener extends FLVProcessSessionListener {
    public static final Function<Session, SessionListener> FACTORY = (session) -> {
        if (!HLSEnv.EXP_HLS) return null;

        try {
            File folder = new File(HLSProtocol.HLS_ROOT, session.id);
            folder.mkdirs();

            return new HLSSessionListener(StreamFilter.ALL_AUDIO, folder);
        } catch (IOException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return null;
        }
    };

    HLSSessionListener(StreamFilter filter, File folder) throws IOException {
        super(
            filter,
            Redirect.DISCARD, Redirect.INHERIT,
            folder,
            "ffmpeg",
            "-hide_banner",
            "-loglevel", Quark.FFLL,
            "-f", "flv",
            "-i", "-",
            "-c", "copy",
            "-f", "fifo",
//            "-hls_segment_type", "fmp4",
//            "-hls_fmp4_init_filename", "init.mp4",
//            "-hls_segment_filename", "segment%d.mp4",
            "-hls_segment_filename", "segment%d.ts",
            "-hls_flags", "delete_segments",
            "-hls_list_size", "4",
            "-hls_start_number_source", "generic",
            "-f", "hls",
            "playlist.m3u8"
        );

        this.onExit(() -> FileUtil.deleteRecursively(folder));
    }

    @Override
    public String type() {
        return "HLS";
    }

}
