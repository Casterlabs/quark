package co.casterlabs.quark.protocol.webrtc;

public class WebRTCEnv {

    public static final boolean EXP_WHIP = "true".equalsIgnoreCase(System.getenv("QUARK_EXP_WHIP"));
    public static final boolean EXP_WHIP_AVC_AUTO_RECONFIG = "true".equalsIgnoreCase(System.getenv("QUARK_EXP_WHIP_AVC_AUTO_RECONFIG"));

    public static final String WEBRTC_OVERRIDE_ADDRESS = System.getenv().getOrDefault("QUARK_WEBRTC_OVERRIDE_ADDRESS", ""); // default: advertises all interfaces

    public static final boolean EXP_WHEP = "true".equalsIgnoreCase(System.getenv("QUARK_EXP_WHEP"));

}
