package co.casterlabs.quark.session.listeners;

import java.io.IOException;
import java.io.OutputStream;

import co.casterlabs.flv4j.flv.FLVFileHeader;
import co.casterlabs.flv4j.flv.muxing.StreamFLVMuxer;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.flv4j.flv.tags.video.FLVVideoFrameType;
import co.casterlabs.flv4j.flv.tags.video.FLVVideoPayload;
import co.casterlabs.quark.session.FLVSequence;
import co.casterlabs.quark.session.Session;
import co.casterlabs.quark.session.SessionListener;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public abstract class FLVSessionListener extends SessionListener {
    private StreamFLVMuxer playbackMuxer;

    private boolean hasGottenSequence = false;
    private boolean hasOffset = false;

    protected void init(OutputStream out) throws IOException {
        this.playbackMuxer = new StreamFLVMuxer(
            new FLVFileHeader(1, 0x4 & 0x1, new byte[0]),
            out
        );
    }

    private void writeOut(Session session, FLVTag tag) {
        try {
            this.playbackMuxer.write(tag);
        } catch (IOException e) {
//            e.printStackTrace();
            session.removeListener(this);
        }
    }

    @Override
    public void onSequence(Session session, FLVSequence seq) {
        if (this.playbackMuxer == null) return; // Invalid state?

        this.hasGottenSequence = true;
        for (FLVTag tag : seq.tags()) {
            this.writeOut(session, tag);
        }
    }

    @Override
    public void onTag(Session session, FLVTag tag) {
        if (this.playbackMuxer == null) return; // Invalid state?

        if (!this.hasGottenSequence) {
            return;
        }

        if (!this.hasOffset) {
            boolean sessionHasVideo = session.info.video.length > 0;
            boolean isVideoKeyFrame = tag.data() instanceof FLVVideoPayload video && video.frameType() == FLVVideoFrameType.KEY_FRAME;

            if (!sessionHasVideo || isVideoKeyFrame) {
                this.hasOffset = true;
//                this.playbackMuxer.timestampOffset = -tag.timestamp();
                FastLogger.logStatic(LogLevel.DEBUG, "Got offset: %d", this.playbackMuxer.timestampOffset);
                // fall through and write it out.
            } else {
//                FastLogger.logStatic(LogLevel.DEBUG, "Discarding tag before offset: %s", tag);
                return;
            }
        }

        this.writeOut(session, tag);
    }

}
