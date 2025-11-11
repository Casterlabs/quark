package co.casterlabs.quark.http;

public class HTTPEnv {

    /**
     * -1 to disable.
     */
    public static final int HTTP_PORT = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "8080"));

}
