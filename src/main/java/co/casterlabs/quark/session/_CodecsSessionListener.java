package co.casterlabs.quark.session;

import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.flv4j.flv.tags.FLVTagType;
import co.casterlabs.flv4j.flv.tags.audio.FLVAudioFormat;
import co.casterlabs.flv4j.flv.tags.audio.FLVStandardAudioTagData;
import co.casterlabs.flv4j.flv.tags.audio.ex.FLVExAudioTagData;
import co.casterlabs.flv4j.flv.tags.audio.ex.FLVExAudioTrack;
import co.casterlabs.flv4j.flv.tags.video.FLVVideoCodec;
import co.casterlabs.flv4j.flv.tags.video.FLVVideoFrameType;
import co.casterlabs.flv4j.flv.tags.video.FLVVideoPayload;
import co.casterlabs.quark.session.info.AudioStream;
import co.casterlabs.quark.session.info.SessionInfo;
import co.casterlabs.quark.session.info.VideoStream;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class _CodecsSessionListener extends QuarkSessionListener {
    private final SessionInfo info;

    private long lastKeyFrame = -1L;
    private boolean hasStdAudio = false; // we have to offset the ex audio since this is always index 0.

    private void process(FLVTag tag) {
        if (tag.type() == FLVTagType.SCRIPT) return; // ignore.

        if (tag.data() instanceof FLVVideoPayload vstd) {
            // Note that we do not support the ex video payload yet. TODO
            if (this.info.video.length == 0) {
                this.info.video = new VideoStream[] {
                        new VideoStream(0, flvToFourCC(vstd.codec()))
                };
            }
        } else if (tag.data() instanceof FLVStandardAudioTagData astd) {
            if (!this.hasStdAudio) {
                AudioStream std = new AudioStream(0, flvToFourCC(astd.format()));

                if (this.info.audio.length == 0) {
                    this.info.audio = new AudioStream[] {
                            std
                    };
                } else {
                    AudioStream[] newAudio = new AudioStream[this.info.audio.length + 1];
                    System.arraycopy(this.info.audio, 0, newAudio, 1, this.info.audio.length);
                    newAudio[0] = std;
                    this.info.audio = newAudio;
                }
            }
            this.hasStdAudio = true;
        } else if (tag.data() instanceof FLVExAudioTagData aex) {
            for (FLVExAudioTrack track : aex.tracks()) {
                if (this.info.audio.length <= track.id()) {
                    AudioStream[] newAudio = new AudioStream[this.info.audio.length + 1];
                    System.arraycopy(this.info.audio, 0, newAudio, 0, this.info.audio.length);
                    this.info.audio = newAudio;
                }

                this.info.audio[track.id()] = new AudioStream(track.id(), track.codec().string());
            }
        }

        if (tag.data() instanceof FLVVideoPayload video) {
            // Note that we do not support the ex video payload yet. TODO

            if (video.frameType() == FLVVideoFrameType.KEY_FRAME) {
                long diff = tag.timestamp() - this.lastKeyFrame;
                this.lastKeyFrame = tag.timestamp();
                this.info.video[0].keyFrameInterval = (int) (diff / 1000);
            }

            this.info.video[0].bitrate.sample(video.size(), tag.timestamp());
        } else if (tag.data() instanceof FLVStandardAudioTagData astd) {
            this.info.audio[0].bitrate.sample(astd.size(), tag.timestamp());
        } else if (tag.data() instanceof FLVExAudioTagData aex) {
            for (FLVExAudioTrack track : aex.tracks()) {
                this.info.audio[track.id()].bitrate.sample(track.data().size(), tag.timestamp());
            }
        }
    }

    @Override
    public void onSequence(QuarkSession session, FLVSequence seq) {
        for (FLVTag tag : seq.tags()) {
            this.process(tag);
        }
    }

    @Override
    public void onData(QuarkSession session, FLVData data) {
        this.process(data.tag());
    }

    @Override
    public void onClose(QuarkSession session) {} // NOOP

    @Override
    public boolean async() {
        return false;
    }

    /* https://github.com/videolan/vlc/blob/master/src/misc/fourcc_list.h */
    private static String flvToFourCC(FLVVideoCodec codec) {
        // @formatter:off
        return switch (codec) {
            case H264 ->          "avc1";
            case ON2_VP6 ->       "vp6f";
            case ON2_VP6_ALPHA -> "vp6a";
            case SCREEN ->        "fsv1";
            case SCREEN_2 ->      "fsv2";
            case SORENSON_H263 -> "flv1";
            case JPEG -> "unknown";
            default -> "unknown";
        };
        // @formatter:on
    }

    /* https://github.com/videolan/vlc/blob/master/src/misc/fourcc_list.h */
    private static String flvToFourCC(FLVAudioFormat format) {
        // @formatter:off
        return switch (format) {
            case AAC ->        "mp4a";
            case ADPCM ->      "swfa";
            case G711_ALAW ->  "alaw";
            case G711_MULAW -> "ulaw";
            case LPCM ->       "lpcm";
            case LPCM_LE ->    "lpcm";
            case MP3, MP3_8 -> "mp3 ";
            case SPEEX ->      "spx ";
            case NELLYMOSER, NELLYMOSER_16_MONO, NELLYMOSER_8_MONO -> "nmos";
            case DEVICE_SPECIFIC -> "unknown";
            default -> "unknown";
        };
        // @formatter:on
    }

}
