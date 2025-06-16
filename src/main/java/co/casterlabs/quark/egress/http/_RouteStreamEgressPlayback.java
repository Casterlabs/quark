package co.casterlabs.quark.egress.http;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import co.casterlabs.commons.io.streams.StreamUtil;
import co.casterlabs.quark.Quark;
import co.casterlabs.quark.session.Session;
import co.casterlabs.quark.session.SessionListener;
import co.casterlabs.quark.session.listeners.FLVMuxedSessionListener;
import co.casterlabs.quark.session.listeners.FLVProcessSessionListener;
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
        // Also includes FLV, since we use that internally!
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
            // NB: this doesn't trick Firefox nor Safari into playing non-standard codecs,
            // but it does trick Chrome into doing so, and it works surprisingly well!
            /*mime*/"video/x-matroska",
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

    @HttpEndpoint(path = "/stream/:streamId/egress/playback/:format", allowedMethods = {
            HttpMethod.GET
    })
    public HttpResponse onMuxedPlayback(HttpSession session, EndpointData<Void> data) {
        Session qSession = Quark.session(data.uriParameters().get("streamId"), false);
        if (qSession == null) {
            return HttpResponse.newFixedLengthResponse(StandardHttpStatus.NOT_FOUND, "Stream not found.");
        }

        String formatStr = data.uriParameters().get("format").toLowerCase();
        if (formatStr.equals("flv")) {
            // This one's special!
            return new HttpResponse(
                new FLVResponseContent(qSession),
                StandardHttpStatus.OK
            ).mime("video/x-flv");
        }

        MuxFormat format = MUX_FORMATS.get(formatStr);
        if (format == null) {
            List<String> valid = new ArrayList<>(MUX_FORMATS.keySet());
            valid.add("flv");
            return HttpResponse.newFixedLengthResponse(StandardHttpStatus.BAD_REQUEST, "Invalid format. Valid: " + String.join(", ", valid));
        }

        return new HttpResponse(
            new RemuxedResponseContent(qSession, format.command),
            StandardHttpStatus.OK
        ).mime(format.mime);
    }

}

@RequiredArgsConstructor
class FLVResponseContent implements ResponseContent {
    private final Session qSession;

    @Override
    public void write(int recommendedBufferSize, OutputStream out) throws IOException {
        CompletableFuture<Void> waitFor = new CompletableFuture<>();

        SessionListener listener = new FLVMuxedSessionListener() {
            {
                this.init(out);
            }

            @Override
            public void onClose(Session session) {
                waitFor.complete(null);
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
        return Long.MAX_VALUE; // infinite length. causes browsers to never seek, more efficient than chunked.
    }

    @Override
    public void close() throws IOException {
        // NOOP
    }
}

class RemuxedResponseContent implements ResponseContent {
    private final Session qSession;
    private final String[] command;

    RemuxedResponseContent(Session qSession, String... command) {
        this.qSession = qSession;
        this.command = command;
    }

    @Override
    public void write(int recommendedBufferSize, OutputStream out) throws IOException {
        CompletableFuture<Void> waitFor = new CompletableFuture<>();

        SessionListener listener = new FLVProcessSessionListener(
            Redirect.PIPE, Redirect.INHERIT,
            this.command
        ) {

            {
                Thread.ofVirtual().name("FFMpeg -> HTTP", 0)
                    .start(() -> {
                        try {
                            StreamUtil.streamTransfer(this.stdout(), out, 8192);
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
        return Long.MAX_VALUE; // infinite length. causes browsers to never seek, more efficient than chunked.
    }

    @Override
    public void close() throws IOException {
        // NOOP
    }
}
