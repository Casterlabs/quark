package co.casterlabs.quark.http;

import co.casterlabs.quark.Quark;
import co.casterlabs.quark.Sessions;
import co.casterlabs.quark.auth.AuthenticationException;
import co.casterlabs.quark.auth.User;
import co.casterlabs.quark.session.Session;
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
    }, postprocessor = _Processor.class, preprocessor = _Processor.class)
    public HttpResponse onEgressList(HttpSession session, EndpointData<User> data) {
        try {
            data.attachment().checkAdmin();

            Session qSession = Sessions.getSession(data.uriParameters().get("sessionId"), false);
            if (qSession == null) {
                return ApiResponse.SESSION_NOT_FOUND.response();
            }

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

    @HttpEndpoint(path = "/session/:sessionId/egress/:id", allowedMethods = {
            HttpMethod.DELETE
    }, postprocessor = _Processor.class, preprocessor = _Processor.class)
    public HttpResponse onEgressDelete(HttpSession session, EndpointData<User> data) {
        try {
            data.attachment().checkAdmin();

            Session qSession = Sessions.getSession(data.uriParameters().get("sessionId"), false);
            if (qSession == null) {
                return ApiResponse.SESSION_NOT_FOUND.response();
            }

            String id = data.uriParameters().get("id");
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
    }, postprocessor = _Processor.class, preprocessor = _Processor.class)
    public HttpResponse onEgressDeleteByFid(HttpSession session, EndpointData<User> data) {
        try {
            data.attachment().checkAdmin();

            Session qSession = Sessions.getSession(data.uriParameters().get("sessionId"), false);
            if (qSession == null) {
                return ApiResponse.SESSION_NOT_FOUND.response();
            }

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
    }, postprocessor = _Processor.class, preprocessor = _Processor.class)
    public HttpResponse onEgressThumbnail(HttpSession session, EndpointData<User> data) {
        try {
            data.attachment().checkPlayback(data.uriParameters().get("sessionId"));

            Session qSession = Sessions.getSession(data.uriParameters().get("sessionId"), false);
            if (qSession == null) {
                return ApiResponse.SESSION_NOT_FOUND.response();
            }

            return HttpResponse.newFixedLengthResponse(StandardHttpStatus.OK, qSession.thumbnail())
                .mime("image/jpeg");
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
