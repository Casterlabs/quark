package co.casterlabs.quark.core.flux;

import java.io.IOException;

import co.casterlabs.flux.client.realtime.FluxRealtimeClient;
import co.casterlabs.flux.client.realtime.FluxRealtimeClientListener;
import co.casterlabs.flux.client.realtime.tcp.FluxTcpClientBuilder;
import co.casterlabs.quark.core.Quark;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class FluxConnection {
    static final FluxRealtimeClient client;

    static {
        FluxRealtimeClient c;
        while (true) {
            try {
                c = new FluxTcpClientBuilder()
                    .withHostAddress(Quark.FLUX_HOST)
                    .withHostPort(Quark.FLUX_PORT)
                    .withToken(Quark.FLUX_TOKEN)
                    .withRealtimeListener(new FluxRealtimeClientListener() {
                    })
                    .build();
                break;
            } catch (IOException e) {
                FastLogger.logStatic(LogLevel.SEVERE, "Couldn't connect to Flux, retrying in 5s:\n%s", e);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {}
            }
        }
        client = c;
    }

    static synchronized void publish(String tube, QuarkPacketType type, byte[] data) throws IOException {
        byte[] packet = new byte[1 + data.length];
        packet[0] = (byte) type.id;
        System.arraycopy(data, 0, packet, 1, data.length);
        client.publish(tube, packet);
    }

}
