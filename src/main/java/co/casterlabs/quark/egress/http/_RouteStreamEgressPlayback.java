package co.casterlabs.quark.egress.http;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import co.casterlabs.quark.Quark;
import co.casterlabs.quark.session.QuarkSession;
import co.casterlabs.quark.session.QuarkSessionListener;
import co.casterlabs.quark.session.listeners.FLVMuxedSessionListener;
import co.casterlabs.rhs.HttpMethod;
import co.casterlabs.rhs.HttpStatus.StandardHttpStatus;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointData;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointProvider;
import co.casterlabs.rhs.protocol.api.endpoints.HttpEndpoint;
import co.casterlabs.rhs.protocol.http.HttpResponse;
import co.casterlabs.rhs.protocol.http.HttpResponse.ResponseContent;
import co.casterlabs.rhs.protocol.http.HttpSession;

public class _RouteStreamEgressPlayback implements EndpointProvider {
    private static final Map<String, MuxFormat> MUX_FORMATS = Map.of(
        // FLV is special since we use it internally :^)
        "mp3", new MuxFormat(
            "audio/mpeg",
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
        "ts", new MuxFormat(
            "video/mp2t",
            "ffmpeg",
            "-hide_banner",
            "-loglevel", "quiet",
            "-i", "-",
            "-c", "copy",
            "-f", "mpegts",
            "-"
        ),
        "mp4", new MuxFormat(
            "video/mp4",
            "ffmpeg",
            "-hide_banner",
            "-loglevel", "quiet",
            "-i", "-",
            "-c", "copy",
            "-bsf:a", "aac_adtstoasc",
            "-bsf:v", "h264_mp4toannexb",
            "-movflags", "+faststart+empty_moov",
            "-f", "mp4",
            "-"
        )
    );

    @HttpEndpoint(path = "/stream/:streamId/egress/playback/flv", allowedMethods = {
            HttpMethod.GET
    })
    public HttpResponse onFLVPlayback(HttpSession session, EndpointData<Void> data) {
        QuarkSession qSession = Quark.session(data.uriParameters().get("streamId"), false);
        if (qSession == null) {
            return HttpResponse.newFixedLengthResponse(StandardHttpStatus.NOT_FOUND, "Stream not found.");
        }

        return new HttpResponse(
            new ResponseContent() {
                @Override
                public void write(int recommendedBufferSize, OutputStream out) throws IOException {
                    CompletableFuture<Void> waitFor = new CompletableFuture<>();

                    QuarkSessionListener listener = new FLVMuxedSessionListener() {
                        {
                            this.init(out);
                        }

                        @Override
                        public void onClose(QuarkSession session) {
                            waitFor.complete(null);
                        }
                    };

                    try {
                        qSession.addListener(listener);
                        waitFor.get();
                    } catch (InterruptedException | ExecutionException ignored) {
                        // NOOP
                    } finally {
                        qSession.removeListener(listener);
                    }
                }

                @Override
                public long length() {
                    return -1; // Chunked mode.
                }

                @Override
                public void close() throws IOException {
                    // NOOP
                }
            },
            StandardHttpStatus.OK
        ).mime("video/x-flv");
    }

    @HttpEndpoint(path = "/stream/:streamId/egress/playback/:format", allowedMethods = {
            HttpMethod.GET
    })
    public HttpResponse onMuxedPlayback(HttpSession session, EndpointData<Void> data) {
        QuarkSession qSession = Quark.session(data.uriParameters().get("streamId"), false);
        if (qSession == null) {
            return HttpResponse.newFixedLengthResponse(StandardHttpStatus.NOT_FOUND, "Stream not found.");
        }

        MuxFormat format = MUX_FORMATS.get(data.uriParameters().get("format"));
        if (format == null) {
            return HttpResponse.newFixedLengthResponse(StandardHttpStatus.BAD_REQUEST, "Invalid format.");
        }

        return new HttpResponse(
            new _RemuxedResponseContent(
                qSession,
                format.command
            ),
            StandardHttpStatus.OK
        ).mime(format.mime);
    }

    private static record MuxFormat(String mime, String... command) {
    }

}
