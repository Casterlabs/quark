package co.casterlabs.quark.egress.http;

import co.casterlabs.quark.Quark;
import co.casterlabs.quark.session.QuarkSession;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rhs.HttpMethod;
import co.casterlabs.rhs.HttpStatus.StandardHttpStatus;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointData;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointProvider;
import co.casterlabs.rhs.protocol.api.endpoints.HttpEndpoint;
import co.casterlabs.rhs.protocol.http.HttpResponse;
import co.casterlabs.rhs.protocol.http.HttpSession;

public class _RouteStreamControl implements EndpointProvider {

    @HttpEndpoint(path = "/streams", allowedMethods = {
            HttpMethod.GET
    })
    public HttpResponse onGetStreamIDs(HttpSession session, EndpointData<Void> data) {
        JsonArray ids = new JsonArray();
        Quark.forEachSession((s) -> ids.add(s.id));

        return HttpResponse.newFixedLengthResponse(StandardHttpStatus.OK, ids.toString(true))
            .mime("application/json; charset=utf-8");
    }

    @HttpEndpoint(path = "/stream/:streamId", allowedMethods = {
            HttpMethod.DELETE
    })
    public HttpResponse onEndStream(HttpSession session, EndpointData<Void> data) {
        QuarkSession qSession = Quark.session(data.uriParameters().get("streamId"), false);
        if (qSession == null) {
            return HttpResponse.newFixedLengthResponse(StandardHttpStatus.NOT_FOUND, "Stream not found.");
        }

        qSession.close();

        return HttpResponse.newFixedLengthResponse(StandardHttpStatus.OK, "Session closed.");
    }

}
