package co.casterlabs.quark.protocol.hls;

import org.jetbrains.annotations.ApiStatus.Experimental;

import co.casterlabs.quark.core.util.EnvHelper;

public class HLSEnv {

    /**
     * Whether or not to generate a HLS playlist for each session.
     */
    public static final @Experimental boolean EXP_HLS = EnvHelper.bool("QUARK_EXP_HLS", false);

    /**
     * Whether or the generated playlist should be in fMP4 (true) or TS (false).
     */
    public static final @Experimental boolean EXP_HLS_FMP4 = EnvHelper.bool("QUARK_EXP_HLS_FMP4", false);

}
