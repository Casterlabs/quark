package co.casterlabs.quark;

import java.io.IOException;

import co.casterlabs.quark.http.HTTPDaemon;
import co.casterlabs.quark.ingest.rtmp.RTMPServer;

public class Bootstrap {

    /* ffplay -x 1280 -y 720 -volume 50 http://localhost:8080/session/test/egress/playback/flv */
    /* ffmpeg -stream_loop -1 -re -v debug -i test.flv -c copy -f flv rtmp://localhost/live/test */
    public static void main(String[] args) throws IOException {
        Quark.init();

        RTMPServer.start();
        HTTPDaemon.start();
    }

}
