package co.casterlabs.quark.auth;

import org.jetbrains.annotations.Nullable;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;

import co.casterlabs.quark.Quark;
import lombok.NonNull;

public class Auth {
    private static final String[] EMPTY_ARR = {};

    private static final User ADMIN_USER = new User("admin", true, EMPTY_ARR);
    private static @Nullable User ANON_USER;

    private static JWTVerifier verifier;

    static {
        if (Quark.AUTH_SECRET != null && !Quark.AUTH_SECRET.isEmpty()) {
            Algorithm signingAlg = Algorithm.HMAC256(Quark.AUTH_SECRET);

            verifier = JWT.require(signingAlg)
                .build();
        }

        if (Quark.AUTH_ANON_PREGEX != null && !Quark.AUTH_ANON_PREGEX.isEmpty()) {
            ANON_USER = new User(
                "anonymous",
                false,
                new String[] {
                        Quark.AUTH_ANON_PREGEX
                }
            );
        }
    }

    public static @NonNull User authenticate(@Nullable String token) throws AuthenticationException {
        if (verifier == null) {
            return ADMIN_USER;
        }

        if (token == null) {
            if (ANON_USER == null) {
                throw new AuthenticationException("No token provided.");
            }
            return ANON_USER;
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring("Bearer ".length());
        }

        try {
            DecodedJWT jwt = verifier.verify(token);

            String id = jwt.getClaim("sub").asString();
            boolean isAdmin = truthy(jwt.getClaim("isAdmin").asBoolean());
            String[] playbackRegexes = claimToArray(jwt.getClaim("playback"));

            if (id == null) {
                id = "anonymous";
            }

            return new User(id, isAdmin, playbackRegexes);
        } catch (JWTVerificationException e) {
            throw new AuthenticationException("Invalid token: " + e.getMessage());
        }
    }

    private static final boolean truthy(Boolean b) {
        if (b == null) return false;
        return b;
    }

    private static String[] claimToArray(Claim claim) {
        if (claim == null) {
            return EMPTY_ARR;
        } else if (claim.asString() != null) {
            return new String[] {
                    claim.asString()
            };
        } else if (claim.asArray(String.class) != null) {
            return claim.asArray(String.class);
        } else {
            return EMPTY_ARR;
        }
    }

}
