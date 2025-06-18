package co.casterlabs.quark.egress.http;

import co.casterlabs.quark.Quark;
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
    })
    public HttpResponse onGetSessions(HttpSession session, EndpointData<Void> data) {
        try {
            JsonArray ids = new JsonArray();
            Quark.forEachSession((s) -> ids.add(s.id));

            return ApiResponse.success(StandardHttpStatus.OK, ids);
        } catch (Throwable t) {
            if (Quark.DEBUG) {
                t.printStackTrace();
            }
            return ApiResponse.INTERNAL_ERROR.response();
        }
    }

    @HttpEndpoint(path = "/session/:sessionId", allowedMethods = {
            HttpMethod.GET
    })
    public HttpResponse onGetSessionInfo(HttpSession session, EndpointData<Void> data) {
        try {
            Session qSession = Quark.session(data.uriParameters().get("sessionId"), false);
            if (qSession == null) {
                return ApiResponse.SESSION_NOT_FOUND.response();
            }

            JsonObject json = new JsonObject()
                .put("id", qSession.id)
                .put("info", Rson.DEFAULT.toJson(qSession.info));

            return ApiResponse.success(StandardHttpStatus.OK, json);
        } catch (Throwable t) {
            if (Quark.DEBUG) {
                t.printStackTrace();
            }
            return ApiResponse.INTERNAL_ERROR.response();
        }
    }

    @HttpEndpoint(path = "/session/:sessionId", allowedMethods = {
            HttpMethod.DELETE
    })
    public HttpResponse onEndSession(HttpSession session, EndpointData<Void> data) {
        try {
            Session qSession = Quark.session(data.uriParameters().get("sessionId"), false);
            if (qSession == null) {
                return ApiResponse.SESSION_NOT_FOUND.response();
            }

            qSession.close();

            return ApiResponse.success(StandardHttpStatus.OK);
        } catch (Throwable t) {
            if (Quark.DEBUG) {
                t.printStackTrace();
            }
            return ApiResponse.INTERNAL_ERROR.response();
        }
    }

}
