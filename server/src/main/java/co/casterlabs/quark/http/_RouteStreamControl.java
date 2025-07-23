package co.casterlabs.quark.http;

import co.casterlabs.quark.Quark;
import co.casterlabs.quark.Sessions;
import co.casterlabs.quark.auth.AuthenticationException;
import co.casterlabs.quark.auth.User;
import co.casterlabs.quark.session.Session;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonObject;
import co.casterlabs.rhs.HttpMethod;
import co.casterlabs.rhs.HttpStatus.StandardHttpStatus;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointData;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointProvider;
import co.casterlabs.rhs.protocol.api.endpoints.HttpEndpoint;
import co.casterlabs.rhs.protocol.http.HttpResponse;
import co.casterlabs.rhs.protocol.http.HttpSession;

public class _RouteStreamControl implements EndpointProvider {

    @HttpEndpoint(path = "/sessions", allowedMethods = {
            HttpMethod.GET
    }, postprocessor = _Processor.class, preprocessor = _Processor.class)
    public HttpResponse onGetSessions(HttpSession session, EndpointData<User> data) {
        try {
            data.attachment().checkAdmin();

            JsonArray ids = new JsonArray();
            Sessions.forEachSession((s) -> ids.add(s.id));

            return ApiResponse.success(StandardHttpStatus.OK, ids);
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

    @HttpEndpoint(path = "/session/:sessionId", allowedMethods = {
            HttpMethod.GET
    }, postprocessor = _Processor.class, preprocessor = _Processor.class)
    public HttpResponse onGetSessionInfo(HttpSession session, EndpointData<User> data) {
        try {
            // This info is public once you start playback,
            // so we check for that instead of admin :^)
            data.attachment().checkPlayback(data.uriParameters().get("sessionId"));

            Session qSession = Sessions.getSession(data.uriParameters().get("sessionId"), false);
            if (qSession == null) {
                return ApiResponse.SESSION_NOT_FOUND.response();
            }

            JsonObject json = new JsonObject()
                .put("id", qSession.id)
                .put("createdAt", qSession.createdAt)
                .put("info", Rson.DEFAULT.toJson(qSession.info));

            if (data.attachment().isAdmin()) {
                // Only admins should see this data, otherwise we'd leak the stream key to
                // _anyone_ who can do playback.
                json.put("metadata", qSession.metadata());
            } else {
                json.putNull("metadata");
            }

            return ApiResponse.success(StandardHttpStatus.OK, json);
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

    @HttpEndpoint(path = "/session/:sessionId", allowedMethods = {
            HttpMethod.DELETE
    }, postprocessor = _Processor.class, preprocessor = _Processor.class)
    public HttpResponse onEndSession(HttpSession session, EndpointData<User> data) {
        try {
            data.attachment().checkAdmin();

            Session qSession = Sessions.getSession(data.uriParameters().get("sessionId"), false);
            if (qSession == null) {
                return ApiResponse.SESSION_NOT_FOUND.response();
            }

            qSession.close(true);

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

}
