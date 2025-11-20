package co.casterlabs.quark.core.http;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.Sessions;
import co.casterlabs.quark.core.auth.Auth;
import co.casterlabs.quark.core.auth.AuthenticationException;
import co.casterlabs.quark.core.auth.User;
import co.casterlabs.quark.core.http.QuarkHttpProcessor.EndpointContext;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.rhs.protocol.HeaderValue;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointData;
import co.casterlabs.rhs.protocol.api.postprocessors.Postprocessor;
import co.casterlabs.rhs.protocol.api.preprocessors.Preprocessor;
import co.casterlabs.rhs.protocol.http.HttpResponse;
import co.casterlabs.rhs.protocol.http.HttpSession;

public class QuarkHttpProcessor implements Postprocessor.Http<Object>, Preprocessor.Http<EndpointContext> {

    @Override
    public void preprocess(HttpSession session, PreprocessorContext<HttpResponse, EndpointContext> context) {
        User user = null;
        Session qSession = null;

        {
            // Prefer header, fallback to query.
            HeaderValue tokenHeader = session.headers().getSingle("authorization");

            String token;
            if (tokenHeader == null) {
                token = session.uri().query.getSingle("authorization"); // may still be null after this.
            } else {
                token = tokenHeader.raw();
            }

            try {
                user = Auth.authenticate(token);
            } catch (AuthenticationException e) {
                if (Quark.DEBUG) {
                    e.printStackTrace();
                }
                context.respondEarly(ApiResponse.UNAUTHORIZED.response());
            }
        }

        if (context.uriParameters().containsKey("sessionId")) {
            String sessionId = context.uriParameters().get("sessionId");

            qSession = Sessions.getSession(sessionId, false);
            if (qSession == null) {
                context.respondEarly(ApiResponse.SESSION_NOT_FOUND.response());
            }
        }

        context.attachment(new EndpointContext(user, qSession));
    }

    @Override
    public void postprocess(HttpSession session, HttpResponse response, EndpointData<Object> ignored) {
        response
            .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS");
    }

    public static record EndpointContext(
        @Nullable User user,
        /**
         * To obtain a session reference, include a :sessionId URI parameter in your
         * endpoint.
         */
        @Nullable Session session
    ) {
    }

}
