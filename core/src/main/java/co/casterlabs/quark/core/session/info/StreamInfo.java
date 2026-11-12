package co.casterlabs.quark.core.session.info;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.flv4j.codecs.CodecData;
import co.casterlabs.flv4j.codecs.audio.aac.AACAudioData;
import co.casterlabs.flv4j.codecs.audio.aac.AACAudioSpecificConfig;
import co.casterlabs.flv4j.codecs.audio.aac.AACExAudioData;
import co.casterlabs.flv4j.codecs.audio.flac.FLACExAudioData;
import co.casterlabs.flv4j.codecs.audio.flac.FLACStreamInfo;
import co.casterlabs.flv4j.codecs.audio.mp3.MP3AudioData;
import co.casterlabs.flv4j.codecs.audio.mp3.MP3ExAudioData;
import co.casterlabs.flv4j.codecs.audio.mp3.MP3FrameHeader;
import co.casterlabs.flv4j.codecs.audio.opus.OpusExAudioData;
import co.casterlabs.flv4j.codecs.audio.opus.OpusHead;
import co.casterlabs.flv4j.codecs.video.TransferCharacteristics;
import co.casterlabs.flv4j.codecs.video.av01.AV1CodecConfigurationRecord;
import co.casterlabs.flv4j.codecs.video.av01.AV1ExVideoData;
import co.casterlabs.flv4j.codecs.video.av01.AV1SequenceHeader;
import co.casterlabs.flv4j.codecs.video.avc1.AVCDecoderConfigurationRecord;
import co.casterlabs.flv4j.codecs.video.avc1.AVCNalu;
import co.casterlabs.flv4j.codecs.video.avc1.AVCSPS;
import co.casterlabs.flv4j.codecs.video.avc1.AVCVideoData;
import co.casterlabs.flv4j.codecs.video.hvc1.HEVCDecoderConfigurationRecord;
import co.casterlabs.flv4j.codecs.video.hvc1.HEVCExVideoData;
import co.casterlabs.flv4j.codecs.video.hvc1.HEVCNalu;
import co.casterlabs.flv4j.codecs.video.hvc1.HEVCNaluType;
import co.casterlabs.flv4j.codecs.video.hvc1.HEVCSPS;
import co.casterlabs.flv4j.codecs.video.hvc1.HEVCVideoData;
import co.casterlabs.flv4j.codecs.video.vpx.VPCodecConfigurationRecord;
import co.casterlabs.flv4j.codecs.video.vpx.VPExVideoData;
import co.casterlabs.quark.core.util.BitrateEstimator;
import co.casterlabs.quark.core.util.CodecUtil;
import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rakurai.json.annotating.JsonExclude;
import co.casterlabs.rakurai.json.element.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@JsonClass(exposeAll = true)
public abstract class StreamInfo {
    private static final long UPDATE_INTERVAL = TimeUnit.MINUTES.toMillis(15);

    public final int id;
    public final BitrateEstimator bitrate = new BitrateEstimator();
    public volatile String codec;
    public volatile String rfc6381;

    public @JsonExclude volatile long lastUpdated = 0;
    public @JsonExclude volatile boolean updating = false;

    public abstract void apply(JsonObject ff);

    public abstract boolean probe(CodecData data);

    public boolean needsUpdate() {
        return !updating && System.currentTimeMillis() - this.lastUpdated > UPDATE_INTERVAL;
    }

    @JsonClass(exposeAll = true)
    public static class AudioStreamInfo extends StreamInfo {
        public volatile double sampleRate = -1;
        public volatile int channels = -1;
        public volatile String layout;

        public AudioStreamInfo(int id, @Nullable String codec) {
            super(id);
            this.codec = codec;
        }

        @Override
        public void apply(JsonObject ff) {
            this.lastUpdated = System.currentTimeMillis();
            this.updating = false;

            if (this.codec == null && ff.containsKey("codec_name")) {
                this.codec = CodecUtil.normalizeToFourCC(ff.getString("codec_name"));
            }

            if (this.rfc6381 == null) {
                this.rfc6381 = CodecUtil.rfc6381(this.codec, ff);
            }

            if (ff.containsKey("channels")) {
                this.channels = ff.getNumber("channels").intValue();
            }

            if (ff.containsKey("sample_rate")) {
                this.sampleRate = Double.parseDouble(ff.getString("sample_rate"));
                if (Double.isNaN(this.sampleRate) || Double.isInfinite(this.sampleRate)) {
                    this.sampleRate = -1;
                }
            }

            if (ff.containsKey("channel_layout")) {
                this.layout = ff.getString("channel_layout");
            }
        }

        private void update(AACAudioSpecificConfig config) {
            this.rfc6381 = CodecUtil.rfc6381(config);
            this.sampleRate = config.sampleRate();
            this.channels = config.channelCount();
            this.layout = switch (config.channelConfig()) {
                case 1 -> "mono";
                case 2 -> "stereo";
                case 3 -> "3.0";
                case 4 -> "4.0";
                case 5 -> "5.0";
                case 6 -> "5.1";
                case 7 -> "7.1";
                default -> "unknown";
            };
        }

        private void update(MP3FrameHeader header) {
            boolean isMono = header.channelMode() == 3; // 3 = single channel, otherwise stereo.

            this.rfc6381 = "mp3";
            this.sampleRate = header.sampleRate();
            this.channels = isMono ? 1 : 2;
            this.layout = isMono ? "mono" : "stereo";
        }

        private void update(OpusHead header) {
            this.rfc6381 = "opus";
            this.sampleRate = 48000; // Opus is always 48kHz.
            this.channels = header.channelCount();
            this.layout = "unknown"; // TODO channelMappingFamily
        }

        private void update(FLACStreamInfo info) {
            this.rfc6381 = "flac";
            this.sampleRate = info.sampleRate();
            this.channels = info.channels();
            this.layout = "unknown"; // TODO
        }

        @Override
        public boolean probe(CodecData data) {
            return switch (data) {
                case AACAudioData aac -> {
                    if (aac.packetData() instanceof AACAudioSpecificConfig asc) {
                        this.update(asc);
                        yield true;
                    } else {
                        yield false;
                    }
                }
                case AACExAudioData aac -> {
                    if (aac.packetData() instanceof AACAudioSpecificConfig asc) {
                        this.update(asc);
                        yield true;
                    } else {
                        yield false;
                    }
                }

                case MP3AudioData mp3 -> {
                    this.update(mp3.frames()[0].header());
                    yield true;
                }
                case MP3ExAudioData mp3 -> {
                    this.update(mp3.frames()[0].header());
                    yield true;
                }

                case OpusExAudioData opus -> {
                    if (opus.packetData() instanceof OpusHead head) {
                        this.update(head);
                        yield true;
                    } else {
                        yield false;
                    }
                }

                case FLACExAudioData opus -> {
                    if (opus.packetData() instanceof FLACStreamInfo info) {
                        this.update(info);
                        yield true;
                    } else {
                        yield false;
                    }
                }

                default -> false; // Unknown codec, we can't probe it.
            };
        }

    }

    @JsonClass(exposeAll = true)
    public static class VideoStreamInfo extends StreamInfo {
        public volatile int width = -1;
        public volatile int height = -1;
        public volatile String pixelFormat;
        public volatile String colorSpace;
        public volatile String colorPrimaries;
        public volatile String colorTransfer;
        public volatile String aspectRatio;
        public volatile double frameRate = -1;
        public volatile boolean isLikelyHDR = false;

        public volatile double actualFrameRate = -1;
        public volatile int keyFrameInterval = -1; // seconds

        private @JsonExclude volatile long lastKeyFrame = 0;
        private @JsonExclude volatile long lastFrame = 0;

        public VideoStreamInfo(int id, String codec) {
            super(id);
            this.codec = codec;
        }

        public void updateKeyFrameInterval(long tagTimestamp) {
            long diff = tagTimestamp - this.lastKeyFrame;
            if (diff == 0) return;

            this.lastKeyFrame = tagTimestamp;
            this.keyFrameInterval = (int) (diff / 1000);
        }

        public void actualFrameRate(long tagTimestamp) {
            long diff = tagTimestamp - this.lastFrame;
            if (diff == 0) return;

            this.lastFrame = tagTimestamp;
            this.actualFrameRate = 1000d / diff;
        }

        private void calculateAspectRatio() {
            if (this.width <= 0 || this.height <= 0) {
                return;
            }

            if (this.width == this.height) {
                this.aspectRatio = "1:1";
                return;
            }

            if (this.width < this.height) { // Portrait
                double ratio = (double) this.height / this.width;

                this.aspectRatio = switch ((int) Math.round(ratio * 100)) {
                    case 120 -> "5:6";
                    case 125 -> "4:5";
                    case 133 -> "3:4";
                    case 138 -> "8:11";
                    case 140 -> "5:7";
                    case 150 -> "2:3";
                    case 156 -> "9:14";
                    case 160 -> "10:16";
                    case 167 -> "3:5";
                    case 178 -> "9:16";
                    case 189 -> "9:17";
                    case 190 -> "10:19";
                    case 200 -> "1:2";
                    case 211 -> "9:19";
                    case 217 -> "6:13";
                    case 222 -> "9:20";
                    case 233 -> "9:21";
                    case 240 -> "5:12";
                    case 250 -> "2:5";
                    case 300 -> "1:3";
                    case 356 -> "9:32";
                    case 400 -> "1:4";
                    default -> String.format(Locale.ROOT, "1:%.2f", ratio);
                };
            } else {
                double ratio = (double) this.width / this.height;

                this.aspectRatio = switch ((int) Math.round(ratio * 100)) {
                    case 120 -> "6:5";
                    case 125 -> "5:4";
                    case 133 -> "4:3";
                    case 138 -> "11:8";
                    case 140 -> "7:5";
                    case 150 -> "3:2";
                    case 156 -> "14:9";
                    case 160 -> "16:10";
                    case 167 -> "5:3";
                    case 178 -> "16:9";
                    case 189 -> "17:9";
                    case 190 -> "19:10";
                    case 200 -> "2:1";
                    case 211 -> "19:9";
                    case 217 -> "13:6";
                    case 222 -> "20:9";
                    case 233 -> "21:9";
                    case 240 -> "12:5";
                    case 250 -> "5:2";
                    case 300 -> "3:1";
                    case 356 -> "32:9";
                    case 400 -> "4:1";
                    default -> String.format(Locale.ROOT, "%.2f:1", ratio);
                };
            }
        }

        // Heuristic
        private void isLikelyHDR(TransferCharacteristics transfer) {
            this.isLikelyHDR = switch (transfer) {
                case SMPTE2084, HLG -> true;
                default -> false;
            };
        }

        @Override
        public void apply(JsonObject ff) {
            this.lastUpdated = System.currentTimeMillis();
            this.updating = false;

            if (this.codec == null && ff.containsKey("codec_name")) {
                this.codec = CodecUtil.normalizeToFourCC(ff.getString("codec_name"));
            }

            if (this.rfc6381 == null) {
                this.rfc6381 = CodecUtil.rfc6381(this.codec, ff);
            }

            this.width = ff.getNumber("width").intValue();
            this.height = ff.getNumber("height").intValue();
            this.calculateAspectRatio();

            if (ff.containsKey("pix_fmt")) {
                this.pixelFormat = ff.getString("pix_fmt");
            }
            if (ff.containsKey("color_space")) {
                this.colorSpace = ff.getString("color_space");
            }

            if (ff.containsKey("avg_frame_rate")) {
                this.frameRate = parse(ff.getString("avg_frame_rate"));
            } else if (ff.containsKey("r_frame_rate")) {
                this.frameRate = parse(ff.getString("r_frame_rate"));
            }
            if (Double.isNaN(this.frameRate) || Double.isInfinite(this.frameRate)) {
                this.frameRate = -1;
            }
        }

        private void update(AVCDecoderConfigurationRecord dcr) {
            for (AVCNalu nalu : dcr.sps()) {
                AVCSPS sps = AVCSPS.from(nalu);

                this.rfc6381 = CodecUtil.rfc6381(sps);

                this.width = sps.displayWidth();
                this.height = sps.displayHeight();
                this.calculateAspectRatio();

                this.actualFrameRate = this.frameRate = sps.fps();

                if (sps.bitDepthLuma() != sps.bitDepthChroma()) {
                    this.pixelFormat = "unknown"; // TODO: Handle this case.
                } else {
                    this.pixelFormat = CodecUtil.pixelFormat(sps.chromaSubsampling(), sps.bitDepthChroma());
                }

                this.colorSpace = CodecUtil.colorSpace(sps.matrixCoefficients());
                this.colorPrimaries = CodecUtil.colorPrimaries(sps.colourPrimaries());
                this.colorTransfer = CodecUtil.colorTransfer(sps.transferCharacteristics());
                this.isLikelyHDR(sps.transferCharacteristics());
            }
        }

        private void update(HEVCDecoderConfigurationRecord dcr) {
            for (HEVCNalu nalu : dcr.nalus(HEVCNaluType.SPS)) {
                HEVCSPS sps = HEVCSPS.from(nalu);

                this.rfc6381 = CodecUtil.rfc6381(sps);

                this.width = sps.displayWidth();
                this.height = sps.displayHeight();
                this.calculateAspectRatio();

                this.actualFrameRate = this.frameRate = sps.fps();

                if (sps.bitDepthLuma() != sps.bitDepthChroma()) {
                    this.pixelFormat = "unknown"; // TODO: Handle this case.
                } else {
                    this.pixelFormat = CodecUtil.pixelFormat(sps.chromaSubsampling(), sps.bitDepthChroma());
                }

                this.colorSpace = CodecUtil.colorSpace(sps.matrixCoefficients());
                this.colorPrimaries = CodecUtil.colorPrimaries(sps.colourPrimaries());
                this.colorTransfer = CodecUtil.colorTransfer(sps.transferCharacteristics());
                this.isLikelyHDR(sps.transferCharacteristics());
            }
        }

        private void update(AV1CodecConfigurationRecord ccr) {
            this.rfc6381 = CodecUtil.rfc6381(ccr);

            this.pixelFormat = CodecUtil.pixelFormat(ccr.chromaSubsampling(), ccr.bitDepth());

            for (AV1SequenceHeader seq : ccr.sequenceHeaders()) {
                this.width = seq.maxFrameWidth();
                this.height = seq.maxFrameHeight();
                this.calculateAspectRatio();

                this.actualFrameRate = this.frameRate = seq.frameRate();

                this.colorSpace = CodecUtil.colorSpace(seq.matrixCoefficients());
                this.colorPrimaries = CodecUtil.colorPrimaries(seq.colourPrimaries());
                this.colorTransfer = CodecUtil.colorTransfer(seq.transferCharacteristics());
                this.isLikelyHDR(seq.transferCharacteristics());
            }
        }

        private void update(VPCodecConfigurationRecord ccr) {
            this.rfc6381 = CodecUtil.rfc6381(this.codec, ccr);

            this.pixelFormat = CodecUtil.pixelFormat(ccr.chromaSubsampling(), ccr.bitDepth());

            this.colorSpace = CodecUtil.colorSpace(ccr.matrixCoefficients());
            this.colorPrimaries = CodecUtil.colorPrimaries(ccr.colourPrimaries());
            this.colorTransfer = CodecUtil.colorTransfer(ccr.transferCharacteristics());
            this.isLikelyHDR(ccr.transferCharacteristics());
        }

        @Override
        public boolean probe(CodecData data) {
            return switch (data) {
                case AVCVideoData avc -> {
                    if (avc.packetData() instanceof AVCDecoderConfigurationRecord dcr) {
                        this.update(dcr);
                        yield true;
                    } else {
                        yield false;
                    }
                }

                case HEVCVideoData hevc -> {
                    if (hevc.packetData() instanceof HEVCDecoderConfigurationRecord dcr) {
                        this.update(dcr);
                        yield true;
                    } else {
                        yield false;
                    }
                }
                case HEVCExVideoData hevc -> {
                    if (hevc.packetData() instanceof HEVCDecoderConfigurationRecord dcr) {
                        this.update(dcr);
                        yield true;
                    } else {
                        yield false;
                    }
                }

                case AV1ExVideoData av1 -> {
                    if (av1.packetData() instanceof AV1CodecConfigurationRecord ccr) {
                        this.update(ccr);
                        yield true;
                    } else {
                        yield false;
                    }
                }

                case VPExVideoData vp -> {
                    if (vp.packetData() instanceof VPCodecConfigurationRecord ccr) {
                        this.update(ccr);
                        yield false; // We currently can't pull out enough information from VPx to determine the
                                     // width/height/framerate, so we fallback to FFProbe for those.
                    } else {
                        yield false;
                    }
                }

                default -> false; // Unknown codec, we can't probe it.
            };
        }

    }

    private static double parse(String timebase) {
        String[] split = timebase.split("/");

        if (split.length == 1) {
            return Double.parseDouble(split[0]);
        } else {
            double numerator = Double.parseDouble(split[0]);
            double denominator = Double.parseDouble(split[1]);
            return numerator / denominator;
        }
    }

}
