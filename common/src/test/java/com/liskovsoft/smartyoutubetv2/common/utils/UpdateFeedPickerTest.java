package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdateFeedPickerTest {
    private static final int FAILED = UpdateFeedPicker.FAILED;
    private static final int MISSING = UpdateFeedPicker.MISSING;
    private static final int NONE = UpdateFeedPicker.NONE;

    /** Shaped like release.ps1's hearthtube.json (PowerShell's ConvertTo-Json spacing) */
    private static String feed(String... versions) {
        StringBuilder json = new StringBuilder("{\n    \"package\":  {\n"
                + "                    \"downloadUrlList\":  [\n"
                + "                                            \"https://example.com/stable/hearthtube_universal.apk\"\n"
                + "                                        ]\n                },\n");
        for (int i = 0; i < versions.length; i += 2) {
            json.append("    \"").append(versions[i]).append("\":  {\n")
                    .append("                    \"versionCode\":  ").append(versions[i + 1]).append(",\n")
                    .append("                    \"changelog\":  [\n                                      \"Fixes\"\n")
                    .append("                                  ]\n                }")
                    .append(i + 2 < versions.length ? ",\n" : "\n");
        }
        return json.append("}").toString();
    }

    @Test
    public void readsTheFeedsVersionCode() {
        assertEquals(2453004, UpdateFeedPicker.newestVersionCode(feed("32.63+4", "2453004")));
    }

    @Test
    public void readsCompactJson() {
        assertEquals(2453005, UpdateFeedPicker.newestVersionCode(
                "{\"package\":{\"downloadUrl\":\"https://example.com/a.apk\"},\"32.63+5\":{\"versionCode\":2453005}}"));
    }

    @Test
    public void takesTheNewestOfSeveralVersions() {
        assertEquals(2453007, UpdateFeedPicker.newestVersionCode(
                feed("32.63+5", "2453005", "32.63+7", "2453007", "32.63+6", "2453006")));
    }

    @Test
    public void readsDateVersions() {
        // Versions are release dates now, a second release that day numbered: versionCode yyMMddNN
        assertEquals(26101002, UpdateFeedPicker.newestVersionCode(
                feed("2026.10.10", "26101001", "2026.10.10.2", "26101002")));
    }

    @Test
    public void notAFeedIsFailed() {
        assertEquals(FAILED, UpdateFeedPicker.newestVersionCode(null));
        assertEquals(FAILED, UpdateFeedPicker.newestVersionCode(""));
        assertEquals(FAILED, UpdateFeedPicker.newestVersionCode("Not Found"));
        assertEquals(FAILED, UpdateFeedPicker.newestVersionCode("{\"package\":{}}"));
        assertEquals(FAILED, UpdateFeedPicker.newestVersionCode("{\"1.0\":{\"versionCode\":0}}"));
    }

    @Test
    public void ignoresNumbersTooBigForAVersionCode() {
        assertEquals(FAILED, UpdateFeedPicker.newestVersionCode("{\"1.0\":{\"versionCode\":12345678901}}"));
        assertEquals(FAILED, UpdateFeedPicker.newestVersionCode("{\"1.0\":{\"versionCode\":2147483648}}"));
        assertEquals(2147483647, UpdateFeedPicker.newestVersionCode("{\"1.0\":{\"versionCode\":2147483647}}"));
    }

    @Test
    public void stableOnly() {
        assertEquals(0, UpdateFeedPicker.pick(new int[] {2453004}));
    }

    @Test
    public void newerPreReleaseWins() {
        assertEquals(1, UpdateFeedPicker.pick(new int[] {2453004, 2453006}));
    }

    @Test
    public void newerStableWins() {
        // A stable release published after the last pre-release reaches pre-release users too
        assertEquals(0, UpdateFeedPicker.pick(new int[] {2453007, 2453006}));
    }

    @Test
    public void aDateVersionBeatsTheSmartTubeStyleOnes() {
        // The first date release on one channel while the other still offers 32.63+7
        assertEquals(1, UpdateFeedPicker.pick(new int[] {2453007, 26101001}));
        assertEquals(0, UpdateFeedPicker.pick(new int[] {26101001, 2453007}));
    }

    @Test
    public void tieGoesToStable() {
        assertEquals(0, UpdateFeedPicker.pick(new int[] {2453006, 2453006}));
    }

    @Test
    public void unreadableOrMissingFeedsAreSkipped() {
        assertEquals(1, UpdateFeedPicker.pick(new int[] {MISSING, 2453004}));
        assertEquals(1, UpdateFeedPicker.pick(new int[] {FAILED, 2453004}));
        assertEquals(0, UpdateFeedPicker.pick(new int[] {2453004, FAILED}));
    }

    @Test
    public void nothingToPick() {
        assertEquals(NONE, UpdateFeedPicker.pick(new int[0]));
        assertEquals(NONE, UpdateFeedPicker.pick(new int[] {MISSING}));
        assertEquals(NONE, UpdateFeedPicker.pick(new int[] {FAILED, MISSING}));
    }

    @Test
    public void nothingPublishedOnlyWhenEveryFeedIsMissing() {
        assertTrue(UpdateFeedPicker.isNothingPublished(new int[] {MISSING}));
        assertTrue(UpdateFeedPicker.isNothingPublished(new int[] {MISSING, MISSING}));
        assertFalse(UpdateFeedPicker.isNothingPublished(new int[] {MISSING, FAILED}));
        assertFalse(UpdateFeedPicker.isNothingPublished(new int[] {FAILED}));
        assertFalse(UpdateFeedPicker.isNothingPublished(new int[] {MISSING, 2453004}));
        assertFalse(UpdateFeedPicker.isNothingPublished(new int[0]));
    }

    @Test
    public void preReleasesStartOnInDebugBuilds() {
        assertTrue(UpdateFeedPicker.defaultIncludePreReleases(true, 1000L, 1000L));
        assertTrue(UpdateFeedPicker.defaultIncludePreReleases(true, 1000L, 2000L));
    }

    @Test
    public void preReleasesStartOnForAnUpdatedReleaseInstall() {
        assertTrue(UpdateFeedPicker.defaultIncludePreReleases(false, 1000L, 2000L));
    }

    @Test
    public void preReleasesStartOffForANewReleaseInstall() {
        assertFalse(UpdateFeedPicker.defaultIncludePreReleases(false, 1000L, 1000L));
    }
}
