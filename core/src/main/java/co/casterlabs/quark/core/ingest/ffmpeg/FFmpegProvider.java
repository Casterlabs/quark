package co.casterlabs.quark.core.ingest.ffmpeg;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.concurrent.ThreadFactory;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.flv4j.flv.FLVFileHeader;
import co.casterlabs.flv4j.flv.muxing.NonSeekableFLVDemuxer;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.quark.core.Analytics;
import co.casterlabs.quark.core.Analytics.Usage;
import co.casterlabs.quark.core.Analytics.UsageProvider;
import co.casterlabs.quark.core.Threads;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.quark.core.session.SessionProvider;
import co.casterlabs.rakurai.json.element.JsonObject;

public class FFmpegProvider implements SessionProvider {
    private static final ThreadFactory TF = Threads.heavyIo("FFmpeg Provider");

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

        Thread t = TF.newThread(() -> {
            try {
                this.demuxer.start(this.proc.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                this.close(true);
            }
        });
        t.start();

        Analytics.startCollecting(t::isAlive, new UsageProvider() {
            private long reportedBytes = 0;

            @Override
            public @Nullable Usage get(long deltaDuration) {
                long deltaBytes = demuxer.getBytesRead() - this.reportedBytes;
                this.reportedBytes += deltaBytes;

                return new Usage(
                    session.id,
                    null,
                    "FFMPEG",
                    false,
                    deltaDuration,
                    deltaBytes
                );
            }
        });
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
        this.proc.destroyForcibly();

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
