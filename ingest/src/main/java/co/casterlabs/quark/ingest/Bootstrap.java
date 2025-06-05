package co.casterlabs.quark.ingest;

import java.io.IOException;

import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.ingest.protocols.rtmp.RTMPServer;

public class Bootstrap {

    /* ffmpeg -stream_loop -1 -re -v debug -i test.flv -c copy -f flv rtmp://localhost/live/test */
    public static void main(String[] args) throws IOException {
        Quark.FLUX_HOST.toString(); // Init the class.
        RTMPServer.start(1935);
    }

}
