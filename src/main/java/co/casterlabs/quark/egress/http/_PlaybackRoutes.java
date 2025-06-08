package co.casterlabs.quark.egress.http;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import co.casterlabs.commons.io.streams.StreamUtil;
import co.casterlabs.quark.Quark;
import co.casterlabs.quark.session.QuarkSession;
import co.casterlabs.quark.session.QuarkSessionListener;
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

public class _PlaybackRoutes implements EndpointProvider {

    @HttpEndpoint(path = "/playback/flv/:streamId", allowedMethods = {
            HttpMethod.GET
    })
    public HttpResponse onPlaybackFLV(HttpSession session, EndpointData<Void> data) {
        String streamId = data.uriParameters().get("streamId");
        QuarkSession qSession = Quark.session(streamId);

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

    @HttpEndpoint(path = "/playback/ts/:streamId", allowedMethods = {
            HttpMethod.GET
    })
    public HttpResponse onPlaybackTS(HttpSession session, EndpointData<Void> data) {
        String streamId = data.uriParameters().get("streamId");
        QuarkSession qSession = Quark.session(streamId);

        return new HttpResponse(
            new RemuxedResponseContent(
                qSession,
                "ffmpeg",
                "-hide_banner",
                "-loglevel", "quiet",
                "-i", "-",
                "-c", "copy",
                "-f", "mpegts",
                "-"
            ),
            StandardHttpStatus.OK
        ).mime("video/mp2t");
    }

    @HttpEndpoint(path = "/playback/mp3/:streamId", allowedMethods = {
            HttpMethod.GET
    })
    public HttpResponse onPlaybackMP3(HttpSession session, EndpointData<Void> data) {
        String streamId = data.uriParameters().get("streamId");
        QuarkSession qSession = Quark.session(streamId);

        return new HttpResponse(
            new RemuxedResponseContent(
                qSession,
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
            StandardHttpStatus.OK
        ).mime("audio/mpeg");
    }

    @HttpEndpoint(path = "/playback/mp4/:streamId", allowedMethods = {
            HttpMethod.GET
    })
    public HttpResponse onPlaybackMP4(HttpSession session, EndpointData<Void> data) {
        String streamId = data.uriParameters().get("streamId");
        QuarkSession qSession = Quark.session(streamId);

        return new HttpResponse(
            new RemuxedResponseContent(
                qSession,
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
            ),
            StandardHttpStatus.OK
        ).mime("video/mp4");
    }

}

class RemuxedResponseContent implements ResponseContent {
    private final QuarkSession qSession;
    private final String[] command;

    RemuxedResponseContent(QuarkSession qSession, String... command) {
        this.qSession = qSession;
        this.command = command;
    }

    @Override
    public void write(int recommendedBufferSize, OutputStream out) throws IOException {
        CompletableFuture<Void> waitFor = new CompletableFuture<>();

        QuarkSessionListener listener = new FLVProcessSessionListener(
            qSession, Redirect.PIPE, Redirect.INHERIT,
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
            public void onClose(QuarkSession session) {
                super.onClose(session);
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
}
