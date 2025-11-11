package co.casterlabs.quark.protocol.hls.routes;

import java.io.File;

import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.Sessions;
import co.casterlabs.quark.core.auth.Auth;
import co.casterlabs.quark.core.auth.AuthenticationException;
import co.casterlabs.quark.core.auth.User;
import co.casterlabs.quark.core.extensibility.QuarkHttpEndpoint;
import co.casterlabs.quark.core.http.ApiResponse;
import co.casterlabs.quark.core.http.QuarkHttpProcessor;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.quark.protocol.hls.HLSEnv;
import co.casterlabs.quark.protocol.hls.HLSProtocol;
import co.casterlabs.rhs.HttpMethod;
import co.casterlabs.rhs.HttpStatus.StandardHttpStatus;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointData;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointProvider;
import co.casterlabs.rhs.protocol.api.endpoints.HttpEndpoint;
import co.casterlabs.rhs.protocol.http.HttpResponse;
import co.casterlabs.rhs.protocol.http.HttpSession;

@QuarkHttpEndpoint
public class RouteStreamEgressPlaybackHLS implements EndpointProvider {

    @HttpEndpoint(path = "/session/:sessionId/egress/playback/hls/:token/:file", allowedMethods = {
            HttpMethod.GET
    }, priority = 10, postprocessor = QuarkHttpProcessor.class)
    public HttpResponse onFLVPlayback(HttpSession session, EndpointData<Void> data) {
        try {
            if (!HLSEnv.EXP_HLS) {
                return ApiResponse.NOT_ENABLED.response();
            }

            User user;
            try {
                user = Auth.authenticate(data.uriParameters().get("token"));
            } catch (AuthenticationException e) {
                if (Quark.DEBUG) {
                    e.printStackTrace();
                }
                return ApiResponse.UNAUTHORIZED.response();
            }

            user.checkPlayback(data.uriParameters().get("sessionId"));

            Session qSession = Sessions.getSession(data.uriParameters().get("sessionId"), false);
            if (qSession == null) {
                return ApiResponse.SESSION_NOT_FOUND.response();
            }

            File hlsDirectory = new File(HLSProtocol.HLS_ROOT, qSession.id);

            File file = new File(hlsDirectory, data.uriParameters().get("file"));
            if (!file.exists()) {
                return ApiResponse.FILE_NOT_FOUND.response();
            }
            if (!file.getCanonicalPath().startsWith(hlsDirectory.getCanonicalPath())) {
                // Attempted path traversal
                return ApiResponse.FILE_NOT_FOUND.response();
            }

            String mime = switch (file.getName().split("\\.")[1]) {
                case "m3u8" -> "application/vnd.apple.mpegurl";
                case "ts" -> "video/mp2t";
                case "mp4" -> "video/mp4";
                default -> "application/octet-stream";
            };

            String cacheControl = switch (file.getName().split("\\.")[1]) {
                case "m3u8" -> "private, max-age=0, no-store"; // ALWAYS retrieve a fresh copy.

                // 3 minutes, we don't know the keyframe interval and the playlist size is
                // theoretically unknown. At a KFI of 2s and a playlist size of 4 it would be 8
                // seconds (in theory). But since we can't guarantee keyframe intervals, we'll
                // go with 3 minutes. It's not perfect, but it's better than nothing.
                default -> "public, max-age=180";
            };

            return HttpResponse.newRangedFileResponse(session, StandardHttpStatus.OK, file)
                .mime(mime)
                .header("Cache-Control", cacheControl);
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
