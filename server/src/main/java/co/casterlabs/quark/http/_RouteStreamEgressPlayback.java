package co.casterlabs.quark.http;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import co.casterlabs.commons.io.streams.StreamUtil;
import co.casterlabs.quark.Quark;
import co.casterlabs.quark.Sessions;
import co.casterlabs.quark.auth.AuthenticationException;
import co.casterlabs.quark.auth.User;
import co.casterlabs.quark.session.Session;
import co.casterlabs.quark.session.SessionListener;
import co.casterlabs.quark.session.listeners.FLVProcessSessionListener;
import co.casterlabs.quark.session.listeners.FLVSessionListener;
import co.casterlabs.quark.util.FF;
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
    }, priority = 10, postprocessor = _Processor.class, preprocessor = _Processor.class)
    public HttpResponse onFLVPlayback(HttpSession session, EndpointData<User> data) {
        try {
            data.attachment().checkPlayback(data.uriParameters().get("sessionId"));

            Session qSession = Sessions.getSession(data.uriParameters().get("sessionId"), false);
            if (qSession == null) {
                return ApiResponse.SESSION_NOT_FOUND.response();
            }

            // This one's special!
            return new HttpResponse(
                new FLVResponseContent(qSession, data.attachment().id()),
                StandardHttpStatus.OK
            ).mime("video/x-flv");
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
    }, postprocessor = _Processor.class, preprocessor = _Processor.class)
    public HttpResponse onMuxedPlayback(HttpSession session, EndpointData<User> data) {
        try {
            data.attachment().checkPlayback(data.uriParameters().get("sessionId"));

            Session qSession = Sessions.getSession(data.uriParameters().get("sessionId"), false);
            if (qSession == null) {
                return ApiResponse.SESSION_NOT_FOUND.response();
            }

            MuxFormat format = MUX_FORMATS.get(data.uriParameters().get("format"));
            if (format == null) {
                return ApiResponse.BAD_REQUEST.response();
            }

            if (!FF.canUseMpeg) {
                return ApiResponse.NOT_ENABLED.response();
            }

            return new HttpResponse(
                new RemuxedResponseContent(qSession, data.attachment().id(), format.mime, format.command),
                StandardHttpStatus.OK
            ).mime(format.mime);
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

    private final Session qSession;
    private final String fid;

    @Override
    public void write(int recommendedBufferSize, OutputStream out) throws IOException {
        CompletableFuture<Void> waitFor = new CompletableFuture<>();

        SessionListener listener = new FLVSessionListener() {
            {
                this.init(out);
            }

            @Override
            public void onClose(Session session) {
                waitFor.complete(null);
            }

            @Override
            public Type type() {
                return Type.HTTP_PLAYBACK;
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
    private final Session qSession;
    private final String fid;
    private final String[] command;
    private final JsonObject metadata;

    RemuxedResponseContent(Session qSession, String fid, String mime, String... command) {
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
            public Type type() {
                return Type.HTTP_PLAYBACK;
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
