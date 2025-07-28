package co.casterlabs.quark.session.listeners;

import java.util.Arrays;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.flv4j.flv.tags.FLVTagType;
import co.casterlabs.flv4j.flv.tags.audio.FLVAudioChannels;
import co.casterlabs.flv4j.flv.tags.audio.FLVAudioFormat;
import co.casterlabs.flv4j.flv.tags.audio.FLVAudioRate;
import co.casterlabs.flv4j.flv.tags.audio.FLVAudioSampleSize;
import co.casterlabs.flv4j.flv.tags.audio.FLVStandardAudioTagData;
import co.casterlabs.flv4j.flv.tags.audio.data.AudioData;
import co.casterlabs.flv4j.flv.tags.audio.ex.FLVExAudioMultitrackType;
import co.casterlabs.flv4j.flv.tags.audio.ex.FLVExAudioTagData;
import co.casterlabs.flv4j.flv.tags.audio.ex.FLVExAudioTrack;
import co.casterlabs.rhs.protocol.uri.Query;

public record StreamFilter(
    int audioStreamSelection // -1 for all
) {
    public static final StreamFilter ALL = new StreamFilter(-1);

    /**
     * @return null, if the filter dropped the tag. You should only use the returned
     *         FLVTag as it may be transformed to normalize the data stream.
     */
    public @Nullable FLVTag transform(FLVTag tag) {
        switch (tag.type()) {
            case AUDIO: {
                if (this.audioStreamSelection < 0) return tag; // Allow all audio tracks.

                if (tag.data() instanceof FLVStandardAudioTagData && this.audioStreamSelection == 0) {
                    return tag; // Check for STD audio stream (implicit id of 0).
                } else if (tag.data() instanceof FLVExAudioTagData aex) {
                    // find the corresponding track :)
                    for (FLVExAudioTrack track : aex.tracks()) {
                        if (track.id() != this.audioStreamSelection) continue; // not our track!

                        // https://github.com/videolan/vlc/blob/master/src/misc/fourcc_list.h
                        switch (track.codec().string()) {
                            case "aac ":
                            case "mp4a": {
                                byte[] aacSeqOrRaw = track.data().raw();

                                // https://rtmp.veriskope.com/pdf/video_file_format_spec_v10_1.pdf#page=77
                                // AAC requires an additional byte at the start which encodes the packet type.

                                byte[] stdFlvAacPayload = new byte[aacSeqOrRaw.length + 1];
                                stdFlvAacPayload[0] = (byte) (aex.isSequenceHeader() ? 0 : 1);
                                System.arraycopy(aacSeqOrRaw, 0, stdFlvAacPayload, 1, aacSeqOrRaw.length);

                                return new FLVTag(
                                    FLVTagType.AUDIO,
                                    tag.timestamp(),
                                    tag.streamId(),
                                    new FLVStandardAudioTagData(
                                        FLVAudioFormat.AAC.id,
                                        FLVAudioRate.KHZ_44.id, // also on page 77, these values are hard-coded
                                        FLVAudioSampleSize.BIT_16.id,
                                        FLVAudioChannels.STEREO.id,
                                        new AudioData(stdFlvAacPayload)
                                    )
                                );
                            }

                            // TODO documentation on the other std audio formats is a little hard to come
                            // by. I would like to get MP3/MP3_8 working natively at some point.
//                            case "mp3 ":
//                            case ".mp3":
//                            case "MP3 ":

                            default:
                                // we can't convert it to a standard audio tag, so we need to remake the
                                // enhanced audio tag and hope for the best.
                                return new FLVTag(
                                    FLVTagType.AUDIO,
                                    tag.timestamp(),
                                    tag.streamId(),
                                    new FLVExAudioTagData(
                                        aex.rawType(),
                                        aex.modifiers(),
                                        FLVExAudioMultitrackType.ONE_TRACK.id,
                                        Arrays.asList(
                                            new FLVExAudioTrack(
                                                track.codec(),
                                                0, // Sole audio track :^)
                                                track.data()
                                            )
                                        )
                                    )
                                );
                        }
                    }
                }

                return null; // drop!
            }

            case SCRIPT:
            case VIDEO:
            default:
                // We only support audio filtering at the moment. So we'll never drop a
                // VIDEO/SCRIPT tag. TODO
                return tag;
        }
    }

    public static StreamFilter from(Query query) {
        int streamA = Integer.parseInt(query.getSingleOrDefault("s:a", "-1"));
        return new StreamFilter(streamA);
    }

}
