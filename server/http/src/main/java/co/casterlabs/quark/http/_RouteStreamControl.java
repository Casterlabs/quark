package co.casterlabs.quark.http;

import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.Sessions;
import co.casterlabs.quark.core.auth.AuthenticationException;
import co.casterlabs.quark.core.auth.User;
import co.casterlabs.quark.core.http.ApiResponse;
import co.casterlabs.quark.core.http.QuarkHttpProcessor;
import co.casterlabs.quark.core.http.QuarkHttpProcessor.EndpointContext;
import co.casterlabs.quark.core.session.Session;
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
    }, postprocessor = QuarkHttpProcessor.class, preprocessor = QuarkHttpProcessor.class)
    public HttpResponse onGetSessions(HttpSession session, EndpointData<EndpointContext> data) {
        User user = data.attachment().user();

        try {
            user.checkAdmin();

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
    }, postprocessor = QuarkHttpProcessor.class, preprocessor = QuarkHttpProcessor.class)
    public HttpResponse onGetSessionInfo(HttpSession session, EndpointData<EndpointContext> data) {
        User user = data.attachment().user();
        Session qSession = data.attachment().session();

        try {
            // This info is public once you start playback,
            // so we check for that instead of admin :^)
            user.checkPlayback(qSession.id);

            JsonObject json = new JsonObject()
                .put("id", qSession.id)
                .put("createdAt", qSession.createdAt)
                .put("info", Rson.DEFAULT.toJson(qSession.info));

            if (user.isAdmin()) {
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
    }, postprocessor = QuarkHttpProcessor.class, preprocessor = QuarkHttpProcessor.class)
    public HttpResponse onEndSession(HttpSession session, EndpointData<EndpointContext> data) {
        User user = data.attachment().user();
        Session qSession = data.attachment().session();

        try {
            user.checkAdmin();

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
