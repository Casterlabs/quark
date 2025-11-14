package co.casterlabs.quark.protocol.webrtc.routes;

import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.Sessions;
import co.casterlabs.quark.core.extensibility.QuarkHttpEndpoint;
import co.casterlabs.quark.core.http.ApiResponse;
import co.casterlabs.quark.core.http.QuarkHttpProcessor;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.quark.core.util.FF;
import co.casterlabs.quark.protocol.webrtc.WebRTCEnv;
import co.casterlabs.quark.protocol.webrtc.ingress.WebRTCProvider;
import co.casterlabs.rakurai.json.element.JsonObject;
import co.casterlabs.rhs.HttpMethod;
import co.casterlabs.rhs.HttpStatus.StandardHttpStatus;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointData;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointProvider;
import co.casterlabs.rhs.protocol.api.endpoints.HttpEndpoint;
import co.casterlabs.rhs.protocol.http.HttpResponse;
import co.casterlabs.rhs.protocol.http.HttpSession;

// https://www.ietf.org/archive/id/draft-ietf-wish-whip-01.html
@QuarkHttpEndpoint
public class RouteStreamIngressWHIP implements EndpointProvider {

    @HttpEndpoint(path = "/session/ingress/whip", allowedMethods = {
            HttpMethod.POST
    }, postprocessor = QuarkHttpProcessor.class)
    public HttpResponse onIngressStartWHIPBlankApp(HttpSession session, EndpointData<Void> data) {
        return this.onIngressStartWHIP(session, data);
    }

    @HttpEndpoint(path = "/session/ingress/whip/:app", allowedMethods = {
            HttpMethod.POST
    }, postprocessor = QuarkHttpProcessor.class)
    public HttpResponse onIngressStartWHIP(HttpSession session, EndpointData<Void> data) {
        if (!WebRTCEnv.EXP_WHIP || !FF.canUseMpeg) {
            return ApiResponse.NOT_ENABLED.response();
        }

        try {
            String url = session.headers().getSingle("Host").raw();
            String auth = session.headers().getSingle("Authorization").raw().substring("Bearer ".length());
            String app = data.uriParameters().getOrDefault("app", "");
            String sdpOffer = session.body().string();

            WebRTCProvider provider = new WebRTCProvider(sdpOffer);
            Session qSession = Sessions.authenticateSession(provider, session.remoteNetworkAddress(), url, app, auth);

            provider.init(qSession);

            JsonObject answer = provider.sdpAnswer.get();

            return HttpResponse.newFixedLengthResponse(StandardHttpStatus.CREATED, answer.getString("sdp"))
                .mime("application/sdp")
                .header("Location", "/session/ingress/whip/resource/" + provider.resourceId);
        } catch (Throwable t) {
            if (Quark.DEBUG) {
                t.printStackTrace();
            }
            return ApiResponse.INTERNAL_ERROR.response();
        }
    }

    @HttpEndpoint(path = "/session/ingress/whip/resource/:resourceId", allowedMethods = {
            HttpMethod.DELETE
    }, postprocessor = QuarkHttpProcessor.class)
    public HttpResponse onTerminateWHIP(HttpSession session, EndpointData<Void> data) {
        if (!WebRTCEnv.EXP_WHIP || !FF.canUseMpeg) {
            return ApiResponse.NOT_ENABLED.response();
        }

        try {
            WebRTCProvider provider = WebRTCProvider.providers.get(data.uriParameters().get("resourceId"));

            if (provider != null) {
                provider.close(true);
            }

            return ApiResponse.success(StandardHttpStatus.OK);
        } catch (Throwable t) {
            if (Quark.DEBUG) {
                t.printStackTrace();
            }
            return ApiResponse.INTERNAL_ERROR.response();
        }
    }

    @HttpEndpoint(path = "/session/ingress/whip/resource/:resourceId", allowedMethods = {
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
