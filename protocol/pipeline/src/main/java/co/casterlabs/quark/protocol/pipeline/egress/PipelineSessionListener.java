package co.casterlabs.quark.protocol.pipeline.egress;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.flv4j.flv.FLVFileHeader;
import co.casterlabs.flv4j.flv.muxing.NonSeekableFLVDemuxer;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.quark.core.Analytics;
import co.casterlabs.quark.core.Analytics.Usage;
import co.casterlabs.quark.core.Analytics.UsageProvider;
import co.casterlabs.quark.core.Sessions;
import co.casterlabs.quark.core.Threads;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.quark.core.session.SessionProvider;
import co.casterlabs.quark.core.session.listeners.FLVProcessSessionListener;
import co.casterlabs.quark.core.session.listeners.StreamFilter;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.element.JsonObject;

public class PipelineSessionListener extends FLVProcessSessionListener {
    private final String fid;
    private final JsonObject metadata;

    private volatile boolean destroyed = false;

    public PipelineSessionListener(String sessionId, StreamFilter filter, String fid, @Nullable String resultId, String... command) throws IOException {
        super(
            filter,
            resultId == null ? Redirect.DISCARD : Redirect.PIPE, Redirect.INHERIT,
            command
        );
        this.fid = fid;

        this.metadata = new JsonObject()
            .put("command", Rson.DEFAULT.toJson(command))
            .put("resultId", resultId);

        Analytics.startCollecting(() -> !this.destroyed, new UsageProvider() {
            private long reportedBytes = 0;

            @Override
            public @Nullable Usage get(long deltaDuration) {
                long deltaBytes = bytesWritten() - this.reportedBytes;
                this.reportedBytes += deltaBytes;

                return new Usage(
                    sessionId,
                    fid,
                    "PIPELINE",
                    true,
                    deltaDuration,
                    deltaBytes
                );
            }
        });

        if (resultId != null) {
            Session qSession = Sessions.getSession(resultId, true);
            PipelineProvider provider = new PipelineProvider(qSession);

            Thread t = Threads.HEAVY_IO_THREAD_BUILDER
                .name(
                    String.format(
                        "Pipeline Egress - fid=%s - resultId=%s",
                        fid == null ? "<anonymous>" : fid,
                        resultId
                    )
                )
                .start(() -> {
                    try {
                        provider.demuxer.start(this.stdout());
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        this.destroyProc();
                        provider.close(true);
                    }
                });

            Analytics.startCollecting(t::isAlive, new UsageProvider() {
                private long reportedBytes = 0;

                @Override
                public @Nullable Usage get(long deltaDuration) {
                    long deltaBytes = provider.demuxer.getBytesRead() - this.reportedBytes;
                    this.reportedBytes += deltaBytes;

                    return new Usage(
                        resultId,
                        fid,
                        "PIPELINE",
                        false,
                        deltaDuration,
                        deltaBytes
                    );
                }
            });
        }
    }

    @Override
    protected void onClose0(Session session) {
        this.destroyed = true;
        super.onClose0(session);
    }

    @Override
    public String type() {
        return "PIPELINE";
    }

    @Override
    public String fid() {
        return this.fid;
    }

    @Override
    public JsonObject metadata() {
        return this.metadata;
    }

    private class PipelineProvider implements SessionProvider {
        private final Demuxer demuxer = new Demuxer();

        private final Session session;

        private final long dtsOffset;

        private volatile boolean jammed = false;

        private JsonObject metadata;

        public PipelineProvider(Session session) {
            this.session = session;
            this.session.setProvider(this);

            this.dtsOffset = session.prevDts;

            this.metadata = new JsonObject()
                .put("type", "PIPELINE")
                .put("dtsOffset", this.dtsOffset);
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
            destroyProc();

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

}
