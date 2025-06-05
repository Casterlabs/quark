package co.casterlabs.quark.core.util;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;

import co.casterlabs.flv4j.flv.FLVFileHeader;
import co.casterlabs.flv4j.flv.muxing.StreamFLVMuxer;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.quark.core.QuarkSession;
import co.casterlabs.quark.core.QuarkSession.SessionListener;

public class FFPlaySessionListener implements SessionListener {
    private final Process ffplay;
    private final StreamFLVMuxer playbackMuxer;

    private boolean hasGottenSequence = false;

    public FFPlaySessionListener(QuarkSession session) throws IOException {
        this.ffplay = new ProcessBuilder()
            .command(
                "ffplay",
                "-hide_banner",
                "-loglevel", "warning",
                "-x", "1280",
                "-y", "720",
                "-volume", "50",
                "-"
            )
            .redirectError(Redirect.DISCARD)
            .redirectInput(Redirect.PIPE)
            .start();

        this.playbackMuxer = new StreamFLVMuxer(
            new FLVFileHeader(1, 0x4 & 0x1, new byte[0]),
            this.ffplay.getOutputStream()
        );

        session.sequenceRequest();
    }

    @Override
    public void onPacket(QuarkSession session, Object data) {
        if (data instanceof FLVSequenceTag seq) {
            data = seq; // Below...
            this.hasGottenSequence = true;
        }

        if (data instanceof FLVTag tag) {
            if (!this.hasGottenSequence) {
                return;
            }

            try {
                this.playbackMuxer.write(tag);
            } catch (IOException e) {
                e.printStackTrace();
                session.listeners.remove(this);
                this.ffplay.destroy();
            }
        }
    }

    @Override
    public void onClose(QuarkSession session) {
        this.ffplay.destroy();
    }

}
