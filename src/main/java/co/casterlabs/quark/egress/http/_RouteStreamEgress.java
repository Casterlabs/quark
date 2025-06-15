package co.casterlabs.quark.egress.http;

import java.io.IOException;

import co.casterlabs.quark.Quark;
import co.casterlabs.quark.session.Session;
import co.casterlabs.quark.session.listeners.FFmpegRTMPSessionListener;
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

public class _RouteStreamEgress implements EndpointProvider {

    @HttpEndpoint(path = "/stream/:streamId/egress/rtmp", allowedMethods = {
            HttpMethod.POST
    })
    public HttpResponse onMuxedPlayback(HttpSession session, EndpointData<Void> data) {
        try {
            Session qSession = Quark.session(data.uriParameters().get("streamId"), false);
            if (qSession == null) {
                return HttpResponse.newFixedLengthResponse(StandardHttpStatus.NOT_FOUND, "Stream not found.");
            }

            EgressRTMPBody body = Rson.DEFAULT.fromJson(session.body().string(), EgressRTMPBody.class);

            qSession.addAsyncListener(new FFmpegRTMPSessionListener(body.url));

            return HttpResponse.newFixedLengthResponse(StandardHttpStatus.CREATED, "Created RTMP egress.");
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
    public static class EgressRTMPBody {
        public final String url = null;
    }

}
