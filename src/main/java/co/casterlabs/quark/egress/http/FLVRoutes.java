package co.casterlabs.quark.egress.http;

import java.io.IOException;
import java.io.OutputStream;
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

public class FLVRoutes implements EndpointProvider {

    @HttpEndpoint(path = "/flv/:streamId", allowedMethods = {
            HttpMethod.GET
    })
    public HttpResponse onWatch(HttpSession session, EndpointData<Void> data) {
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

}
