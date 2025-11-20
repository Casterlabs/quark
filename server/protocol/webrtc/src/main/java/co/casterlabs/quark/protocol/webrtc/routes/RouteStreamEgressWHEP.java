package co.casterlabs.quark.protocol.webrtc.routes;

import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.auth.User;
import co.casterlabs.quark.core.extensibility.QuarkHttpEndpoint;
import co.casterlabs.quark.core.http.ApiResponse;
import co.casterlabs.quark.core.http.QuarkHttpProcessor;
import co.casterlabs.quark.core.http.QuarkHttpProcessor.EndpointContext;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.quark.core.session.listeners.StreamFilter;
import co.casterlabs.quark.core.util.FF;
import co.casterlabs.quark.core.util.PrivatePortRange;
import co.casterlabs.quark.core.util.PublicPortRange;
import co.casterlabs.quark.protocol.webrtc.WebRTCEnv;
import co.casterlabs.quark.protocol.webrtc.egress.WebRTCSessionListener;
import co.casterlabs.rakurai.json.element.JsonObject;
import co.casterlabs.rhs.HttpMethod;
import co.casterlabs.rhs.HttpStatus.StandardHttpStatus;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointData;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointProvider;
import co.casterlabs.rhs.protocol.api.endpoints.HttpEndpoint;
import co.casterlabs.rhs.protocol.http.HttpResponse;
import co.casterlabs.rhs.protocol.http.HttpSession;

// https://www.ietf.org/archive/id/draft-murillo-whep-03.html
@QuarkHttpEndpoint
public class RouteStreamEgressWHEP implements EndpointProvider {

    @HttpEndpoint(path = "/session/:sessionId/egress/playback/whep", allowedMethods = {
            HttpMethod.POST
    }, postprocessor = QuarkHttpProcessor.class, preprocessor = QuarkHttpProcessor.class)
    public HttpResponse onEgressStartWHEP(HttpSession session, EndpointData<EndpointContext> data) {
        if (!WebRTCEnv.EXP_WHIP || !FF.canUseMpeg) {
            return ApiResponse.NOT_ENABLED.response();
        }

        User user = data.attachment().user();
        Session qSession = data.attachment().session();

        try {
            user.checkPlayback(qSession.id);

            StreamFilter filter = StreamFilter.from(session.uri().query);

            String sdpOffer = session.body().string();
            WebRTCSessionListener listener = new WebRTCSessionListener(
                qSession, sdpOffer,
                new int[] {
                        PublicPortRange.acquirePort(),
                        PrivatePortRange.acquirePort(),
                        PrivatePortRange.acquirePort()
                },
                filter
            );

            JsonObject answer = listener.sdpAnswer.get();
            qSession.addAsyncListener(listener);

            return HttpResponse.newFixedLengthResponse(StandardHttpStatus.CREATED, answer.getString("sdp"))
                .mime("application/sdp")
                .header("Location", "/resource/whep/" + listener.resourceId);
        } catch (Throwable t) {
            if (Quark.DEBUG) {
                t.printStackTrace();
            }
            return ApiResponse.INTERNAL_ERROR.response();
        }
    }

    @HttpEndpoint(path = "/resource/whep/:resourceId", allowedMethods = {
            HttpMethod.DELETE
    }, postprocessor = QuarkHttpProcessor.class)
    public HttpResponse onTerminateWHEP(HttpSession session, EndpointData<Void> data) {
        if (!WebRTCEnv.EXP_WHIP || !FF.canUseMpeg) {
            return ApiResponse.NOT_ENABLED.response();
        }

        try {
            WebRTCSessionListener instance = WebRTCSessionListener.listeners.remove(data.uriParameters().get("resourceId"));

            if (instance != null) {
                instance.close();
            }

            return ApiResponse.success(StandardHttpStatus.OK);
        } catch (Throwable t) {
            if (Quark.DEBUG) {
                t.printStackTrace();
            }
            return ApiResponse.INTERNAL_ERROR.response();
        }
    }

    @HttpEndpoint(path = "/resource/whep/:resourceId", allowedMethods = {
            HttpMethod.PATCH, // We don't support trickle ICE yet, so by spec we should 405.

            // The rest of these are reserved for future use.
            HttpMethod.GET,
            HttpMethod.HEAD,
            HttpMethod.POST,
            HttpMethod.PUT
    }, postprocessor = QuarkHttpProcessor.class)
    public HttpResponse onInvalidResourceMethod(HttpSession session, EndpointData<Void> data) {
        return HttpResponse.newFixedLengthResponse(StandardHttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed");
    }

}
