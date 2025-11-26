package co.casterlabs.quark.core.session.listeners;

import java.io.IOException;
import java.io.OutputStream;

import co.casterlabs.flv4j.flv.FLVFileHeader;
import co.casterlabs.flv4j.flv.muxing.StreamFLVMuxer;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.flv4j.flv.tags.video.FLVStandardVideoTagData;
import co.casterlabs.flv4j.flv.tags.video.FLVVideoFrameType;
import co.casterlabs.quark.core.session.FLVSequence;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.quark.core.session.SessionListener;
import lombok.RequiredArgsConstructor;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

@RequiredArgsConstructor
public abstract class FLVSessionListener extends SessionListener {
    private StreamFLVMuxer playbackMuxer;

    private boolean hasGottenSequence = false;
    private boolean hasOffset = false;

    private final StreamFilter filter;

    protected void init(OutputStream out) throws IOException {
        this.playbackMuxer = new StreamFLVMuxer(
            new FLVFileHeader(1, 0x4 & 0x1, new byte[0]),
            out
        );
    }

    private void writeTag(Session session, FLVTag tag) {
        if (this.playbackMuxer == null) return; // Invalid state?

        tag = this.filter.transform(tag);
        if (tag == null) return; // tag should be dropped!

        try {
            this.playbackMuxer.write(tag);
        } catch (IOException e) {
//            e.printStackTrace();
            session.removeListener(this);
        }
    }

    @Override
    public void onSequence(Session session, FLVSequence seq) {
        this.hasGottenSequence = true;

        for (FLVTag tag : seq.tags()) {
            this.writeTag(session, tag);
        }
    }

    @Override
    public void onTag(Session session, FLVTag tag) {
        if (!this.hasGottenSequence) return;

        if (!this.hasOffset) {
            boolean sessionHasVideo = session.info.video.length > 0;
            boolean isVideoKeyFrame = tag.data() instanceof FLVStandardVideoTagData video && video.frameType() == FLVVideoFrameType.KEY_FRAME;

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

        this.writeTag(session, tag);
    }

}
