package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.youtubeapi.innertube.impl.MediaItemFormatInfoImpl;
import com.liskovsoft.youtubeapi.innertube.models.PlayerResult;
import com.liskovsoft.youtubeapi.service.data.YouTubeMediaItemFormatInfo;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

/** Loudness values are real ones, from the player responses of the videos named */
public class LoudnessNormalizerTest {
    private static final float DELTA = 0.001f;
    private static final float LONELY_DAY = 5.99f; // System Of A Down - Lonely Day
    private static final float ENTER_SANDMAN = 5.79f; // Metallica
    private static final float RICK_ASTLEY = 0.99f; // Never Gonna Give You Up
    private static final float TED_TALK = -3.68f; // Do schools kill creativity?
    private static final float REM = -9.25f; // R.E.M. - Losing My Religion
    private static final float WHEELS_ON_THE_BUS_SHORT = 4.91f; // CoComelon Short
    private static final float NO_LOUDNESS = Float.NaN;

    private static float volume(float loudnessDb) {
        return LoudnessNormalizer.getVolume(1f, true, loudnessDb, false, false);
    }

    private static float db(float gain) {
        return (float) (20 * Math.log10(gain));
    }

    @Test
    public void loudVideoIsTurnedDownByItsLoudness() {
        assertEquals(-5.99f, db(volume(LONELY_DAY)), DELTA);
        assertEquals(0.502f, volume(LONELY_DAY), DELTA);
        assertEquals(-0.99f, db(volume(RICK_ASTLEY)), DELTA);
    }

    @Test
    public void quietVideoIsTurnedUp() {
        assertEquals(3.68f, db(volume(TED_TALK)), DELTA);
        assertEquals(1.527f, volume(TED_TALK), DELTA);
    }

    @Test
    public void liftStopsAt6dB() {
        assertEquals(LoudnessNormalizer.MAX_LIFT_DB, db(volume(REM)), DELTA);
        assertEquals(1.995f, volume(REM), DELTA);
    }

    @Test
    public void cutStopsAt12dB() {
        assertEquals(-LoudnessNormalizer.MAX_CUT_DB, db(volume(20f)), DELTA);
        assertEquals(0.251f, volume(20f), DELTA);
    }

    @Test
    public void noLoudnessPlaysAtTheUsersVolume() {
        assertEquals(1f, volume(NO_LOUDNESS), 0);
        assertEquals(0.7f, LoudnessNormalizer.getVolume(0.7f, true, NO_LOUDNESS, false, false), 0);
        assertEquals(1f, volume(Float.POSITIVE_INFINITY), 0);
    }

    @Test
    public void liveStreamPlaysAtTheUsersVolume() {
        assertEquals(1f, LoudnessNormalizer.getVolume(1f, true, LONELY_DAY, true, false), 0);
        assertEquals(0.6f, LoudnessNormalizer.getVolume(0.6f, true, REM, true, false), 0);
    }

    @Test
    public void usersVolumeScalesTheVideosGain() {
        assertEquals(0.251f, LoudnessNormalizer.getVolume(0.5f, true, LONELY_DAY, false, false), DELTA);
        // At 50% a quiet video's lift just brings it back to 100%: no booster needed
        assertEquals(0.998f, LoudnessNormalizer.getVolume(0.5f, true, REM, false, false), DELTA);
        // Over 100% the booster set up with the player does the user's part
        assertEquals(0.502f, LoudnessNormalizer.getVolume(1.5f, true, LONELY_DAY, false, false), DELTA);
    }

    @Test
    public void offPlaysAtTheUsersVolumeLikeSmartTube() {
        assertEquals(1f, LoudnessNormalizer.getVolume(1f, false, LONELY_DAY, false, false), 0);
        assertEquals(0.8f, LoudnessNormalizer.getVolume(0.8f, false, REM, false, false), 0);
        assertEquals(1.5f, LoudnessNormalizer.getVolume(1.5f, false, REM, false, false), 0);
        assertEquals(0.4f, LoudnessNormalizer.getVolume(0.8f, false, LONELY_DAY, false, true), 0); // Shorts at half
    }

    @Test
    public void measuredShortIsNormalizedNotHalved() {
        float short_ = LoudnessNormalizer.getVolume(1f, true, WHEELS_ON_THE_BUS_SHORT, false, true);
        assertEquals(-4.91f, db(short_), DELTA);
        assertEquals(volume(WHEELS_ON_THE_BUS_SHORT), short_, 0);
    }

    @Test
    public void shortWithoutLoudnessIsHalvedLikeSmartTube() {
        assertEquals(0.5f, LoudnessNormalizer.getVolume(1f, true, NO_LOUDNESS, false, true), 0);
        assertEquals(0.35f, LoudnessNormalizer.getVolume(0.7f, true, NO_LOUDNESS, false, true), DELTA);
    }

    @Test
    public void liftIsInMillibelsAndCapped() {
        assertEquals(0, LoudnessNormalizer.getLiftMb(1f));
        assertEquals(0, LoudnessNormalizer.getLiftMb(0.5f));
        assertEquals(0, LoudnessNormalizer.getLiftMb(Float.NaN));
        assertEquals(368, LoudnessNormalizer.getLiftMb(volume(TED_TALK)));
        assertEquals(600, LoudnessNormalizer.getLiftMb(volume(REM)));
        assertEquals(600, LoudnessNormalizer.getLiftMb(3f));
    }

    // MediaServiceCore: the parsed loudnessDb is read from both implementations, also where the heuristic gives up

    @Test
    public void readsTheLegacyFormatInfosLoudness() throws Exception {
        assertEquals(LONELY_DAY, LoudnessNormalizer.loudnessDbOf(legacy(LONELY_DAY)), 0);
        assertEquals(7.04f, LoudnessNormalizer.loudnessDbOf(legacy(7.04f)), 0); // Baby Shark
        assertEquals(REM, LoudnessNormalizer.loudnessDbOf(legacy(REM)), 0);
        assertTrue(Float.isNaN(LoudnessNormalizer.loudnessDbOf(legacy(0f))));
    }

    @Test
    public void readsTheInnertubeFormatInfosLoudness() {
        assertEquals(LONELY_DAY, LoudnessNormalizer.loudnessDbOf(innertube(LONELY_DAY)), 0);
        assertEquals(TED_TALK, LoudnessNormalizer.loudnessDbOf(innertube(TED_TALK)), 0);
        assertTrue(Float.isNaN(LoudnessNormalizer.loudnessDbOf(innertube(null))));
        assertTrue(Float.isNaN(LoudnessNormalizer.loudnessDbOf(null)));
    }

    @Test
    public void undoesTheHeuristicOfBothImplementations() throws Exception {
        for (float loudnessDb = -20f; loudnessDb < 5.75f; loudnessDb += 0.37f) {
            assertEquals(loudnessDb, LoudnessNormalizer.loudnessDbFromVolumeLevel(legacy(loudnessDb).getVolumeLevel()), DELTA);
            assertEquals(loudnessDb, LoudnessNormalizer.loudnessDbFromVolumeLevel(innertube(loudnessDb).getVolumeLevel()), DELTA);
        }

        assertEquals(ENTER_SANDMAN, LoudnessNormalizer.loudnessDbFromVolumeLevel(legacy(ENTER_SANDMAN).getVolumeLevel()), DELTA);
        // Above 5.8 dB the heuristic clamps: the least it can stand for
        assertEquals(5.8f, LoudnessNormalizer.loudnessDbFromVolumeLevel(legacy(LONELY_DAY).getVolumeLevel()), 0);
        assertEquals(5.8f, LoudnessNormalizer.loudnessDbFromVolumeLevel(innertube(7.04f).getVolumeLevel()), 0);
        assertTrue(Float.isNaN(LoudnessNormalizer.loudnessDbFromVolumeLevel(legacy(0f).getVolumeLevel())));
    }

    @Test
    public void fallsBackToTheHeuristicForAnotherImplementation() {
        assertEquals(RICK_ASTLEY, LoudnessNormalizer.loudnessDbOf(withVolumeLevel(0.4396363f)), DELTA);
        assertEquals(5.8f, LoudnessNormalizer.loudnessDbOf(withVolumeLevel(0.25f)), 0);
        assertTrue(Float.isNaN(LoudnessNormalizer.loudnessDbOf(withVolumeLevel(0.5f))));
    }

    private static MediaItemFormatInfo legacy(float loudnessDb) throws Exception {
        Constructor<YouTubeMediaItemFormatInfo> constructor = YouTubeMediaItemFormatInfo.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        YouTubeMediaItemFormatInfo formatInfo = constructor.newInstance();
        Field field = YouTubeMediaItemFormatInfo.class.getDeclaredField("mLoudnessDb");
        field.setAccessible(true);
        field.setFloat(formatInfo, loudnessDb);
        return formatInfo;
    }

    private static MediaItemFormatInfo innertube(Float loudnessDb) {
        PlayerResult.PlayerConfig playerConfig = new PlayerResult.PlayerConfig(null, new PlayerResult.PlayerConfig.AudioConfig(loudnessDb));
        return new MediaItemFormatInfoImpl(new PlayerResult(null, null, playerConfig, null, null, null, null, null, null));
    }

    private static MediaItemFormatInfo withVolumeLevel(float volumeLevel) {
        return (MediaItemFormatInfo) Proxy.newProxyInstance(LoudnessNormalizerTest.class.getClassLoader(),
                new Class<?>[] {MediaItemFormatInfo.class},
                (proxy, method, args) -> "getVolumeLevel".equals(method.getName()) ? volumeLevel : null);
    }
}
