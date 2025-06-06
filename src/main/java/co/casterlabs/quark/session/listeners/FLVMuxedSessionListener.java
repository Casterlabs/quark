package co.casterlabs.quark.session.listeners;

import java.io.IOException;
import java.io.OutputStream;

import co.casterlabs.flv4j.flv.FLVFileHeader;
import co.casterlabs.flv4j.flv.muxing.StreamFLVMuxer;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.quark.session.QuarkSession;
import co.casterlabs.quark.session.QuarkSessionListener;
import co.casterlabs.quark.util.FLVSequenceTag;

public abstract class FLVMuxedSessionListener extends QuarkSessionListener {
    private StreamFLVMuxer playbackMuxer;

    private boolean hasGottenSequence = false;
//    private boolean hasOffset = false;

    protected void init(OutputStream out) throws IOException {
        this.playbackMuxer = new StreamFLVMuxer(
            new FLVFileHeader(1, 0x4 & 0x1, new byte[0]),
            out
        );
    }

    private void writeOut(QuarkSession session, FLVTag tag) {
        try {
            this.playbackMuxer.write(tag);
        } catch (IOException e) {
            e.printStackTrace();
            session.removeListener(this);
            this.onClose(session);
        }
    }

    @Override
    public void onPacket(QuarkSession session, Object data) {
        if (this.playbackMuxer == null) return; // Invalid state?

        if (data instanceof FLVSequenceTag seq) {
            this.hasGottenSequence = true;
            this.writeOut(session, seq.tag());
        } else if (data instanceof FLVTag tag) {
            if (!this.hasGottenSequence) {
                return;
            }

//            if (!this.hasOffset) {
//                this.hasOffset = true;
//                this.playbackMuxer.timestampOffset = -tag.timestamp();
//            }

            this.writeOut(session, tag);
        }
    }

}
