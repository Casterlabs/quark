package co.casterlabs.quark.http;

import co.casterlabs.quark.Quark;
import co.casterlabs.quark.Sessions;
import co.casterlabs.quark.auth.AuthenticationException;
import co.casterlabs.quark.auth.User;
import co.casterlabs.quark.egress.config.PipelineEgressConfiguration;
import co.casterlabs.quark.egress.config.RTMPEgressConfiguration;
import co.casterlabs.quark.session.Session;
import co.casterlabs.quark.session.listeners.StreamFilter;
import co.casterlabs.quark.util.DependencyException;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.serialization.JsonParseException;
import co.casterlabs.rhs.HttpMethod;
import co.casterlabs.rhs.HttpStatus.StandardHttpStatus;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointData;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointProvider;
import co.casterlabs.rhs.protocol.api.endpoints.HttpEndpoint;
import co.casterlabs.rhs.protocol.http.HttpResponse;
import co.casterlabs.rhs.protocol.http.HttpSession;

public class _RouteStreamEgressExternal implements EndpointProvider {

    @HttpEndpoint(path = "/session/:sessionId/egress/external/rtmp", allowedMethods = {
            HttpMethod.POST
    }, postprocessor = _Processor.class, preprocessor = _Processor.class)
    public HttpResponse onEgressRTMP(HttpSession session, EndpointData<User> data) {
        try {
            data.attachment().checkAdmin();

            Session qSession = Sessions.getSession(data.uriParameters().get("sessionId"), false);
            if (qSession == null) {
                return ApiResponse.SESSION_NOT_FOUND.response();
            }

            RTMPEgressConfiguration body = Rson.DEFAULT.fromJson(session.body().string(), RTMPEgressConfiguration.class);
            if (!session.uri().query.isEmpty()) {
                // compat
                body.filter = StreamFilter.from(session.uri().query);
            }

            body.create(qSession);

            return ApiResponse.success(StandardHttpStatus.CREATED);
        } catch (DependencyException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return ApiResponse.NOT_ENABLED.response();
        } catch (AuthenticationException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return ApiResponse.UNAUTHORIZED.response();
        } catch (JsonParseException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return ApiResponse.BAD_REQUEST.response();
        } catch (Throwable t) {
            if (Quark.DEBUG) {
                t.printStackTrace();
            }
            return ApiResponse.INTERNAL_ERROR.response();
        }
    }

    @Deprecated
    @HttpEndpoint(path = "/session/:sessionId/egress/external/rtmp_ff", allowedMethods = {
            HttpMethod.POST
    }, postprocessor = _Processor.class, preprocessor = _Processor.class)
    public HttpResponse onEgressRTMPFFmpeg(HttpSession session, EndpointData<User> data) {
        try {
            data.attachment().checkAdmin();

            Session qSession = Sessions.getSession(data.uriParameters().get("sessionId"), false);
            if (qSession == null) {
                return ApiResponse.SESSION_NOT_FOUND.response();
            }

            RTMPEgressConfiguration body = Rson.DEFAULT.fromJson(session.body().string(), RTMPEgressConfiguration.class);
            if (!session.uri().query.isEmpty()) {
                // compat
                body.filter = StreamFilter.from(session.uri().query);
            }

            body.useNativeImpl = false; // compat.
            body.create(qSession);

            return ApiResponse.success(StandardHttpStatus.CREATED);
        } catch (DependencyException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return ApiResponse.NOT_ENABLED.response();
        } catch (AuthenticationException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return ApiResponse.UNAUTHORIZED.response();
        } catch (JsonParseException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return ApiResponse.BAD_REQUEST.response();
        } catch (Throwable t) {
            if (Quark.DEBUG) {
                t.printStackTrace();
            }
            return ApiResponse.INTERNAL_ERROR.response();
        }
    }

    @Deprecated
    @HttpEndpoint(path = "/session/:sessionId/egress/external/rtmp_ntv", allowedMethods = {
            HttpMethod.POST
    }, postprocessor = _Processor.class, preprocessor = _Processor.class)
    public HttpResponse onEgressRTMPNative(HttpSession session, EndpointData<User> data) {
        try {
            data.attachment().checkAdmin();

            Session qSession = Sessions.getSession(data.uriParameters().get("sessionId"), false);
            if (qSession == null) {
                return ApiResponse.SESSION_NOT_FOUND.response();
            }

            RTMPEgressConfiguration body = Rson.DEFAULT.fromJson(session.body().string(), RTMPEgressConfiguration.class);
            if (!session.uri().query.isEmpty()) {
                // compat
                body.filter = StreamFilter.from(session.uri().query);
            }

            body.useNativeImpl = true; // compat.
            body.create(qSession);

            return ApiResponse.success(StandardHttpStatus.CREATED);
        } catch (DependencyException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return ApiResponse.NOT_ENABLED.response();
        } catch (AuthenticationException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return ApiResponse.UNAUTHORIZED.response();
        } catch (JsonParseException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return ApiResponse.BAD_REQUEST.response();
        } catch (Throwable t) {
            if (Quark.DEBUG) {
                t.printStackTrace();
            }
            return ApiResponse.INTERNAL_ERROR.response();
        }
    }

    @HttpEndpoint(path = "/session/:sessionId/egress/external/pipeline", allowedMethods = {
            HttpMethod.POST
    }, postprocessor = _Processor.class, preprocessor = _Processor.class)
    public HttpResponse onEgressPipeline(HttpSession session, EndpointData<User> data) {
        try {
            data.attachment().checkAdmin();

            Session qSession = Sessions.getSession(data.uriParameters().get("sessionId"), false);
            if (qSession == null) {
                return ApiResponse.SESSION_NOT_FOUND.response();
            }

            PipelineEgressConfiguration body = Rson.DEFAULT.fromJson(session.body().string(), PipelineEgressConfiguration.class);
            if (!session.uri().query.isEmpty()) {
                // compat
                body.filter = StreamFilter.from(session.uri().query);
            }

            body.create(qSession);

            return ApiResponse.success(StandardHttpStatus.CREATED);
        } catch (DependencyException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return ApiResponse.NOT_ENABLED.response();
        } catch (AuthenticationException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return ApiResponse.UNAUTHORIZED.response();
        } catch (JsonParseException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            return ApiResponse.BAD_REQUEST.response();
        } catch (Throwable t) {
            if (Quark.DEBUG) {
                t.printStackTrace();
            }
            return ApiResponse.INTERNAL_ERROR.response();
        }
    }

}
