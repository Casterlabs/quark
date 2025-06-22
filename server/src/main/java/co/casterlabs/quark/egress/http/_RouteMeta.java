package co.casterlabs.quark.egress.http;

import co.casterlabs.rhs.HttpMethod;
import co.casterlabs.rhs.HttpStatus.StandardHttpStatus;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointData;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointProvider;
import co.casterlabs.rhs.protocol.api.endpoints.HttpEndpoint;
import co.casterlabs.rhs.protocol.http.HttpResponse;
import co.casterlabs.rhs.protocol.http.HttpSession;

public class _RouteMeta implements EndpointProvider {

    @HttpEndpoint(path = "/_healthcheck", allowedMethods = {
            HttpMethod.GET
    })
    public HttpResponse onHealthCheck(HttpSession session, EndpointData<Void> data) {
        return HttpResponse.newFixedLengthResponse(StandardHttpStatus.OK, "Healthy");
    }

    @HttpEndpoint(path = ".*", priority = -1000)
    public HttpResponse onUnknownEndpoint(HttpSession session, EndpointData<Void> data) {
        return HttpResponse.newFixedLengthResponse(StandardHttpStatus.METHOD_NOT_ALLOWED, "Unknown endpoint.");
    }

}
