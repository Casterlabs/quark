package co.casterlabs.quark.egress.http;

import java.io.IOException;

import co.casterlabs.quark.Quark;
import co.casterlabs.quark.ingest.protocols.ffmpeg.FFMpegProvider;
import co.casterlabs.quark.session.Session;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rakurai.json.serialization.JsonParseException;
import co.casterlabs.rhs.HttpMethod;
import co.casterlabs.rhs.HttpStatus.StandardHttpStatus;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointData;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointProvider;
import co.casterlabs.rhs.protocol.api.endpoints.HttpEndpoint;
import co.casterlabs.rhs.protocol.http.HttpResponse;
import co.casterlabs.rhs.protocol.http.HttpSession;

public class _RouteStreamIngress implements EndpointProvider {

    @SuppressWarnings("resource")
    @HttpEndpoint(path = "/stream/ingress", allowedMethods = {
            HttpMethod.POST
    })
    public HttpResponse onIngressFile(HttpSession session, EndpointData<Void> data) {
        try {
            IngressFileBody body = Rson.DEFAULT.fromJson(session.body().string(), IngressFileBody.class);

            Session qSession = Quark.session(body.id, true);

            new FFMpegProvider(qSession, body.source);

            return HttpResponse.newFixedLengthResponse(StandardHttpStatus.CREATED, "Created stream: " + body.id);
        } catch (JsonParseException e) {
            return HttpResponse.newFixedLengthResponse(StandardHttpStatus.BAD_REQUEST, "Invalid JSON.");
        } catch (IOException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return HttpResponse.newFixedLengthResponse(StandardHttpStatus.INTERNAL_ERROR, "Could not start source.");
        }
    }

    @JsonClass(exposeAll = true)
    public static class IngressFileBody {
        public final String id = null;
        public final String source = null;
    }

}
