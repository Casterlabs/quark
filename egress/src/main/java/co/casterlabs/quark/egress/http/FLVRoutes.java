package co.casterlabs.quark.egress.http;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import co.casterlabs.flv4j.flv.FLVFileHeader;
import co.casterlabs.flv4j.flv.muxing.StreamFLVMuxer;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.QuarkSession;
import co.casterlabs.quark.core.QuarkSession.SessionListener;
import co.casterlabs.quark.core.util.FLVSequenceTag;
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
        QuarkSession qSession = Quark.session(streamId, true);

        return new HttpResponse(new ResponseContent() {
            @Override
            public void write(int recommendedBufferSize, OutputStream out) throws IOException {
                StreamFLVMuxer muxer = new StreamFLVMuxer(
                    new FLVFileHeader(1, 0x4 & 0x1, new byte[0]),
                    out
                );

                CompletableFuture<Void> waitFor = new CompletableFuture<>();

                SessionListener listener = new SessionListener() {
                    private boolean hasGottenSequence = false;

                    @Override
                    public void onPacket(QuarkSession session, Object data) {
                        if (data instanceof FLVSequenceTag seq) {
                            data = seq.tag(); // Below...
                            this.hasGottenSequence = true;
                            System.out.println(data);
                        }

                        if (data instanceof FLVTag tag) {
                            if (!this.hasGottenSequence) {
                                return;
                            }

                            try {
                                muxer.write(tag);
                            } catch (IOException e) {
                                waitFor.completeExceptionally(e);
                            }
                        }
                    }

                    @Override
                    public void onClose(QuarkSession session) {
                        waitFor.complete(null);
                    }
                };

                try {
                    qSession.listeners.add(listener);
                    qSession.sequenceRequest();
                    waitFor.get();
                } catch (InterruptedException | ExecutionException ignored) {} finally {
                    qSession.listeners.remove(listener);
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
        }, StandardHttpStatus.OK);
    }

}
