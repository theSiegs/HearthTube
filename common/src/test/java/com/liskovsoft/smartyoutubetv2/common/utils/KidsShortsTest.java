package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class KidsShortsTest {
    /** An interface answering from a map (method name to value); anything else is 0, false or null */
    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, Map<String, Object> answers) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (answers.containsKey(method.getName())) {
                return answers.get(method.getName());
            }

            Class<?> returns = method.getReturnType();
            if (returns == boolean.class) return false;
            if (returns == long.class) return 0L;
            if (returns == int.class) return 0;
            if (returns == float.class) return 0f;
            return null;
        });
    }

    private static Map<String, Object> answers(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static MediaItemFormatInfo formats(int width, int height, String lengthSeconds, boolean live) {
        MediaFormat format = fake(MediaFormat.class, answers("getWidth", width, "getHeight", height));

        return fake(MediaItemFormatInfo.class, answers("containsDashFormats", true, "getAdaptiveFormats", Collections.singletonList(format),
                "getLengthSeconds", lengthSeconds, "isLive", live));
    }

    private static MediaItem video(String title, long durationMs) {
        return fake(MediaItem.class, answers("getVideoId", "abc", "getTitle", title, "getDurationMs", durationMs));
    }

    @Test
    public void aPortraitVideoOfUnderThreeMinutesIsBlockedInAKidsProfileOnly() {
        MediaItemFormatInfo portrait = formats(720, 1280, "45", false);

        assertTrue(KidsShorts.isBlockedFormat(true, portrait, "Funny cat", "Pets"));
        assertFalse(KidsShorts.isBlockedFormat(false, portrait, "Funny cat", "Pets"));
    }

    @Test
    public void aLongOrLandscapeOrLiveVideoIsNotAShort() {
        assertFalse(KidsShorts.isBlockedFormat(true, formats(720, 1280, "600", false), "Lesson", "School"));
        assertFalse(KidsShorts.isBlockedFormat(true, formats(1920, 1080, "45", false), "Trailer", "Studio"));
        assertFalse(KidsShorts.isBlockedFormat(true, formats(720, 1280, "45", true), "Stream", "Studio"));
        assertFalse(KidsShorts.isBlockedFormat(true, null, "Anything", "Anyone"));
    }

    @Test
    public void aClipFromElsewhereIsBlockedByTitleOrChannelInAKidsProfile() {
        assertTrue(KidsShorts.isBlockedClip(true, "Best TikTok dances", "Dance"));
        assertTrue(KidsShorts.isBlockedClip(true, "Cats", "Instagram Reels Daily"));
        assertFalse(KidsShorts.isBlockedClip(true, "Cats", "Pets"));
        assertFalse(KidsShorts.isBlockedClip(false, "Best TikTok dances", "Dance"));
        assertFalse(KidsShorts.isBlockedClip(true, null, null));
        // A long landscape compilation of clips goes too, from its name alone
        assertTrue(KidsShorts.isBlockedFormat(true, formats(1920, 1080, "900", false), "TikTok compilation", "Fun"));
    }

    @Test
    public void aShortInARowIsHiddenOnlyWhileShortsAreHidden() {
        MediaItem tagged = video("Cat #shorts", 30_000);
        MediaItem normal = video("Cat documentary", 600_000);

        assertTrue(KidsShorts.isHiddenShort(true, tagged));
        assertFalse(KidsShorts.isHiddenShort(false, tagged));
        assertFalse(KidsShorts.isHiddenShort(true, normal));
        assertFalse(KidsShorts.isHiddenShort(true, null));
    }

    @Test
    public void aVideoWithNoLengthOrAClipTitleIsHiddenAsAShort() {
        assertTrue(KidsShorts.isHiddenShort(true, video("Mystery", 0)));
        assertTrue(KidsShorts.isHiddenShort(true, video("Instagram reels of the week", 600_000)));
    }
}
