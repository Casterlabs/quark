package co.casterlabs.quark.protocol.rtmp;

public class RTMPEnv {

    /**
     * -1 to disable.
     */
    public static final int RTMP_PORT = Integer.parseInt(System.getenv().getOrDefault("RTMP_PORT", "1935"));

}
