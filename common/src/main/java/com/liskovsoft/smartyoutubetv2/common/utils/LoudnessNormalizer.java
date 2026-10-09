package com.liskovsoft.smartyoutubetv2.common.utils;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * HearthTube: "Even out volume" (the Auto volume adjustment setting). Every video plays at about the same level, the
 * way youtube.com does it.<br/>
 * YouTube measures each video and puts how far it is over its -14 LUFS reference in the player response
 * (playerConfig.audioConfig.loudnessDb). youtube.com turns a video down by that much: min(1, 10^(-loudnessDb/20)).<br/>
 * HearthTube does the same, and also turns quiet videos up, by at most {@link #MAX_LIFT_DB}: on a TV a talk or an old
 * song would otherwise play up to 10 dB under everything else. The lift is LoudnessEnhancer's (VolumeBooster), which is
 * gain into a fast peak compressor (knee at -8 dBFS, 7:1, 1 ms attack, 15 ms release, then a hard clip), stereo only.
 * AOSP calls it mild up to 2x (+6 dB); SmartTube ran it at 6x on every video. Here it's off unless a video needs a lift.
 */
public final class LoudnessNormalizer {
    /** The most a quiet video is turned up: 2x, the top of LoudnessEnhancer's mild range */
    public static final float MAX_LIFT_DB = 6f;
    /** The most a loud video is turned down (1/4), so a wrong value can't mute a video. Loud music sits near +6 dB */
    public static final float MAX_CUT_DB = 12f;
    /** SmartTube plays Shorts at half volume. Kept for Shorts without a loudness: a measured Short needs no rule */
    public static final float SHORTS_VOLUME = 0.5f;
    /** 20 * log10(1.95): MediaServiceCore's getVolumeLevel() stops following the loudness above this */
    static final float HEURISTIC_CLAMP_DB = 5.8f;
    // Where MediaServiceCore keeps the loudnessDb it parsed (both private): LoudnessNormalizerTest pins them
    private static final String LEGACY_FIELD = "mLoudnessDb"; // YouTubeMediaItemFormatInfo
    private static final String INNERTUBE_GETTER = "get_loudnessDb"; // MediaItemFormatInfoImpl, a Kotlin lazy

    private LoudnessNormalizer() {
    }

    /**
     * The volume to play a video at, for the player's setVolume(): 1 plays it as is, and above 1 asks the booster for a
     * lift (the player itself goes up to 1).
     *
     * @param userVolume the Player volume setting, 1 is 100%
     * @param normalize "Even out volume" is on
     * @param loudnessDb the video's loudness (see {@link #loudnessDbOf}), NaN when there's none
     */
    public static float getVolume(float userVolume, boolean normalize, float loudnessDb, boolean isLive, boolean isShorts) {
        if (!normalize) {
            return isShorts ? userVolume * SHORTS_VOLUME : userVolume; // SmartTube's
        }

        // Over 100%, the booster set up with the player does the user's part (SmartTube's), so only the video's is here
        float volume = Math.min(userVolume, 1f) * getGain(loudnessDb, isLive);

        return isShorts && !hasLoudness(loudnessDb, isLive) ? volume * SHORTS_VOLUME : volume;
    }

    /**
     * youtube.com's gain, 10^(-loudnessDb/20), from -{@link #MAX_CUT_DB} to +{@link #MAX_LIFT_DB}. 1 for a live stream
     * or when there's no loudness.
     */
    public static float getGain(float loudnessDb, boolean isLive) {
        if (!hasLoudness(loudnessDb, isLive)) {
            return 1f;
        }

        float gainDb = Math.max(-MAX_CUT_DB, Math.min(MAX_LIFT_DB, -loudnessDb));

        return (float) Math.pow(10, gainDb / 20);
    }

    /** LoudnessEnhancer's target gain, in millibels, for a volume over 1: at most {@link #MAX_LIFT_DB}. 0 for none */
    public static int getLiftMb(float volume) {
        if (!(volume > 1f)) {
            return 0;
        }

        return Math.round(Math.min(MAX_LIFT_DB, (float) (20 * Math.log10(volume))) * 100);
    }

    /**
     * The video's loudnessDb, NaN when the response has none (a live stream, say).<br/>
     * MediaServiceCore parses it but only hands out getVolumeLevel(), a heuristic of its own that gives up above
     * +5.8 dB, which is where loud music sits (Lonely Day is +6.0, Baby Shark +7.0). So the parsed value is read from
     * the instance. Should that fail (MediaServiceCore renamed it), it's worked back out of the heuristic instead.
     */
    public static float loudnessDbOf(MediaItemFormatInfo formatInfo) {
        if (formatInfo == null) {
            return Float.NaN;
        }

        Float parsed = readParsedLoudnessDb(formatInfo);
        float loudnessDb = parsed != null ? parsed : loudnessDbFromVolumeLevel(formatInfo.getVolumeLevel());

        return loudnessDb == 0f ? Float.NaN : loudnessDb; // both parsers give 0 when the response has none
    }

    /**
     * Undoes MediaServiceCore's getVolumeLevel(): level = (2 - n) / 2, with n = 10^(loudnessDb/20), except that an n
     * over 1.95 becomes 1.5 and no loudness gives n = 1.<br/>
     * Exact up to +5.8 dB. A level from that clamp (n = 1.5) comes back as +5.8 dB, the least it can stand for (it could
     * be exactly +3.5 dB too, which is unlikely).
     */
    static float loudnessDbFromVolumeLevel(float volumeLevel) {
        double normalLevel = 2.0 - 2.0 * volumeLevel;

        if (!(normalLevel > 0) || Math.abs(normalLevel - 1.0) < 1e-6) {
            return Float.NaN; // n = 1: no loudness (a live stream, say)
        }

        if (Math.abs(normalLevel - 1.5) < 1e-6) {
            return HEURISTIC_CLAMP_DB;
        }

        return (float) (20 * Math.log10(normalLevel));
    }

    private static Float readParsedLoudnessDb(MediaItemFormatInfo formatInfo) {
        Class<?> type = formatInfo.getClass();

        try {
            Field field = type.getDeclaredField(LEGACY_FIELD);
            field.setAccessible(true);
            return field.getFloat(formatInfo);
        } catch (NoSuchFieldException e) {
            // Not the legacy one
        } catch (Exception e) {
            return null;
        }

        try {
            Method getter = type.getDeclaredMethod(INNERTUBE_GETTER);
            getter.setAccessible(true);
            Object value = getter.invoke(formatInfo);
            return value instanceof Number ? ((Number) value).floatValue() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean hasLoudness(float loudnessDb, boolean isLive) {
        return !isLive && !Float.isNaN(loudnessDb) && !Float.isInfinite(loudnessDb);
    }
}
