package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GlintPlayLedgerTest {
    private final GlintPlayLedger mLedger = new GlintPlayLedger();

    @Test
    public void nothingSentCountsOnlyWhatIsUnsent() {
        assertEquals(500, mLedger.playedSince(1_000L, 500));
    }

    @Test
    public void glintUpToDateCountsOnlyWhatIsUnsent() {
        mLedger.sent(1_000, 60_000);
        mLedger.sent(2_000, 30_000);
        assertEquals(700, mLedger.playedSince(2_000L, 700));
    }

    @Test
    public void reportsGlintHadntCountedAreAddedBack() {
        mLedger.sent(1_000, 60_000);
        mLedger.sent(2_000, 30_000);
        mLedger.sent(3_000, 10_000);
        assertEquals(40_000 + 100, mLedger.playedSince(1_000L, 100));
        assertEquals(40_000 + 100, mLedger.playedSince(1_500L, 100));
    }

    @Test
    public void anAsOfOlderThanEveryReportCountsFromTheOldest() {
        mLedger.sent(5_000, 60_000);
        assertEquals(60_000, mLedger.playedSince(1_000L, 0));
    }

    @Test
    public void noAsOfCountsOnlyWhatIsUnsent() {
        mLedger.sent(5_000, 60_000);
        assertEquals(200, mLedger.playedSince(null, 200));
    }
}
