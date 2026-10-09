package com.liskovsoft.smartyoutubetv2.common.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HearthTube's update channels, the part without Android (unit tested): which update feed wins, and where
 * "Include pre-releases" starts. {@link UpdateChannels} does the reading and the storing.
 */
public final class UpdateFeedPicker {
    /** A feed that couldn't be read: offline, a server error, or not an update feed */
    public static final int FAILED = -1;
    /** A feed that isn't there (HTTP 404): nothing published on that channel yet */
    public static final int MISSING = 0;
    /** Nothing to pick */
    public static final int NONE = -1;
    private static final Pattern VERSION_CODE = Pattern.compile("\"versionCode\"\\s*:\\s*(\\d+)");

    private UpdateFeedPicker() {
    }

    /**
     * The newest versionCode an update feed offers, as SharedModules' AppVersionChecker reads it: a "package" entry
     * with the download links, then one entry per version, each with its "versionCode" (release.ps1 writes one).
     *
     * @return the highest versionCode in it, or {@link #FAILED} when it has none
     */
    public static int newestVersionCode(String feed) {
        if (feed == null) {
            return FAILED;
        }

        int newest = FAILED;
        Matcher matcher = VERSION_CODE.matcher(feed);

        while (matcher.find()) {
            String digits = matcher.group(1);
            // An Android versionCode fits an int; anything longer isn't one
            if (digits.length() <= 10 && Long.parseLong(digits) <= Integer.MAX_VALUE) {
                newest = Math.max(newest, Integer.parseInt(digits));
            }
        }

        return newest > 0 ? newest : FAILED;
    }

    /**
     * The feed to update from: the one offering the highest versionCode; on a tie, the first of them (the stable
     * feed comes first).
     *
     * @param versionCodes per feed, in order: its newest versionCode, {@link #MISSING} or {@link #FAILED}
     * @return the winner's index, or {@link #NONE} when no feed offers anything
     */
    public static int pick(int[] versionCodes) {
        int best = NONE;

        for (int i = 0; i < versionCodes.length; i++) {
            if (versionCodes[i] > MISSING && (best == NONE || versionCodes[i] > versionCodes[best])) {
                best = i;
            }
        }

        return best;
    }

    /**
     * When no feed offers anything: true if that's only because none is published yet (every one {@link #MISSING}),
     * so there's nothing to update to; false when one couldn't be read, which is an error worth showing.
     */
    public static boolean isNothingPublished(int[] versionCodes) {
        if (versionCodes.length == 0) {
            return false;
        }

        for (int versionCode : versionCodes) {
            if (versionCode != MISSING) {
                return false;
            }
        }

        return true;
    }

    /**
     * Where "Include pre-releases" starts, until someone changes it. On in debug builds. In release builds, on for a
     * copy that was installed before and updated to this one (every copy from before the setting existed took
     * pre-releases), off for a new install, where Android gives both times the same value. Worked out once, at the
     * first start of a version with the setting, and kept: a later update mustn't turn it on.
     */
    public static boolean defaultIncludePreReleases(boolean debugBuild, long firstInstallTime, long lastUpdateTime) {
        return debugBuild || firstInstallTime != lastUpdateTime;
    }
}
