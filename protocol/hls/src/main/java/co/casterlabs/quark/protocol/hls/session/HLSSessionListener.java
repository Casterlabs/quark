package co.casterlabs.quark.protocol.hls.session;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

@QuarkStaticSessionListener(async = true, requiresInfo = true)
public class HLSSessionListener extends FLVProcessSessionListener {
    public static final Function<Session, SessionListener> FACTORY = (session) -> {
        if (!HLSEnv.EXP_HLS) return null;

        try {
            File folder = new File(HLSProtocol.HLS_ROOT, session.id);
            if (!folder.exists() && !folder.mkdirs()) {
                throw new IOException("Failed to create folder: " + folder.getAbsolutePath());
            }

            List<String> command = new ArrayList<>();

            command.addAll(
                List.of(
                    "ffmpeg",
                    "-hide_banner",
                    "-loglevel", Quark.FFLL,

                    "-f", "flv",
                    "-i", "-",

                    "-c", "copy",
                    "-copyts", "-start_at_zero",

                    "-f", "hls",
                    "-hls_time", "2",
                    "-hls_list_size", "4",
                    "-hls_delete_threshold", "4",
                    "-hls_start_number_source", "generic",
                    "-hls_flags", "delete_segments"
                )
            );

            System.out.println(Arrays.asList(session.info.video));
            if (session.info.video.length > 0 && "hvc1".equals(session.info.video[0].codec)) {
                command.addAll(
                    List.of(
                        "-tag:v", "hvc1"
                    )
                );
            }

            if (HLSEnv.EXP_HLS_FMP4) {
                command.addAll(
                    List.of(
                        "-hls_segment_type", "fmp4",
                        "-hls_fmp4_init_filename", "init.mp4",
                        "-hls_segment_filename", "segment%d.m4s"
                    )
                );
            } else {
                command.addAll(
                    List.of(
                        "-hls_segment_type", "mpegts",
                        "-hls_segment_filename", "segment%d.ts"
                    )
                );
            }

            command.add("playlist.m3u8");

            return new HLSSessionListener(command.toArray(new String[0]), StreamFilter.ALL_AUDIO, folder);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    };

    HLSSessionListener(String[] command, StreamFilter filter, File folder) throws IOException {
        super(
            filter,
            Redirect.DISCARD, Redirect.INHERIT,
            folder,
            command
        );

        this.onExit(() -> FileUtil.deleteRecursively(folder));
    }

    @Override
    public String type() {
        return "HLS";
    }

}
