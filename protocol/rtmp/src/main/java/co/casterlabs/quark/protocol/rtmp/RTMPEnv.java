package co.casterlabs.quark.protocol.rtmp;

import co.casterlabs.quark.core.util.EnvHelper;

public class RTMPEnv {

    /**
     * -1 to disable.
     */
    public static final int RTMP_PORT = EnvHelper.integer("RTMP_PORT", 1935);

}
