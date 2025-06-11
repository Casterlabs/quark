package co.casterlabs.quark.egress.http;

import java.io.IOException;

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
        final int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        ApiFramework framework = new ApiFramework();
        framework.register(new _RouteStreamEgressPlayback());

        HttpServer server = new HttpServerBuilder()
            .withPort(port)
            .withBehindProxy(true)
            .withKeepAliveSeconds(-1)
            .withMinSoTimeoutSeconds(60)
            .withServerHeader("Quark")
            .withTaskExecutor(RakuraiTaskExecutor.INSTANCE)
            .with(new HttpProtocol(), framework.httpHandler)
            .with(new WebsocketProtocol(), framework.websocketHandler)
            .build();

        server.start();
        FastLogger.logStatic("Listening on port %d", port);
    }

}
