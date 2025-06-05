package co.casterlabs.quark.egress;

import java.io.IOException;

import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.egress.http.HTTPDaemon;

public class Bootstrap {

    /* ffplay -x 1280 -y 720 -volume 50 http://localhost:8080/flv/test */
    public static void main(String[] args) throws IOException {
        Quark.FLUX_HOST.toString(); // Init the class.

        HTTPDaemon.init();
    }

}
