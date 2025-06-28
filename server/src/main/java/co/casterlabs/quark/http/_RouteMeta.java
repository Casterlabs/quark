package co.casterlabs.quark.http;

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
    }, postprocessor = _Processor.class)
    public HttpResponse onHealthCheck(HttpSession session, EndpointData<Void> data) {
        return HttpResponse.newFixedLengthResponse(StandardHttpStatus.OK, "Healthy");
    }

    @HttpEndpoint(path = ".*", allowedMethods = {
            HttpMethod.OPTIONS
    }, postprocessor = _Processor.class)
    public HttpResponse onCors(HttpSession session, EndpointData<Void> data) {
        return HttpResponse.newFixedLengthResponse(StandardHttpStatus.OK, "");
    }

    @HttpEndpoint(path = ".*", priority = -1000, postprocessor = _Processor.class)
    public HttpResponse onUnknownEndpoint(HttpSession session, EndpointData<Void> data) {
        return HttpResponse.newFixedLengthResponse(StandardHttpStatus.METHOD_NOT_ALLOWED, "Unknown endpoint.");
    }

}
