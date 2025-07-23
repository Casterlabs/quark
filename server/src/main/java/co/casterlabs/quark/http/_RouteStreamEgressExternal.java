package co.casterlabs.quark.http;

import co.casterlabs.quark.Quark;
import co.casterlabs.quark.Sessions;
import co.casterlabs.quark.auth.AuthenticationException;
import co.casterlabs.quark.auth.User;
import co.casterlabs.quark.egress.FFmpegRTMPSessionListener;
import co.casterlabs.quark.session.Session;
import co.casterlabs.quark.util.FF;
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

            EgressRTMPBody body = Rson.DEFAULT.fromJson(session.body().string(), EgressRTMPBody.class);

            if (!FF.canUseMpeg) {
                return ApiResponse.NOT_ENABLED.response();
            }

            qSession.addAsyncListener(new FFmpegRTMPSessionListener(body.url, body.foreignId));

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
    public static class EgressRTMPBody {
        public String foreignId = null;
        public String url = null;

        @JsonValidate
        private void $validate() {
            if (this.url == null) throw new IllegalArgumentException("url cannot be null.");
        }
    }

}
