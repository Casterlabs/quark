package co.casterlabs.quark.core.session;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadFactory;

import co.casterlabs.commons.io.streams.StreamUtil;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.flv4j.flv.tags.FLVTagType;
import co.casterlabs.flv4j.flv.tags.audio.FLVStandardAudioTagData;
import co.casterlabs.flv4j.flv.tags.audio.ex.FLVExAudioTagData;
import co.casterlabs.flv4j.flv.tags.audio.ex.FLVExAudioTrack;
import co.casterlabs.flv4j.flv.tags.video.FLVStandardVideoTagData;
import co.casterlabs.flv4j.flv.tags.video.FLVVideoFrameType;
import co.casterlabs.flv4j.flv.tags.video.ex.FLVExVideoFrameType;
import co.casterlabs.flv4j.flv.tags.video.ex.FLVExVideoTagData;
import co.casterlabs.flv4j.flv.tags.video.ex.FLVExVideoTrack;
import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.Threads;
import co.casterlabs.quark.core.session.info.SessionInfo;
import co.casterlabs.quark.core.session.info.StreamInfo;
import co.casterlabs.quark.core.session.info.StreamInfo.AudioStreamInfo;
import co.casterlabs.quark.core.session.info.StreamInfo.VideoStreamInfo;
import co.casterlabs.quark.core.session.listeners.FLVProcessSessionListener;
import co.casterlabs.quark.core.session.listeners.StreamFilter;
import co.casterlabs.quark.core.util.CodecUtil;
import co.casterlabs.quark.core.util.FF;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class _CodecsSessionListener extends SessionListener {
    private static final ThreadFactory THREAD_FACTORY = Threads.lightIo("Codec Stream Probe");

    private final Session session;
    private final SessionInfo info;

    private void process(FLVTag tag) {
        if (tag.type() == FLVTagType.SCRIPT) return; // ignore.

        if (tag.data() instanceof FLVStandardVideoTagData vstd) {
            if (this.info.video.length == 0) {
                this.info.video = new VideoStreamInfo[] {
                        new VideoStreamInfo(0, CodecUtil.flvToFourCC(vstd.codec()))
                };
            } else if (this.info.video[0] == null) {
                this.info.video[0] = new VideoStreamInfo(0, CodecUtil.flvToFourCC(vstd.codec()));
            }
        } else if (tag.data() instanceof FLVExVideoTagData vex) {
            for (FLVExVideoTrack track : vex.tracks()) {
                int trackId = track.id();

                if (this.info.video.length <= trackId) {
                    int newLength = Math.max(this.info.video.length + 1, trackId + 1);
                    VideoStreamInfo[] newVideo = new VideoStreamInfo[newLength];
                    System.arraycopy(this.info.video, 0, newVideo, 0, this.info.video.length);
                    newVideo[trackId] = new VideoStreamInfo(trackId, track.codec().string());
                    this.info.video = newVideo;
                } else if (this.info.video[trackId] == null) {
                    this.info.video[trackId] = new VideoStreamInfo(trackId, track.codec().string());
                }
            }
        } else if (tag.data() instanceof FLVStandardAudioTagData astd) {
            if (this.info.audio.length == 0) {
                this.info.audio = new AudioStreamInfo[] {
                        new AudioStreamInfo(0, CodecUtil.flvToFourCC(astd.format()))
                };
            } else if (this.info.audio[0] == null) {
                this.info.audio[0] = new AudioStreamInfo(0, CodecUtil.flvToFourCC(astd.format()));
            }
        } else if (tag.data() instanceof FLVExAudioTagData aex) {
            for (FLVExAudioTrack track : aex.tracks()) {
                int trackId = track.id();

                if (this.info.audio.length <= trackId) {
                    int newLength = Math.max(this.info.audio.length + 1, trackId + 1);
                    AudioStreamInfo[] newAudio = new AudioStreamInfo[newLength];
                    System.arraycopy(this.info.audio, 0, newAudio, 0, this.info.audio.length);
                    newAudio[trackId] = new AudioStreamInfo(trackId, track.codec().string());
                    this.info.audio = newAudio;
                } else if (this.info.audio[trackId] == null) {
                    this.info.audio[trackId] = new AudioStreamInfo(trackId, track.codec().string());
                }
            }
        }

        if (tag.data() instanceof FLVStandardVideoTagData video) {
            VideoStreamInfo info = this.info.video[0];

            if (video.frameType() == FLVVideoFrameType.KEY_FRAME) {
                long diff = tag.timestamp() - info.lastKeyFrame;
                info.lastKeyFrame = tag.timestamp();
                info.keyFrameInterval = (int) (diff / 1000);
            }

            info.bitrate.sample(video.size(), tag.timestamp());

            if (video.isSequenceHeader() || info.needsUpdate()) {
                update("v:0", info);
            }
        } else if (tag.data() instanceof FLVExVideoTagData vex) {
            for (FLVExVideoTrack track : vex.tracks()) {
                VideoStreamInfo info = this.info.video[track.id()];

                if (vex.frameType() == FLVExVideoFrameType.KEY_FRAME || vex.frameType() == FLVExVideoFrameType.GENERATED_KEY_FRAME) {
                    long diff = tag.timestamp() - info.lastKeyFrame;
                    info.lastKeyFrame = tag.timestamp();
                    info.keyFrameInterval = (int) (diff / 1000);
                }

                info.bitrate.sample(track.data().size(), tag.timestamp());

                if (vex.isSequenceHeader() || info.needsUpdate()) {
                    update("v:" + track.id(), info);
                }
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
    public String type() {
        return null;
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

    @Override
    protected void onClose0(Session session) {} // NOOP, resource cleanup is below.

    private class FFprobeSessionListener extends FLVProcessSessionListener {

        public FFprobeSessionListener(String map, StreamInfo toUpdate) throws IOException {
            super(
                StreamFilter.ALL_AUDIO, // Let FFmpeg handle the selection
                Redirect.PIPE, Redirect.INHERIT,
                "ffprobe",
                "-hide_banner",
                "-v", "quiet",
                "-strict", "0",
                "-print_format", "json",
                "-show_streams",
                "-select_streams", map,
                "-f", "flv",
                "-"
            );

            THREAD_FACTORY.newThread(() -> {
                try {
                    // Wait for the result, then copy it.
                    String str = StreamUtil.toString(this.stdout(), StandardCharsets.UTF_8).replace("\r", "").replace("\n", "").replace(" ", "");
                    if (str.isEmpty()) return;

                    JsonObject json = Rson.DEFAULT.fromJson(str, JsonObject.class);

                    JsonArray streams = json.getArray("streams");
                    if (!streams.isEmpty()) {
                        JsonObject first = streams.getObject(0);
                        toUpdate.apply(first);
                    }
                } catch (IOException | StringIndexOutOfBoundsException e) {
                    if (Quark.DEBUG) {
                        e.printStackTrace();
                    }
                } finally {
                    session.removeListener(this);
                }
            }).start();
        }

        @Override
        public String type() {
            return null;
        }

    }

}
