package co.casterlabs.quark.session;

import co.casterlabs.flv4j.flv.tags.FLVTag;

/**
 * {@link #tag()} will always contain dts (generated via wallclock).
 */
public record FLVData(long pts, FLVTag tag) {

}
