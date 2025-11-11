package co.casterlabs.quark.http;

import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.auth.AuthenticationException;
import co.casterlabs.quark.core.auth.User;
import co.casterlabs.quark.core.http.ApiResponse;
import co.casterlabs.quark.core.http.QuarkHttpProcessor;
import co.casterlabs.quark.core.http.QuarkHttpProcessor.EndpointContext;
import co.casterlabs.quark.core.ingest.ffmpeg.FFmpegProvider;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.quark.core.util.FF;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rakurai.json.serialization.JsonParseException;
import co.casterlabs.rakurai.json.validation.JsonValidate;
import co.casterlabs.rhs.HttpMethod;
import co.casterlabs.rhs.HttpStatus.StandardHttpStatus;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointData;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointProvider;
import co.casterlabs.rhs.protocol.api.endpoints.HttpEndpoint;
import co.casterlabs.rhs.protocol.http.HttpResponse;
import co.casterlabs.rhs.protocol.http.HttpSession;

public class _RouteStreamIngress implements EndpointProvider {

    @SuppressWarnings("resource")
    @HttpEndpoint(path = "/session/ingress", allowedMethods = {
            HttpMethod.POST
    }, postprocessor = QuarkHttpProcessor.class, preprocessor = QuarkHttpProcessor.class)
    public HttpResponse onIngressFile(HttpSession session, EndpointData<EndpointContext> data) {
        User user = data.attachment().user();
        Session qSession = data.attachment().session();

        try {
            user.checkAdmin();

            if (!FF.canUseMpeg) {
                return ApiResponse.NOT_ENABLED.response();
            }

            IngressFileBody body = Rson.DEFAULT.fromJson(session.body().string(), IngressFileBody.class);
            new FFmpegProvider(qSession, body.source, body.loop);

            return ApiResponse.success(StandardHttpStatus.CREATED);
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

    @JsonClass(exposeAll = true)
    public static class IngressFileBody {
        public String id = null;
        public String source = null;
        public boolean loop = false;

        @JsonValidate
        private void $validate() {
            if (this.id == null) throw new IllegalArgumentException("id cannot be null.");
            if (this.source == null) throw new IllegalArgumentException("source cannot be null.");
        }
    }

}
