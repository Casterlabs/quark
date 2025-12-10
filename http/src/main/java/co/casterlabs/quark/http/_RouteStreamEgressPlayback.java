package co.casterlabs.quark.http;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import co.casterlabs.commons.io.streams.StreamUtil;
import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.auth.AuthenticationException;
import co.casterlabs.quark.core.auth.User;
import co.casterlabs.quark.core.http.ApiResponse;
import co.casterlabs.quark.core.http.QuarkHttpProcessor;
import co.casterlabs.quark.core.http.QuarkHttpProcessor.EndpointContext;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.quark.core.session.SessionListener;
import co.casterlabs.quark.core.session.listeners.FLVProcessSessionListener;
import co.casterlabs.quark.core.session.listeners.FLVSessionListener;
import co.casterlabs.quark.core.session.listeners.StreamFilter;
import co.casterlabs.quark.core.util.FF;
import co.casterlabs.rakurai.json.element.JsonObject;
import co.casterlabs.rhs.HttpMethod;
import co.casterlabs.rhs.HttpStatus.StandardHttpStatus;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointData;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointProvider;
import co.casterlabs.rhs.protocol.api.endpoints.HttpEndpoint;
import co.casterlabs.rhs.protocol.http.HttpResponse;
import co.casterlabs.rhs.protocol.http.HttpResponse.ResponseContent;
import co.casterlabs.rhs.protocol.http.HttpSession;
import lombok.RequiredArgsConstructor;

public class _RouteStreamEgressPlayback implements EndpointProvider {
    private static final Map<String, MuxFormat> MUX_FORMATS = Map.of(
        // Audio reencoding:
        "opus", new MuxFormat(
            /*mime*/"audio/ogg",
            "ffmpeg",
            "-hide_banner",
            "-loglevel", "quiet",
            "-strict", "0",
            "-i", "-",
            "-vn",
            "-c:a", "libopus",
            "-b:a", "320k",
            "-f", "ogg",
            "-"
        ),
        "mp3", new MuxFormat(
            /*mime*/"audio/mpeg",
            "ffmpeg",
            "-hide_banner",
            "-loglevel", "quiet",
            "-strict", "0",
            "-i", "-",
            "-vn",
            "-c:a", "mp3",
            "-b:a", "320k",
            "-f", "mp3",
            "-"
        ),

        // Passthrough remuxing:
        // Can use https://github.com/xqq/mpegts.js for both ts and flv
        "ts", new MuxFormat(
            /*mime*/"video/mp2t",
            "ffmpeg",
            "-hide_banner",
            "-loglevel", "quiet",
            "-i", "-",
            "-c", "copy",
            "-f", "mpegts",
            "-"
        ),
        "mkv", new MuxFormat(
            /*mime*/"video/x-matroska",
            "ffmpeg",
            "-hide_banner",
            "-loglevel", "quiet",
            "-i", "-",
            "-c", "copy",
            "-f", "matroska",
            "-"
        ),
        "webm", new MuxFormat(
            // NB: this doesn't trick Firefox nor Safari into playing non-standard codecs,
            // but it does trick Chrome into doing so, and it works surprisingly well!
            /*mime*/"video/webm",
            "ffmpeg",
            "-hide_banner",
            "-loglevel", "quiet",
            "-i", "-",
            "-c", "copy",
            "-f", "matroska",
            "-"
        )
    );

    private static record MuxFormat(String mime, String... command) {
    }

    @HttpEndpoint(path = "/session/:sessionId/egress/playback/flv", allowedMethods = {
            HttpMethod.GET
    }, priority = 10, postprocessor = QuarkHttpProcessor.class, preprocessor = QuarkHttpProcessor.class)
    public HttpResponse onFLVPlayback(HttpSession session, EndpointData<EndpointContext> data) {
        User user = data.attachment().user();
        Session qSession = data.attachment().session();

        try {
            user.checkPlayback(qSession.id);

            StreamFilter filter = StreamFilter.from(session.uri().query);

            // This one's special!
            return new HttpResponse(
                new FLVResponseContent(filter, qSession, user.id()),
                StandardHttpStatus.OK
            )
                .mime("video/x-flv")
                .header("Cache-Control", "private, max-age=0, no-store");
        } catch (AuthenticationException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return ApiResponse.UNAUTHORIZED.response();
        } catch (Throwable t) {
            if (Quark.DEBUG) {
                t.printStackTrace();
            }
            return ApiResponse.INTERNAL_ERROR.response();
        }
    }

    @HttpEndpoint(path = "/session/:sessionId/egress/playback/:format", allowedMethods = {
            HttpMethod.GET
    }, postprocessor = QuarkHttpProcessor.class, preprocessor = QuarkHttpProcessor.class)
    public HttpResponse onMuxedPlayback(HttpSession session, EndpointData<EndpointContext> data) {
        User user = data.attachment().user();
        Session qSession = data.attachment().session();

        try {
            user.checkPlayback(qSession.id);

            if (!FF.canUseMpeg) {
                return ApiResponse.NOT_ENABLED.response();
            }

            MuxFormat format = MUX_FORMATS.get(data.uriParameters().get("format"));
            if (format == null) {
                return ApiResponse.BAD_REQUEST.response();
            }

            StreamFilter filter = StreamFilter.from(session.uri().query);

            return new HttpResponse(
                new RemuxedResponseContent(filter, qSession, user.id(), format.mime, format.command),
                StandardHttpStatus.OK
            )
                .mime(format.mime)
                .header("Cache-Control", "private, max-age=0, no-store");
        } catch (AuthenticationException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return ApiResponse.UNAUTHORIZED.response();
        } catch (Throwable t) {
            if (Quark.DEBUG) {
                t.printStackTrace();
            }
            return ApiResponse.INTERNAL_ERROR.response();
        }
    }

}

@RequiredArgsConstructor
class FLVResponseContent implements ResponseContent {
    private static final JsonObject METADATA = new JsonObject()
        .put("mime", "video/x-flv");

    private final StreamFilter filter;
    private final Session qSession;
    private final String fid;

    @Override
    public void write(int recommendedBufferSize, OutputStream out) throws IOException {
        CompletableFuture<Void> waitFor = new CompletableFuture<>();

        SessionListener listener = new FLVSessionListener(this.filter) {
            {
                this.init(out);
            }

            @Override
            public void onClose(Session session) {
                waitFor.complete(null);
            }

            @Override
            public String type() {
                return "HTTP_PLAYBACK";
            }

            @Override
            public String fid() {
                return fid;
            }

            @Override
            public JsonObject metadata() {
                return METADATA;
            }
        };

        try {
            this.qSession.addAsyncListener(listener);
            waitFor.get();
        } catch (InterruptedException | ExecutionException ignored) {
            // NOOP
        } finally {
            this.qSession.removeListener(listener);
        }
    }

    @Override
    public long length() {
        return -1;
    }

    @Override
    public void close() throws IOException {
        // NOOP
    }
}

class RemuxedResponseContent implements ResponseContent {
    private final StreamFilter filter;
    private final Session qSession;
    private final String fid;
    private final String[] command;
    private final JsonObject metadata;

    RemuxedResponseContent(StreamFilter streamSelection, Session qSession, String fid, String mime, String... command) {
        this.filter = streamSelection;
        this.qSession = qSession;
        this.fid = fid;
        this.command = command;
        this.metadata = new JsonObject()
            .put("mime", mime);
    }

    @Override
    public void write(int recommendedBufferSize, OutputStream out) throws IOException {
        CompletableFuture<Void> waitFor = new CompletableFuture<>();

        SessionListener listener = new FLVProcessSessionListener(
            this.filter,
            Redirect.PIPE, Redirect.INHERIT,
            this.command
        ) {

            {
                Thread.ofVirtual().name("FFmpeg -> HTTP", 0)
                    .start(() -> {
                        try {
                            StreamUtil.streamTransfer(this.stdout(), out, recommendedBufferSize);
                        } catch (IOException e) {} finally {
                            waitFor.complete(null);
                        }
                    });
            }

            @Override
            public void onClose(Session session) {
                super.onClose(session);
                waitFor.complete(null);
            }

            @Override
            public String type() {
                return "HTTP_PLAYBACK";
            }

            @Override
            public String fid() {
                return fid;
            }

            @Override
            public JsonObject metadata() {
                return metadata;
            }
        };

        try {
            this.qSession.addAsyncListener(listener);
            waitFor.get();
        } catch (InterruptedException | ExecutionException ignored) {
            // NOOP
        } finally {
            this.qSession.removeListener(listener);
        }
    }

    @Override
    public long length() {
        return -1;
    }

    @Override
    public void close() throws IOException {
        // NOOP
    }
}
