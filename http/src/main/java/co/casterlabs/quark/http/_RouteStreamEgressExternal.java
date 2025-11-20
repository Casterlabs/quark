package co.casterlabs.quark.http;

import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.auth.AuthenticationException;
import co.casterlabs.quark.core.auth.User;
import co.casterlabs.quark.core.egress.config.EgressConfiguration;
import co.casterlabs.quark.core.extensibility._Extensibility;
import co.casterlabs.quark.core.http.ApiResponse;
import co.casterlabs.quark.core.http.QuarkHttpProcessor;
import co.casterlabs.quark.core.http.QuarkHttpProcessor.EndpointContext;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.quark.core.util.DependencyException;
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

    @HttpEndpoint(path = "/session/:sessionId/egress/external/:type", allowedMethods = {
            HttpMethod.POST
    }, postprocessor = QuarkHttpProcessor.class, preprocessor = QuarkHttpProcessor.class)
    public HttpResponse onEgressExternal(HttpSession session, EndpointData<EndpointContext> data) {
        User user = data.attachment().user();
        Session qSession = data.attachment().session();

        try {
            user.checkAdmin();

            Class<? extends EgressConfiguration> configurationClass = _Extensibility.egressConfigurations.get(data.uriParameters().get("type"));
            if (configurationClass == null) {
                return ApiResponse.BAD_REQUEST.response();
            }

            EgressConfiguration body = Rson.DEFAULT.fromJson(session.body().string(), configurationClass);
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
