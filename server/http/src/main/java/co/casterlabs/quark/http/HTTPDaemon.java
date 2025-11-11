package co.casterlabs.quark.http;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import co.casterlabs.quark.core.extensibility.QuarkEntrypoint;
import co.casterlabs.quark.core.extensibility._Extensibility;
import co.casterlabs.rhs.HttpServer;
import co.casterlabs.rhs.HttpServerBuilder;
import co.casterlabs.rhs.protocol.http.HttpProtocol;
import co.casterlabs.rhs.protocol.websocket.WebsocketProtocol;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

@QuarkEntrypoint
public class HTTPDaemon {

    public static void start() throws IOException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
        _Extensibility.http.register(new _RouteMeta());
        _Extensibility.http.register(new _RouteStreamControl());
        _Extensibility.http.register(new _RouteStreamEgress());
        _Extensibility.http.register(new _RouteStreamEgressExternal());
        _Extensibility.http.register(new _RouteStreamEgressPlayback());
        _Extensibility.http.register(new _RouteStreamIngress());

        HttpServer server = new HttpServerBuilder()
            .withPort(HTTPEnv.HTTP_PORT)
            .withBehindProxy(true)
            .withKeepAliveSeconds(-1)
            .withMinSoTimeoutSeconds(60)
            .withServerHeader("Quark")
            .withTaskExecutor(_RakuraiTaskExecutor.INSTANCE)
            .with(new HttpProtocol(), _Extensibility.http.httpHandler)
            .with(new WebsocketProtocol(), _Extensibility.http.websocketHandler)
            .build();

        server.start();
        FastLogger.logStatic("Listening on port %d", HTTPEnv.HTTP_PORT);
    }

}
