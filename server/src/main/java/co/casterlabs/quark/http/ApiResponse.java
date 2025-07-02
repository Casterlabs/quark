package co.casterlabs.quark.http;

import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonObject;
import co.casterlabs.rhs.HttpStatus;
import co.casterlabs.rhs.HttpStatus.StandardHttpStatus;
import co.casterlabs.rhs.protocol.http.HttpResponse;

public enum ApiResponse {
    SESSION_NOT_FOUND(StandardHttpStatus.NOT_FOUND),

    UNAUTHORIZED(StandardHttpStatus.UNAUTHORIZED),
    BAD_REQUEST(StandardHttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(StandardHttpStatus.INTERNAL_ERROR),
    NOT_ENABLED(StandardHttpStatus.BAD_REQUEST),

    ;

    private static final String SUCCESS_EMPTY = new JsonObject()
        .put("data", JsonObject.EMPTY_OBJECT)
        .putNull("error")
        .toString(true);

    private final HttpStatus status;
    private final String json;

    private ApiResponse(HttpStatus status) {
        this.status = status;
        this.json = new JsonObject()
            .putNull("data")
            .put("error", this.name())
            .toString(true);
    }

    public HttpResponse response() {
        return HttpResponse.newFixedLengthResponse(this.status, this.json)
            .mime("application/json; charset=utf-8");
    }

    public static HttpResponse success(HttpStatus status, JsonElement data) {
        String json = new JsonObject()
            .put("data", data)
            .putNull("error")
            .toString(true);

        return HttpResponse.newFixedLengthResponse(status, json)
            .mime("application/json; charset=utf-8");
    }

    public static HttpResponse success(HttpStatus status) {
        return HttpResponse.newFixedLengthResponse(status, SUCCESS_EMPTY)
            .mime("application/json; charset=utf-8");
    }

}
