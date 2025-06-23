package co.casterlabs.quark.session;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.concurrent.TimeUnit;

import co.casterlabs.commons.io.streams.StreamUtil;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.quark.Quark;
import co.casterlabs.quark.session.listeners.FLVProcessSessionListener;

class _ThumbnailSessionListener extends SessionListener {
    private static final long THUMB_INTERVAL = TimeUnit.SECONDS.toMillis(30);

    private volatile long lastThumbnail = 0;
    private volatile boolean isGeneratingThumbnail = false;
    volatile byte[] thumbnail = {};

    @Override
    public void onSequence(Session session, FLVSequence seq) {
        for (FLVTag tag : seq.tags()) {
            this.onData(session, new FLVData(0, tag));
        }
    }

    @Override
    public void onData(Session session, FLVData data) {
        if (session.info.video.length == 0) return; // No video, don't process at all.

        if (this.isGeneratingThumbnail) return;

        boolean isTimeToGenerateThumbnail = System.currentTimeMillis() - this.lastThumbnail > THUMB_INTERVAL;
        if (!isTimeToGenerateThumbnail) return;

        try {
            this.isGeneratingThumbnail = true;
            session.addAsyncListener(new ThumbnailGenerator());
        } catch (IOException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Type type() {
        return null;
    }

    @Override
    public void onClose(Session session) {}

    private class ThumbnailGenerator extends FLVProcessSessionListener {

        public ThumbnailGenerator() throws IOException {
            super(
                Redirect.PIPE, Redirect.INHERIT,
                "ffmpeg",
                "-hide_banner",
                "-loglevel", Quark.FFLL,
                "-f", "flv",
                "-i", "-",
                "-frames:v", "1",
                "-f", "image2pipe",
                "-vcodec", "mjpeg",
                "-"
            );

            Thread.ofVirtual()
                .name("Thumbnail Generator", 0)
                .start(() -> {
                    try {
                        thumbnail = StreamUtil.toBytes(this.stdout());
                    } catch (IOException e) {
                        if (Quark.DEBUG) {
                            e.printStackTrace();
                        }
                    } finally {
                        lastThumbnail = System.currentTimeMillis();
                        isGeneratingThumbnail = false;
                    }
                });
        }

        @Override
        public Type type() {
            return null;
        }

        @Override
        public void onClose(Session session) {}

    }

}
