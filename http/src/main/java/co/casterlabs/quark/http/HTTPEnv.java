package co.casterlabs.quark.http;

import co.casterlabs.quark.core.util.EnvHelper;

public class HTTPEnv {

    /**
     * -1 to disable.
     */
    public static final int HTTP_PORT = EnvHelper.integer("HTTP_PORT", 8080);

}
