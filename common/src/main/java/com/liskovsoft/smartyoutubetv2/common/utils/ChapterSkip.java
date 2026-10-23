package com.liskovsoft.smartyoutubetv2.common.utils;

/**
 * HearthTube: where a chapter skip goes. Forward is the next chapter's start; back is the start of the chapter playing
 * now, or the one before it when the playing one has only just begun (the way a player's "previous" works). Plain Java,
 * so the rule is tested apart from Android.
 */
public final class ChapterSkip {
    /** Inside this long of a chapter's start, "back" goes to the chapter before */
    public static final long JUST_STARTED_MS = 3_000;

    private ChapterSkip() {
    }

    /**
     * @param startsMs the chapters' start times, ascending
     * @return the position to seek to, or -1 when there is nowhere to go (the last chapter forward)
     */
    public static long target(long[] startsMs, long positionMs, boolean forward) {
        if (startsMs == null || startsMs.length == 0) {
            return -1;
        }

        if (forward) {
            for (long start : startsMs) {
                if (start > positionMs + JUST_STARTED_MS) {
                    return start;
                }
            }

            return -1;
        }

        long result = 0;

        for (long start : startsMs) {
            if (start < positionMs - JUST_STARTED_MS) {
                result = start;
            }
        }

        return result;
    }
}
