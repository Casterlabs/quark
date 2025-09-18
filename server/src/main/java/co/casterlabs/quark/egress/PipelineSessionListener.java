package co.casterlabs.quark.egress;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.flv4j.flv.FLVFileHeader;
import co.casterlabs.flv4j.flv.muxing.NonSeekableFLVDemuxer;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.quark.Quark;
import co.casterlabs.quark.Sessions;
import co.casterlabs.quark.session.Session;
import co.casterlabs.quark.session.SessionProvider;
import co.casterlabs.quark.session.listeners.FLVProcessSessionListener;
import co.casterlabs.quark.session.listeners.StreamFilter;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.element.JsonObject;

public class PipelineSessionListener extends FLVProcessSessionListener {
    private final String fid;
    private final JsonObject metadata;

    public PipelineSessionListener(StreamFilter filter, String fid, @Nullable String resultId, String... command) throws IOException {
        super(
            filter,
            resultId == null ? Redirect.DISCARD : Redirect.PIPE, Redirect.INHERIT,
            command
        );
        this.fid = fid;

        this.metadata = new JsonObject()
            .put("command", Rson.DEFAULT.toJson(command))
            .put("resultId", resultId);

        if (resultId != null) {
            Quark.HEAVY_IO_THREAD_BUILDER
                .name(
                    String.format(
                        "Pipeline Egress - fid=%s -  resultId=%s",
                        fid == null ? "<anonymous>" : fid,
                        resultId == null ? "<anonymous>" : resultId
                    )
                )
                .start(() -> {
                    Session qSession = Sessions.getSession(resultId, true);
                    PipelineProvider provider = new PipelineProvider(qSession);

                    try {
                        provider.demuxer.start(this.stdout());
                    } catch (IOException ignored) {} finally {
                        provider.close(true);
                        this.destroyProc();
                    }
                });
        }
    }

    @Override
    public Type type() {
        return Type.PIPELINE;
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

        private boolean jammed = false;

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
