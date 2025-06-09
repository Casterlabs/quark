package co.casterlabs.quark.session;

import co.casterlabs.flv4j.flv.tags.FLVTag;

/**
 * The {@link #tags()} timestamps <i>should</i> be 0.
 */
public record FLVSequence(FLVTag... tags) {

}
