package co.casterlabs.quark.core.session;

import co.casterlabs.flv4j.flv.tags.FLVTag;

/**
 * The {@link #tags()} timestamps <i>should</i> be 0 when a connection first
 * starts, but may not be due to session jamming.
 */
public record FLVSequence(FLVTag... tags) {

}
