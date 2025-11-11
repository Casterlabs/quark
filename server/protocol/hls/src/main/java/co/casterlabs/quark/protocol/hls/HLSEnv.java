package co.casterlabs.quark.protocol.hls;

import org.jetbrains.annotations.ApiStatus.Experimental;

public class HLSEnv {

    /**
     * Whether or not to generate a HLS playlist for each session.
     */
    public static final @Experimental boolean EXP_HLS = "true".equalsIgnoreCase(System.getenv("QUARK_EXP_HLS"));

}
