package co.casterlabs.quark.http;

import java.io.IOException;

import co.casterlabs.quark.Quark;
import co.casterlabs.rhs.HttpServer;
import co.casterlabs.rhs.HttpServerBuilder;
import co.casterlabs.rhs.protocol.api.ApiFramework;
import co.casterlabs.rhs.protocol.http.HttpProtocol;
import co.casterlabs.rhs.protocol.websocket.WebsocketProtocol;
import lombok.SneakyThrows;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public class HTTPDaemon {

    @SneakyThrows
    public static void start() throws IOException {
        ApiFramework framework = new ApiFramework();
        framework.register(new _RouteMeta());
        framework.register(new _RouteStreamControl());
        framework.register(new _RouteStreamEgress());
        framework.register(new _RouteStreamEgressExternal());
        framework.register(new _RouteStreamEgressPlayback());
        framework.register(new _RouteStreamIngress());

        HttpServer server = new HttpServerBuilder()
            .withPort(Quark.HTTP_PORT)
            .withBehindProxy(true)
            .withKeepAliveSeconds(-1)
            .withMinSoTimeoutSeconds(60)
            .withServerHeader("Quark")
            .withTaskExecutor(_RakuraiTaskExecutor.INSTANCE)
            .with(new HttpProtocol(), framework.httpHandler)
            .with(new WebsocketProtocol(), framework.websocketHandler)
            .build();

        server.start();
        FastLogger.logStatic("Listening on port %d", Quark.HTTP_PORT);
    }

}
