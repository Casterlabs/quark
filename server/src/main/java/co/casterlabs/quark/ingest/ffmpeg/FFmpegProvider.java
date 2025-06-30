package co.casterlabs.quark.ingest.ffmpeg;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;

import co.casterlabs.flv4j.flv.FLVFileHeader;
import co.casterlabs.flv4j.flv.muxing.NonSeekableFLVDemuxer;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.quark.session.Session;
import co.casterlabs.quark.session.SessionProvider;

public class FFmpegProvider implements SessionProvider {
    private final Demuxer demuxer = new Demuxer();

    private final Session session;
    private final Process proc;

    private final long dtsOffset;

    private boolean jammed = false;

    public FFmpegProvider(Session session, String source, boolean loop) throws IOException {
        this.session = session;
        this.session.setProvider(this);

        this.dtsOffset = session.prevDts;

        this.proc = new ProcessBuilder()
            .command(
                "ffmpeg",
                "-hide_banner",
                "-loglevel", "warning",
                "-re",
                "-stream_loop", loop ? "-1" : "0",
                "-i", source,
                "-c", "copy",
                "-f", "flv",
                "-"
            )
            .redirectOutput(Redirect.PIPE)
            .redirectError(Redirect.INHERIT)
            .redirectInput(Redirect.PIPE)
            .start();

        Thread.ofPlatform()
            .name("FFMpeg Ingest - " + this.session.id)
            .start(() -> {
                try {
                    this.demuxer.start(this.proc.getInputStream());
                } catch (IOException ignored) {} finally {
                    this.close(true);
                }
            });
    }

    @Override
    public void jam() {
        this.jammed = true;
        this.close(true);
    }

    @Override
    public void close(boolean graceful) {
        this.proc.destroy();

        if (!this.jammed) {
            this.session.close(graceful);
        }
    }

    private class Demuxer extends NonSeekableFLVDemuxer {

        @Override
        protected void onHeader(FLVFileHeader header) {} // ignore.

        @Override
        protected void onTag(long previousTagSize, FLVTag tag) {
            if (jammed) return; // Just in case.

            tag = new FLVTag(
                tag.type(),
                (tag.timestamp() + dtsOffset) & 0xFFFFFFFFL, // rewrite with our offset
                tag.streamId(),
                tag.data()
            );

            session.tag(tag);
        }

    }

}
