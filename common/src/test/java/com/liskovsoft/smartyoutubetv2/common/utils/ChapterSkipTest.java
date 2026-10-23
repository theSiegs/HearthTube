package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ChapterSkipTest {
    private static final long[] STARTS = {0, 60_000, 180_000, 300_000};

    @Test
    public void forwardGoesToTheNextChapter() {
        assertEquals(60_000, ChapterSkip.target(STARTS, 10_000, true));
        assertEquals(180_000, ChapterSkip.target(STARTS, 100_000, true));
    }

    @Test
    public void forwardJustBeforeAChapterGoesToTheOneAfter() {
        // 58 s is within 3 s of the 60 s start: that one is as good as here
        assertEquals(180_000, ChapterSkip.target(STARTS, 58_000, true));
    }

    @Test
    public void forwardFromTheLastChapterHasNowhereToGo() {
        assertEquals(-1, ChapterSkip.target(STARTS, 400_000, true));
    }

    @Test
    public void backGoesToTheStartOfThisChapter() {
        assertEquals(60_000, ChapterSkip.target(STARTS, 100_000, false));
    }

    @Test
    public void backJustAfterAChapterStartGoesToTheChapterBefore() {
        assertEquals(0, ChapterSkip.target(STARTS, 61_500, false));
    }

    @Test
    public void backInTheFirstChapterGoesToTheStart() {
        assertEquals(0, ChapterSkip.target(STARTS, 20_000, false));
    }

    @Test
    public void noChaptersMeansNoSkip() {
        assertEquals(-1, ChapterSkip.target(new long[0], 5_000, true));
        assertEquals(-1, ChapterSkip.target(null, 5_000, false));
    }
}
