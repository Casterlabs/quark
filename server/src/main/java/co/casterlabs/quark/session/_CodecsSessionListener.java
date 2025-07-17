package co.casterlabs.quark.session;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.commons.io.streams.StreamUtil;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.flv4j.flv.tags.FLVTagType;
import co.casterlabs.flv4j.flv.tags.audio.FLVAudioFormat;
import co.casterlabs.flv4j.flv.tags.audio.FLVStandardAudioTagData;
import co.casterlabs.flv4j.flv.tags.audio.ex.FLVExAudioTagData;
import co.casterlabs.flv4j.flv.tags.audio.ex.FLVExAudioTrack;
import co.casterlabs.flv4j.flv.tags.video.FLVVideoCodec;
import co.casterlabs.flv4j.flv.tags.video.FLVVideoFrameType;
import co.casterlabs.flv4j.flv.tags.video.FLVVideoPayload;
import co.casterlabs.quark.Quark;
import co.casterlabs.quark.session.info.SessionInfo;
import co.casterlabs.quark.session.info.StreamInfo;
import co.casterlabs.quark.session.info.StreamInfo.AudioStreamInfo;
import co.casterlabs.quark.session.info.StreamInfo.VideoStreamInfo;
import co.casterlabs.quark.session.listeners.FLVProcessSessionListener;
import co.casterlabs.quark.util.FF;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class _CodecsSessionListener extends SessionListener {
    private final Session session;
    private final SessionInfo info;

    private boolean hasStdAudio = false; // we have to offset the ex audio since this is always index 0.

    private void process(FLVTag tag) {
        if (tag.type() == FLVTagType.SCRIPT) return; // ignore.

        if (tag.data() instanceof FLVVideoPayload vstd) {
            // Note that we do not support the ex video payload yet. TODO
            if (this.info.video.length == 0) {
                this.info.video = new VideoStreamInfo[] {
                        new VideoStreamInfo(0, flvToFourCC(vstd.codec()))
                };
            }
        } else if (tag.data() instanceof FLVStandardAudioTagData astd) {
            if (!this.hasStdAudio) {
                AudioStreamInfo std = new AudioStreamInfo(0, flvToFourCC(astd.format()));

                if (this.info.audio.length == 0) {
                    this.info.audio = new AudioStreamInfo[] {
                            std
                    };
                } else {
                    AudioStreamInfo[] newAudio = new AudioStreamInfo[this.info.audio.length + 1];
                    System.arraycopy(this.info.audio, 0, newAudio, 1, this.info.audio.length);
                    newAudio[0] = std;
                    this.info.audio = newAudio;
                }
            }
            this.hasStdAudio = true;
        } else if (tag.data() instanceof FLVExAudioTagData aex) {
            for (FLVExAudioTrack track : aex.tracks()) {
                if (this.info.audio.length <= track.id()) {
                    AudioStreamInfo[] newAudio = new AudioStreamInfo[this.info.audio.length + 1];
                    System.arraycopy(this.info.audio, 0, newAudio, 0, this.info.audio.length);
                    this.info.audio = newAudio;
                }

                this.info.audio[track.id()] = new AudioStreamInfo(track.id(), track.codec().string());
            }
        }

        if (tag.data() instanceof FLVVideoPayload video) {
            // Note that we do not support the ex video payload yet. TODO
            VideoStreamInfo info = this.info.video[0];

            if (video.frameType() == FLVVideoFrameType.KEY_FRAME) {
                long diff = tag.timestamp() - info.lastKeyFrame;
                info.lastKeyFrame = tag.timestamp();
                info.keyFrameInterval = (int) (diff / 1000);
            }

            info.bitrate.sample(video.size(), tag.timestamp());

            if (video.isSequenceHeader() || info.needsUpdate()) {
                // Since we do not support the ex video payload, we rely on the update interval
                // to keep us up-to-date. TODO :^)
                update("v:0", info);
            }
        } else if (tag.data() instanceof FLVStandardAudioTagData astd) {
            AudioStreamInfo info = this.info.audio[0];

            info.bitrate.sample(astd.size(), tag.timestamp());

            if (astd.isSequenceHeader() || info.needsUpdate()) {
                update("a:0", info);
            }
        } else if (tag.data() instanceof FLVExAudioTagData aex) {
            for (FLVExAudioTrack track : aex.tracks()) {
                AudioStreamInfo info = this.info.audio[track.id()];

                info.bitrate.sample(track.data().size(), tag.timestamp());

                if (aex.isSequenceHeader() || info.needsUpdate()) {
                    update("a:" + track.id(), info);
                }
            }
        }
    }

    @Override
    public void onSequence(Session session, FLVSequence seq) {
        for (FLVTag tag : seq.tags()) {
            this.process(tag);
        }
    }

    @Override
    public void onTag(Session session, FLVTag tag) {
        this.process(tag);
    }

    @Override
    public void onClose(Session session) {} // NOOP

    @Override
    public Type type() {
        return null;
    }

    /* https://github.com/videolan/vlc/blob/master/src/misc/fourcc_list.h */
    private static @Nullable String flvToFourCC(FLVVideoCodec codec) {
        // @formatter:off
        return switch (codec) {
            case H264 ->          "avc1";
            case ON2_VP6 ->       "vp6f";
            case ON2_VP6_ALPHA -> "vp6a";
            case SCREEN ->        "fsv1";
            case SCREEN_2 ->      "fsv2";
            case SORENSON_H263 -> "flv1";
            case JPEG -> null;
            default -> null;
        };
        // @formatter:on
    }

    /* https://github.com/videolan/vlc/blob/master/src/misc/fourcc_list.h */
    private static @Nullable String flvToFourCC(FLVAudioFormat format) {
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
            case DEVICE_SPECIFIC -> null;
            default -> null;
        };
        // @formatter:on
    }

    private void update(String map, StreamInfo toUpdate) {
        toUpdate.updating = true;
        if (!FF.canUseProbe) return;

        try {
            this.session.addAsyncListener(new FFprobeSessionListener(map, toUpdate));
        } catch (IOException e) {
            if (Quark.DEBUG) {
                e.printStackTrace();
            }
        }

        // We intentionally break the state and leave updating set to true, otherwise
        // we'd go in an infinite loop of updates :P
    }

    private class FFprobeSessionListener extends FLVProcessSessionListener {

        public FFprobeSessionListener(String map, StreamInfo toUpdate) throws IOException {
            super(
                Redirect.PIPE, Redirect.INHERIT,
                "ffprobe",
                "-hide_banner",
                "-v", "quiet",
                "-print_format", "json",
                "-show_entries", "stream=pix_fmt",
                "-show_streams",
                "-select_streams", map,
                "-f", "flv",
                "-"
            );

            Thread.ofVirtual().name("Stream Probe", 0).start(() -> {
                try {
                    // Wait for the result, then copy it.
                    String str = StreamUtil.toString(this.stdout(), StandardCharsets.UTF_8).replace("\r", "").replace("\n", "").replace(" ", "");

                    JsonObject json = Rson.DEFAULT.fromJson(str, JsonObject.class);

                    JsonArray streams = json.getArray("streams");
                    if (!streams.isEmpty()) {
                        JsonObject first = streams.getObject(0);
                        toUpdate.apply(first);
                    }
                } catch (IOException e) {
                    if (Quark.DEBUG) {
                        e.printStackTrace();
                    }
                } finally {
                    session.removeListener(this);
                }
            });
        }

        @Override
        public Type type() {
            return null;
        }

    }

}
