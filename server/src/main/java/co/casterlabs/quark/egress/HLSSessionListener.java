package co.casterlabs.quark.egress;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;

import co.casterlabs.quark.Quark;
import co.casterlabs.quark.session.listeners.FLVProcessSessionListener;
import co.casterlabs.quark.session.listeners.StreamFilter;
import co.casterlabs.quark.util.FileUtil;

public class HLSSessionListener extends FLVProcessSessionListener {

    public HLSSessionListener(StreamFilter filter, File folder) throws IOException {
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
    public Type type() {
        return Type.HLS;
    }

}
