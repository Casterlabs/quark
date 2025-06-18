package co.casterlabs.quark.egress.http;

import co.casterlabs.quark.Quark;
import co.casterlabs.quark.auth.AuthenticationException;
import co.casterlabs.quark.auth.User;
import co.casterlabs.quark.ingest.protocols.ffmpeg.FFmpegProvider;
import co.casterlabs.quark.session.Session;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rakurai.json.serialization.JsonParseException;
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
    }, postprocessor = _Processor.class, preprocessor = _Processor.class)
    public HttpResponse onIngressFile(HttpSession session, EndpointData<User> data) {
        try {
            data.attachment().checkAdmin();

            IngressFileBody body = Rson.DEFAULT.fromJson(session.body().string(), IngressFileBody.class);

            Session qSession = Quark.session(body.id, true);
            new FFmpegProvider(qSession, body.source, body.loop);

            return ApiResponse.success(StandardHttpStatus.CREATED);
        } catch (AuthenticationException e) {
            return ApiResponse.UNAUTHORIZED.response();
        } catch (JsonParseException e) {
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
    }

}
