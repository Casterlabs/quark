package co.casterlabs.quark.egress.http;

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

    @HttpEndpoint(path = "/session/:sessionId/egress/rtmp", allowedMethods = {
            HttpMethod.POST
    })
    public HttpResponse onMuxedPlayback(HttpSession session, EndpointData<Void> data) {
        try {
            Session qSession = Quark.session(data.uriParameters().get("sessionId"), false);
            if (qSession == null) {
                return ApiResponse.SESSION_NOT_FOUND.response();
            }

            EgressRTMPBody body = Rson.DEFAULT.fromJson(session.body().string(), EgressRTMPBody.class);

            qSession.addAsyncListener(new FFmpegRTMPSessionListener(body.url));

            return ApiResponse.success(StandardHttpStatus.CREATED);
        } catch (JsonParseException e) {
            return ApiResponse.BAD_REQUEST.response();
        } catch (Throwable t) {
            if (Quark.DEBUG) {
                t.printStackTrace();
            }
            return ApiResponse.INTERNAL_ERROR.response();
        }
    }

    @JsonClass(exposeAll = true)
    public static class EgressRTMPBody {
        public final String url = null;
    }

}
