package co.casterlabs.quark.protocol.rtmp.egress;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.quark.core.Analytics;
import co.casterlabs.quark.core.Analytics.Usage;
import co.casterlabs.quark.core.Analytics.UsageProvider;
import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.quark.core.session.listeners.FLVProcessSessionListener;
import co.casterlabs.quark.core.session.listeners.StreamFilter;
import co.casterlabs.rakurai.json.element.JsonObject;

public class FFmpegRTMPSessionListener extends FLVProcessSessionListener {
    private final String fid;
    private final JsonObject metadata;

    private volatile boolean destroyed = false;

    public FFmpegRTMPSessionListener(String sessionId, StreamFilter filter, String address, String fid) throws IOException {
        super(
            filter,
            Redirect.DISCARD, Redirect.INHERIT,
            "ffmpeg",
            "-hide_banner",
            "-loglevel", Quark.FFLL,
            "-f", "flv",
            "-i", "-",
            "-c", "copy",
            "-f", "fifo",
            "-fifo_format", "flv",
            "-drop_pkts_on_overflow", "1",
            "-attempt_recovery", "1",
            "-recovery_wait_time", "1",
            "-rtmp_enhanced_codecs", "hvc1,av01,vp09",
            "-map", "0",
            address
        );
        this.fid = fid;

        this.metadata = new JsonObject()
            .put("address", address);

        Analytics.startCollecting(() -> !this.destroyed, new UsageProvider() {
            private long reportedBytes = 0;

            @Override
            public @Nullable Usage get(long deltaDuration) {
                long deltaBytes = bytesWritten() - this.reportedBytes;
                this.reportedBytes += deltaBytes;

                return new Usage(
                    sessionId,
                    fid,
                    "RTMP",
                    true,
                    deltaDuration,
                    deltaBytes
                );
            }
        });
    }

    @Override
    protected void onClose0(Session session) {
        this.destroyed = true;
        super.onClose0(session);
    }

    @Override
    public String type() {
        return "RTMP_EGRESS";
    }

    @Override
    public String fid() {
        return this.fid;
    }

    @Override
    public JsonObject metadata() {
        return this.metadata;
    }

}
