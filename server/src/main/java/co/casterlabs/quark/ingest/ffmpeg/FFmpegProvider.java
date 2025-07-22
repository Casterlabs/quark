package co.casterlabs.quark.ingest.ffmpeg;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.concurrent.ThreadFactory;

import co.casterlabs.flv4j.flv.FLVFileHeader;
import co.casterlabs.flv4j.flv.muxing.NonSeekableFLVDemuxer;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.quark.Quark;
import co.casterlabs.quark.session.Session;
import co.casterlabs.quark.session.SessionProvider;
import co.casterlabs.rakurai.json.element.JsonObject;

public class FFmpegProvider implements SessionProvider {
    private static final ThreadFactory TF = (Quark.EXPR_VIRTUAL_THREAD_HEAVY_IO ? Thread.ofVirtual() : Thread.ofPlatform()) //
        .name("FFmpeg Provider", 0).factory();

    private final Demuxer demuxer = new Demuxer();

    private final Session session;
    private final Process proc;

    private final long dtsOffset;

    private boolean jammed = false;

    private JsonObject metadata;

    public FFmpegProvider(Session session, String source, boolean loop) throws IOException {
        this.session = session;
        this.session.setProvider(this);

        this.dtsOffset = session.prevDts;

        this.metadata = new JsonObject()
            .put("type", "FFMPEG")
            .put("source", source)
            .put("loop", loop)
            .put("dtsOffset", this.dtsOffset);

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

        TF.newThread(() -> {
            try {
                this.demuxer.start(this.proc.getInputStream());
            } catch (IOException ignored) {} finally {
                this.close(true);
            }
        }).start();
    }

    @Override
    public JsonObject metadata() {
        return this.metadata;
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
