package co.casterlabs.quark.core.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.flv4j.flv.tags.audio.FLVAudioFormat;
import co.casterlabs.flv4j.flv.tags.video.FLVVideoCodec;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonObject;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class CodecUtil {

    /* https://github.com/videolan/vlc/blob/master/src/misc/fourcc_list.h */
    public static @Nullable String flvToFourCC(FLVVideoCodec codec) {
        if (codec == null) return null;
        // @formatter:off
        return switch (codec) {
            case H264 ->          "avc1";
            case ON2_VP6 ->       "vp6f";
            case ON2_VP6_ALPHA -> "vp6a";
            case SCREEN ->        "fsv1";
            case SCREEN_2 ->      "fsv2";
            case SORENSON_H263 -> "flv1";
            case NS_HEVC ->       "hvc1";
            case NS_MPEG4 ->      "mp4v";
            case NS_REALH263 ->   "h263";
            case JPEG -> null;
            default -> null;
        };
        // @formatter:on
    }

    /* https://github.com/videolan/vlc/blob/master/src/misc/fourcc_list.h */
    public static @Nullable String flvToFourCC(FLVAudioFormat format) {
        if (format == null) return null;
        // @formatter:off
        return switch (format) {
            case AAC ->                  "mp4a";
            case ADPCM ->                "swfa";
            case G711_ALAW ->            "alaw";
            case G711_MULAW ->           "ulaw";
            case LPCM_LE ->              "lpcm";
            case MP3, MP3_8 ->           "mp3 ";
            case SPEEX ->                "spx ";
            case LPCM_PLATFORM_ENDIAN -> "lpcm";
            case NELLYMOSER, NELLYMOSER_16_MONO, NELLYMOSER_8_MONO -> "nmos";
            case NS_MP2 ->               "mp2a";
            case NS_OPUS ->              "Opus";
            case DEVICE_SPECIFIC -> null;
            default -> null;
        };
        // @formatter:on
    }

    // Normalize FFMpeg's codec names to FourCC.
    // Worth noting that for codecs already handled by flvToFourCC called by
    // _CodecsSessionListener, this function will never get called. Same for ExAudio
    // codecs since the fourCC value is already provided by the FLVExAudioTrack.
    /* https://github.com/videolan/vlc/blob/master/src/misc/fourcc_list.h */
    public static String normalizeToFourCC(String codecName) {
        if (codecName == null) return null;
        // @formatter:off
        return switch (codecName) {
            // MPEG-1
            case "mpeg1video" -> "mp1v";
            
            // MPEG-2
            case "mpeg2video" -> "mp2v";

            // MPEG-4 Part 2
            case "mpeg4" -> "mp4v";

            // Microsoft MPEG-4
            case "msmpeg4v1" -> "mp41";
            case "msmpeg4v2" -> "mp42";
            case "msmpeg4v3" -> "mp43";

            // H.261
            case "h261" -> "h261";

            // H.263
            case "h263", "h263p", "h263i" -> "h263";
            case "flv1" -> "flv1"; // Sorenson H.263

            // H.264 / AVC
            case "h264" -> "avc1";

            // H.265 / HEVC
            case "hevc" -> "hvc1";

            // H.266 / VVC
            case "vvc" -> "vvc1";

            // AV1
            case "av1" -> "av01";

            // VP8 & VP9
            case "vp8" -> "vp08";
            case "vp9" -> "vp09";

            // Theora
            case "theora", "theo" -> "theo";

            // ---------------------------------------------
            
            // AAC
            case "aac", "aac_latm" -> "mp4a";

            // MPEG audio
            case "mp3", "mp3adu" -> "mp3 ";
            case "mp2" -> "mp2a";
            case "mp1" -> "mp1 ";

            // AC-3
            case "ac3" -> "ac-3";
            case "eac3" -> "ec-3";

            // ALAC
            case "alac" -> "alac";

            // Opus
            case "opus" -> "Opus";

            // Vorbis
            case "vorbis" -> "vorb";

            // FLAC
            case "flac" -> "fLaC";

            // Speex
            case "speex" -> "spx ";

            // PCM
            case "pcm_s16le" -> "s16l";
            case "pcm_s16be" -> "s16b";
            case "pcm_s24le" -> "s24l";
            case "pcm_s24be" -> "s24b";
            case "pcm_s32le" -> "s32l";
            case "pcm_s32be" -> "s32b";
            case "pcm_s64le" -> "s64l";
            case "pcm_s64be" -> "s64b";
            case "pcm_f32le", "pcm_f32be" -> "fl32";
            case "pcm_f64le", "pcm_f64be" -> "fl64";
            case "pcm_u8" -> "raw ";
            case "pcm_alaw" -> "alaw";
            case "pcm_mulaw" -> "ulaw";

            // ---------------------------------------------
            
            // Subtitles
            case "subrip", "srt" -> "srt ";
            case "ass" -> "ass ";
            case "ssa" -> "ssa ";
            case "webvtt" -> "wvtt";
            case "mov_text" -> "tx3g";
            case "dvd_subtitle" -> "dvb ";
            case "dvb_subtitle" -> "dvbs";
            case "hdmv_pgs_subtitle" -> "pgss";
            case "xsub" -> "xsub";
            case "eia_608" -> "c608";

            // Data / timed metadata
            case "timed_id3" -> "ID3 ";
            case "klv" -> "KLVA";
            case "bin_data" -> "bin ";

            default -> codecName; // This sucks.
        };
        // @formatter:on
    }

    public static String rfc6381(String fourCC, JsonObject ff) {
        if (fourCC == null) return null;

        try {
            return switch (fourCC) {
                case "avc1" -> rfc6381Avc(fourCC, ff);
                case "hvc1" -> rfc6381Hevc(fourCC, ff);
                case "av01" -> rfc6381Av1(ff);
                case "vp08" -> rfc6381Vpx("vp08", ff);
                case "vp09" -> rfc6381Vpx("vp09", ff);
                case "mp4a" -> rfc6381Aac(ff);

                // No parameters in RFC 6381 for these - the fourCC-ish literal *is* the string
                case "ac-3" -> "ac-3";
                case "ec-3" -> "ec-3";
                case "Opus" -> "opus";
                case "fLaC" -> "flac";

                default -> fourCC;
            };
        } catch (Throwable t) {
            FastLogger.logStatic(LogLevel.WARNING, "Failed to generate RFC 6381 string for codec: " + fourCC + " " + ff + " " + t, t);
            return fourCC;
        }
    }

    /*
     * AVC RFC 6381 format is:
     * avc1.PPCCLL
     *
     * PP = profile_idc
     * CC = constraint flags
     * LL = level_idc
     */
    private static String rfc6381Avc(String fourCC, JsonObject ff) {
        String profile = looseString(ff.get("profile"), "Baseline");
        int level = looseNumber(ff.get("level"), 30).intValue(); // ffprobe already reports level*10 (raw level_idc)

        int profileIdc = switch (normalize(profile)) {
            case "baseline", "constrained baseline" -> 66; // 0x42
            case "main" -> 77;                             // 0x4D
            case "extended" -> 88;                         // 0x58
            case "high" -> 100;                            // 0x64
            case "high 10", "high 10 intra" -> 110;        // 0x6E
            case "high 4 2 2", "high 4 2 2 intra" -> 122;  // 0x7A
            case "high 4 4 4", "high 4 4 4 predictive", "high 4 4 4 intra" -> 244; // 0xF4
            case "cavlc 4 4 4" -> 44;                      // 0x2C
            case "scalable baseline" -> 83;                // 0x53
            case "scalable high" -> 86;                    // 0x56
            case "multiview high" -> 118;                  // 0x76
            case "stereo high" -> 128;                     // 0x80
            default -> 66;
        };

        // constraint_set flags aren't in ffprobe's generic profile string — only
        // "Constrained Baseline" tells us anything definitive (constraint_set1=1).
        // Everything else defaults unconstrained; override via "constraintFlags" if
        // you ever get it from an SPS parse.
        int defaultConstraints = normalize(profile).equals("constrained baseline") ? 0xC0 : 0x00;
        int constraints = looseNumber(ff.get("constraintFlags"), defaultConstraints).intValue();

        return String.format(Locale.ROOT, "avc1.%02X%02X%02X", profileIdc, constraints, level);
    }

    /*
     * HEVC RFC 6381 format is:
     * hvc1.<profile>.<compat>.<tier><level>[.<constraints>]
     *
     * profile: general_profile_idc
     * compat: general_profile_compatibility_flags
     * tier: general_tier_flag (0=Main, 1=High)
     * level: general_level_idc
     * constraints: general_constraint_indicator_flags (6 bytes)
     */
    private static String rfc6381Hevc(String fourCC, JsonObject ff) {
        String profile = looseString(ff.get("profile"), "Main");
        int level = looseNumber(ff.get("level"), 93).intValue(); // ffprobe reports raw general_level_idc (level*30)

        int profileIdc = switch (normalize(profile)) {
            case "main" -> 1;
            case "main 10" -> 2;
            case "main still picture", "main still" -> 3;
            case "rext", "range extensions" -> 4;
            case "high throughput" -> 5;
            case "multiview main" -> 6;
            case "3d main" -> 8;
            case "screen extended content", "screen content coding", "scc" -> 9;
            default -> 1;
        };

        // ffprobe doesn't expose tier as its own field — not derivable without an SPS
        // parse, so default to Main tier unless the caller has stashed it separately.
        String tier = looseString(ff.get("tier"), "main");
        String tierChar = normalize(tier).equals("high") ? "H" : "L";

        // Real general_profile_compatibility_flags need the SPS too. Approximation:
        // flag just this stream's own profile bit, which any conformant bitstream
        // sets at minimum. Override via "profileCompatibility" if you ever parse it.
        long compat = looseNumber(ff.get("profileCompatibility"), 1L << profileIdc).longValue();
        String compatHex = Long.toHexString(compat).toUpperCase(Locale.ROOT);

        String base = String.format(
            Locale.ROOT, "%s.%d.%s.%s%d",
            fourCC, profileIdc, compatHex, tierChar, level
        );

        long constraints = looseNumber(ff.get("constraintFlags"), 0).longValue();
        if (constraints == 0) return base;

        StringBuilder sb = new StringBuilder(base);
        for (int i = 5; i >= 0; i--) {
            int b = (int) ((constraints >> (i * 8)) & 0xFF);
            sb.append('.').append(Integer.toHexString(b).toUpperCase(Locale.ROOT));
        }
        return sb.toString();
    }

    /*
     * AV1 RFC 6381 format is:
     * av01.<profile>.<level><tier>.<depth>[.<colorPrimaries>.<transferCharacteristics>.<matrixCoefficients>[.<chromaSubsampling>]]
     *
     * profile: seq_profile
     * level: seq_level_idx
     * tier: seq_tier (0=Main, 1=High)
     * depth: bits_per_raw_sample
     * colorPrimaries: color_primaries (optional)
     * transferCharacteristics: transfer_characteristics (optional)
     * matrixCoefficients: matrix_coefficients (optional)
     * chromaSubsampling: chroma_subsampling (optional)
     */
    private static String rfc6381Av1(JsonObject ff) {
        String profile = looseString(ff.get("profile"), "Main");
        String tier = looseString(ff.get("tier"), "Main");
        int level = looseNumber(ff.get("level"), 4).intValue();        // seq_level_idx
        int depth = looseNumber(ff.get("bits_per_raw_sample"), 8).intValue();

        int profileNum = switch (normalize(profile)) {
            case "main" -> 0;
            case "high" -> 1;
            case "professional" -> 2;
            default -> 0;
        };
        String tierChar = normalize(tier).equals("high") ? "H" : "M";

        return String.format(Locale.ROOT, "av01.%d.%02d%s.%02d", profileNum, level, tierChar, depth);
    }

    /*
     * VP8/VP9 RFC 6381 format is:
     * vp08/vp09.PP.LL.DD
     *
     * PP = profile (0-3)
     * LL = level (0-63)
     * DD = bits_per_raw_sample (8-12)
     */
    private static String rfc6381Vpx(String base, JsonObject ff) {
        String profile = looseString(ff.get("profile"), "Profile 0");
        int level = looseNumber(ff.get("level"), 10).intValue();
        int depth = looseNumber(ff.get("bits_per_raw_sample"), 8).intValue();

        // ffmpeg reports VP9 profile as "Profile 0".."Profile 3" (VP8 rarely sets this
        // at all, so the "Profile 0" default covers it too).
        Matcher m = Pattern.compile("(\\d+)").matcher(profile);
        int profileNum = m.find() ? Integer.parseInt(m.group(1)) : 0;

        return String.format(Locale.ROOT, "%s.%02d.%02d.%02d", base, profileNum, level, depth);
    }

    /*
     * AAC RFC 6381 format is:
     * mp4a.40.<objectType>
     *
     * objectType: MPEG-4 Audio Object Type (1-42)
     */
    private static String rfc6381Aac(JsonObject ff) {
        String profile = looseString(ff.get("profile"), "LC"); // default: AAC-LC

        int objectType = switch (normalize(profile)) {
            case "main", "aac main" -> 1;
            case "lc", "aac lc", "low complexity" -> 2;
            case "ssr", "aac ssr" -> 3;
            case "ltp", "aac ltp" -> 4;
            case "he aac", "heaac", "aac he", "aac lc sbr", "lc sbr" -> 5;
            case "he aac v2", "he aacv2", "heaacv2", "aac he v2", "aac lc sbr ps", "lc sbr ps" -> 29;
            default -> 2;
        };

        return String.format(Locale.ROOT, "mp4a.40.%d", objectType);
    }

    private static Number looseNumber(JsonElement el, Number def) {
        if (el == null) return def;

        if (el.isJsonNumber()) {
            return el.getAsNumber();
        }

        if (el.isJsonString()) {
            try {
                return Double.parseDouble(el.getAsString());
            } catch (NumberFormatException e) {
                FastLogger.logStatic(LogLevel.WARNING, "Failed to parse number from string: " + e, e);
            }
        }

        return def;
    }

    private static String looseString(JsonElement el, String def) {
        if (el == null) return def;

        if (el.isJsonString()) {
            return el.getAsString();
        }

        return def;
    }

    private static String normalize(@Nullable String profile) {
        if (profile == null) {
            return "";
        }

        return profile
            .trim()
            .toLowerCase(Locale.ROOT)
            .replace('_', ' ')
            .replace('-', ' ')
            .replace('+', ' ')
            .replace(':', ' ')
            .replaceAll("\\s+", " ");
    }

}
