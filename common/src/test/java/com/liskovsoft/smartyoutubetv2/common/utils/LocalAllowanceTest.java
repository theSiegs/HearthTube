package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocalAllowanceTest {
    private static final long MINUTE = 60_000;
    private static final String MON = "2026-10-19";
    private static final String TUE = "2026-10-20";

    private final LocalAllowance mAllowance = new LocalAllowance();

    @Test
    public void noLimitMeansNoLimit() {
        mAllowance.played(MON, 500 * MINUTE);
        assertNull(mAllowance.msLeft(MON));
        assertTrue(mAllowance.mayPlay(MON));
    }

    @Test
    public void playingCountsDownTheDay() {
        mAllowance.setLimitMinutes(30);
        mAllowance.played(MON, 10 * MINUTE);
        assertEquals(Long.valueOf(20 * MINUTE), mAllowance.msLeft(MON));
        assertTrue(mAllowance.mayPlay(MON));
    }

    @Test
    public void theLimitUsedUpStopsPlaying() {
        mAllowance.setLimitMinutes(30);
        mAllowance.played(MON, 30 * MINUTE);
        assertEquals(Long.valueOf(0), mAllowance.msLeft(MON));
        assertFalse(mAllowance.mayPlay(MON));
    }

    @Test
    public void overshootingDoesNotGoNegative() {
        mAllowance.setLimitMinutes(30);
        mAllowance.played(MON, 45 * MINUTE);
        assertEquals(Long.valueOf(0), mAllowance.msLeft(MON));
    }

    @Test
    public void aNewDayStartsAgain() {
        mAllowance.setLimitMinutes(30);
        mAllowance.played(MON, 30 * MINUTE);
        assertFalse(mAllowance.mayPlay(MON));
        assertTrue(mAllowance.mayPlay(TUE));
        assertEquals(Long.valueOf(30 * MINUTE), mAllowance.msLeft(TUE));
    }

    @Test
    public void raisingTheLimitMidDayGivesTheDifference() {
        mAllowance.setLimitMinutes(30);
        mAllowance.played(MON, 30 * MINUTE);
        mAllowance.setLimitMinutes(45);
        assertEquals(Long.valueOf(15 * MINUTE), mAllowance.msLeft(MON));
    }

    @Test
    public void takingTheLimitOffMidDayPlaysAgain() {
        mAllowance.setLimitMinutes(30);
        mAllowance.played(MON, 30 * MINUTE);
        mAllowance.setLimitMinutes(0);
        assertTrue(mAllowance.mayPlay(MON));
    }

    @Test
    public void negativeOrZeroPlayingIsIgnored() {
        mAllowance.setLimitMinutes(30);
        mAllowance.played(MON, -5 * MINUTE);
        mAllowance.played(MON, 0);
        assertEquals(Long.valueOf(30 * MINUTE), mAllowance.msLeft(MON));
    }

    @Test
    public void saveAndRestoreKeepTheDay() {
        mAllowance.setLimitMinutes(30);
        mAllowance.played(MON, 12 * MINUTE);

        LocalAllowance restored = new LocalAllowance();
        restored.restore(mAllowance.save());

        assertEquals(30, restored.getLimitMinutes());
        assertEquals(Long.valueOf(18 * MINUTE), restored.msLeft(MON));
        assertEquals(Long.valueOf(30 * MINUTE), restored.msLeft(TUE));
    }

    @Test
    public void unreadableSavedStateIsIgnored() {
        mAllowance.restore("nonsense");
        mAllowance.restore("a|b|c");
        mAllowance.restore(null);
        assertNull(mAllowance.msLeft(MON));
    }
}
