package co.casterlabs.quark.http;

import co.casterlabs.quark.Quark;
import co.casterlabs.quark.auth.Auth;
import co.casterlabs.quark.auth.AuthenticationException;
import co.casterlabs.quark.auth.User;
import co.casterlabs.rhs.protocol.HeaderValue;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointData;
import co.casterlabs.rhs.protocol.api.postprocessors.Postprocessor;
import co.casterlabs.rhs.protocol.api.preprocessors.Preprocessor;
import co.casterlabs.rhs.protocol.http.HttpResponse;
import co.casterlabs.rhs.protocol.http.HttpSession;

public class _Processor implements Postprocessor.Http<User>, Preprocessor.Http<User> {

    @Override
    public void preprocess(HttpSession session, PreprocessorContext<HttpResponse, User> context) {
        // Prefer header, fallback to query.

        HeaderValue tokenHeader = session.headers().getSingle("authorization");

        String token;
        if (tokenHeader == null) {
            token = session.uri().query.getSingle("authorization"); // may still be null after this.
        } else {
            token = tokenHeader.raw();
        }

        try {
            User user = Auth.authenticate(token);
            context.attachment(user);
        } catch (AuthenticationException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
            context.respondEarly(ApiResponse.UNAUTHORIZED.response());
        }
    }

    @Override
    public void postprocess(HttpSession session, HttpResponse response, EndpointData<User> data) {
        response
            .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS");
    }

}
