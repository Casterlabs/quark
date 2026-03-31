package co.casterlabs.quark.protocol.webrtc;

import co.casterlabs.quark.core.util.EnvHelper;

public class WebRTCEnv {

    public static final boolean EXP_WHIP = EnvHelper.bool("QUARK_EXP_WHIP", false);
    public static final boolean EXP_WHIP_AVC_AUTO_RECONFIG = EnvHelper.bool("QUARK_EXP_WHIP_AVC_AUTO_RECONFIG", false);

    public static final String WEBRTC_OVERRIDE_ADDRESS = EnvHelper.string("QUARK_WEBRTC_OVERRIDE_ADDRESS", ""); // default: advertises all interfaces

    public static final boolean EXP_WHEP = EnvHelper.bool("QUARK_EXP_WHEP", false);

}
