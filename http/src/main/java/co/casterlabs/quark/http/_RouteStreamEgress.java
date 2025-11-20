package co.casterlabs.quark.http;

import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.auth.AuthenticationException;
import co.casterlabs.quark.core.auth.User;
import co.casterlabs.quark.core.http.ApiResponse;
import co.casterlabs.quark.core.http.QuarkHttpProcessor;
import co.casterlabs.quark.core.http.QuarkHttpProcessor.EndpointContext;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.rhs.HttpMethod;
import co.casterlabs.rhs.HttpStatus.StandardHttpStatus;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointData;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointProvider;
import co.casterlabs.rhs.protocol.api.endpoints.HttpEndpoint;
import co.casterlabs.rhs.protocol.http.HttpResponse;
import co.casterlabs.rhs.protocol.http.HttpSession;

public class _RouteStreamEgress implements EndpointProvider {

    @HttpEndpoint(path = "/session/:sessionId/egress", allowedMethods = {
            HttpMethod.GET
    }, postprocessor = QuarkHttpProcessor.class, preprocessor = QuarkHttpProcessor.class)
    public HttpResponse onEgressList(HttpSession session, EndpointData<EndpointContext> data) {
        User user = data.attachment().user();
        Session qSession = data.attachment().session();

        try {
            user.checkAdmin();

            return ApiResponse.success(StandardHttpStatus.OK, qSession.listeners());
        } catch (AuthenticationException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return ApiResponse.UNAUTHORIZED.response();
        } catch (Throwable t) {
            if (Quark.DEBUG) {
                t.printStackTrace();
            }
            return ApiResponse.INTERNAL_ERROR.response();
        }
    }

    @HttpEndpoint(path = "/session/:sessionId/egress/:egressId", allowedMethods = {
            HttpMethod.DELETE
    }, postprocessor = QuarkHttpProcessor.class, preprocessor = QuarkHttpProcessor.class)
    public HttpResponse onEgressDelete(HttpSession session, EndpointData<EndpointContext> data) {
        User user = data.attachment().user();
        Session qSession = data.attachment().session();

        try {
            user.checkAdmin();

            String id = data.uriParameters().get("egressId");
            qSession.removeById(id);

            return ApiResponse.success(StandardHttpStatus.OK);
        } catch (AuthenticationException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return ApiResponse.UNAUTHORIZED.response();
        } catch (Throwable t) {
            if (Quark.DEBUG) {
                t.printStackTrace();
            }
            return ApiResponse.INTERNAL_ERROR.response();
        }
    }

    @HttpEndpoint(path = "/session/:sessionId/egress/:fid/fid", allowedMethods = {
            HttpMethod.DELETE
    }, postprocessor = QuarkHttpProcessor.class, preprocessor = QuarkHttpProcessor.class)
    public HttpResponse onEgressDeleteByFid(HttpSession session, EndpointData<EndpointContext> data) {
        User user = data.attachment().user();
        Session qSession = data.attachment().session();

        try {
            user.checkAdmin();

            String fid = data.uriParameters().get("fid");
            qSession.removeByFid(fid);

            return ApiResponse.success(StandardHttpStatus.OK);
        } catch (AuthenticationException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return ApiResponse.UNAUTHORIZED.response();
        } catch (Throwable t) {
            if (Quark.DEBUG) {
                t.printStackTrace();
            }
            return ApiResponse.INTERNAL_ERROR.response();
        }
    }

    @HttpEndpoint(path = "/session/:sessionId/egress/thumbnail", allowedMethods = {
            HttpMethod.GET
    }, postprocessor = QuarkHttpProcessor.class, preprocessor = QuarkHttpProcessor.class)
    public HttpResponse onEgressThumbnail(HttpSession session, EndpointData<EndpointContext> data) {
        User user = data.attachment().user();
        Session qSession = data.attachment().session();

        try {
            user.checkPlayback(qSession.id);

            return HttpResponse.newFixedLengthResponse(StandardHttpStatus.OK, qSession.thumbnail())
                .mime("image/jpeg")
                .header("Cache-Control", "public, max-age=" + Quark.THUMBNAIL_INTERVAL);
        } catch (AuthenticationException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return ApiResponse.UNAUTHORIZED.response();
        } catch (Throwable t) {
            if (Quark.DEBUG) {
                t.printStackTrace();
            }
            return ApiResponse.INTERNAL_ERROR.response();
        }
    }

}
