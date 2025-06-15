package co.casterlabs.quark.ingest.protocols.ffmpeg;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.LinkedList;
import java.util.List;

import co.casterlabs.flv4j.flv.FLVFileHeader;
import co.casterlabs.flv4j.flv.muxing.NonSeekableFLVDemuxer;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.flv4j.flv.tags.script.FLVScriptTagData;
import co.casterlabs.quark.session.FLVData;
import co.casterlabs.quark.session.FLVSequence;
import co.casterlabs.quark.session.QuarkSession;
import co.casterlabs.quark.session.QuarkSessionListener;

public class FFMpegConnection extends QuarkSessionListener implements Closeable {
    private final Demuxer demuxer = new Demuxer();

    private final QuarkSession session;
    private final Process proc;

    private final List<FLVTag> sequenceTags = new LinkedList<>();
    private boolean jammed = false;

    private final long dtsOffset;

    public FFMpegConnection(QuarkSession session, String source) throws IOException {
        this.session = session;
        this.session.addListener(this);

        this.dtsOffset = this.session.prevDts; // The file already has accurate DTS, so we'll just offset (for jamming).

        this.proc = new ProcessBuilder()
            .command(
                "ffmpeg",
                "-hide_banner",
                "-loglevel", "warning",
                "-re",
                "-stream_loop", "-1",
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
                    this.close();
                }
            });
    }

    @Override
    public void onSequenceRequest(QuarkSession session) {
        session.sequence(new FLVSequence(this.sequenceTags.toArray(new FLVTag[0])));
    }

    @Override
    public void onJam(QuarkSession session) {
        this.jammed = true;
        session.removeListener(this);
    }

    @Override
    public void onClose(QuarkSession session) {
        this.close();
    }

    @Override
    public boolean async() {
        return false;
    }

    @Override
    public void close() {
        this.proc.destroy();

        if (!this.jammed) {
            this.session.close();
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
                (tag.timestamp() + dtsOffset) & 0xFFFFFFFF, // Rewrite the DTS
                tag.streamId(),
                tag.data()
            );

            if (tag.data() instanceof FLVScriptTagData script) {
                if (script.methodName().equals("@setDataFrame")) {
                    sequenceTags.add(tag);
                    session.sequence(new FLVSequence(tag)); // /shrug/
                    return;
                }
            } else if (tag.data().isSequenceHeader()) {
                sequenceTags.add(tag);
                session.sequence(new FLVSequence(tag)); // /shrug/
                return;
            }

            session.data(new FLVData(tag.timestamp(), tag));
        }

    }

}
