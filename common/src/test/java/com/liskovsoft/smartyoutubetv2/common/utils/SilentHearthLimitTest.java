package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SilentHearthLimitTest {
    private static final long MINUTE = 60_000;
    private static final String MON = "2026-10-19";
    private static final String TUE = "2026-10-20";

    private final SilentHearthLimit mLimit = new SilentHearthLimit();

    @Test
    public void nothingKeptMeansNoLimit() {
        assertNull(mLimit.msLeft(MON));
    }

    @Test
    public void countsDownWhileHearthIsSilent() {
        mLimit.remember("alex", 30 * MINUTE, MON, 1_000);
        mLimit.played(12 * MINUTE);
        assertEquals(Long.valueOf(18 * MINUTE), mLimit.msLeft(MON));
        mLimit.played(30 * MINUTE);
        assertEquals(Long.valueOf(0), mLimit.msLeft(MON));
    }

    @Test
    public void aNewSilentDayStartsFromTheMostTheLastDayHeld() {
        mLimit.remember("alex", 60 * MINUTE, MON, 1_000);
        mLimit.played(55 * MINUTE);
        assertEquals(Long.valueOf(5 * MINUTE), mLimit.msLeft(MON));
        // Midnight with Hearth still silent: not unlimited, the day's 60 minutes again
        assertEquals(Long.valueOf(60 * MINUTE), mLimit.msLeft(TUE));
        mLimit.played(10 * MINUTE);
        assertEquals(Long.valueOf(50 * MINUTE), mLimit.msLeft(TUE));
    }

    @Test
    public void laterFiguresTheSameDayKeepWhatPlayedBetween() {
        mLimit.remember("alex", 60 * MINUTE, MON, 1_000);
        mLimit.played(20 * MINUTE);
        // Hearth speaks again with 40 left: the day still held 60
        mLimit.remember("alex", 40 * MINUTE, MON, 2_000);
        mLimit.played(40 * MINUTE);
        assertEquals(Long.valueOf(60 * MINUTE), mLimit.msLeft(TUE));
    }

    @Test
    public void aDayThatHeldNothingStaysAtNothingUntilHearthSpeaks() {
        mLimit.remember("alex", 0L, MON, 1_000);
        assertEquals(Long.valueOf(0), mLimit.msLeft(TUE));
        mLimit.remember("alex", 45 * MINUTE, TUE, 2_000);
        assertEquals(Long.valueOf(45 * MINUTE), mLimit.msLeft(TUE));
    }

    @Test
    public void theDaysMostSurvivesARestart() {
        mLimit.remember("alex", 60 * MINUTE, MON, 1_000);
        mLimit.played(30 * MINUTE);
        SilentHearthLimit restored = new SilentHearthLimit();
        restored.restore(mLimit.save());
        assertEquals(Long.valueOf(30 * MINUTE), restored.msLeft(MON));
        assertEquals(Long.valueOf(60 * MINUTE), restored.msLeft(TUE));
    }

    @Test
    public void anOlderSaveStillReads() {
        mLimit.restore("alex|" + MON + "|" + (5 * MINUTE) + "|1000");
        assertEquals(Long.valueOf(5 * MINUTE), mLimit.msLeft(MON));
        assertEquals(Long.valueOf(5 * MINUTE), mLimit.msLeft(TUE));
    }

    @Test
    public void hearthGivingNoLimitForgetsIt() {
        mLimit.remember("alex", 5 * MINUTE, MON, 1_000);
        mLimit.remember("alex", null, MON, 2_000);
        assertNull(mLimit.msLeft(MON));
    }

    @Test
    public void aLiveRowReplacesTheCountdown() {
        mLimit.remember("alex", 5 * MINUTE, MON, 1_000);
        mLimit.played(4 * MINUTE);
        mLimit.remember("alex", 40 * MINUTE, MON, 2_000);
        assertEquals(Long.valueOf(40 * MINUTE), mLimit.msLeft(MON));
    }

    @Test
    public void severalProfilesTheLeastCounts() {
        mLimit.remember("alex", 30 * MINUTE, MON, 1_000);
        mLimit.remember("sam", 10 * MINUTE, MON, 1_000);
        assertEquals(Long.valueOf(10 * MINUTE), mLimit.msLeft(MON));
    }

    @Test
    public void savedAndRestored() {
        mLimit.remember("alex|1;2", 30 * MINUTE, MON, 1_000);
        mLimit.remember("sam", 10 * MINUTE, MON, 2_000);
        mLimit.played(3 * MINUTE);

        SilentHearthLimit restored = new SilentHearthLimit();
        restored.restore(mLimit.save());
        assertEquals(Long.valueOf(7 * MINUTE), restored.msLeft(MON));
        restored.remember("sam", null, MON, 3_000);
        assertEquals(Long.valueOf(27 * MINUTE), restored.msLeft(MON));
    }

    @Test
    public void theLastLockHoldsUntilHearthLiftsIt() {
        mLimit.remember("alex", 30 * MINUTE, MON, 1_000);
        mLimit.rememberLock("alex", true, "Bedtime");
        assertTrue(mLimit.isLocked());
        assertEquals("Bedtime", mLimit.lockMessage());
        // The minutes Hearth gave before the lock are still there for after it
        assertEquals(Long.valueOf(30 * MINUTE), mLimit.msLeft(MON));

        // Across a restart and a new day, still locked: only Hearth lifts it
        SilentHearthLimit restored = new SilentHearthLimit();
        restored.restore(mLimit.save());
        assertTrue(restored.isLocked());
        assertEquals("Bedtime", restored.lockMessage());
        restored.msLeft(TUE);
        assertTrue(restored.isLocked());

        restored.rememberLock("alex", false, null);
        assertFalse(restored.isLocked());
        assertNull(restored.lockMessage());
    }

    @Test
    public void aLockWithoutWordsOrMinutesIsKept() {
        mLimit.rememberLock("sam|1;2", true, null);
        SilentHearthLimit restored = new SilentHearthLimit();
        restored.restore(mLimit.save());
        assertTrue(restored.isLocked());
        assertNull(restored.lockMessage());
        assertNull(restored.msLeft(MON));

        // Minutes saved beside a lock read back too (an older HearthTube skips the three-part lock item)
        mLimit.remember("alex", 5 * MINUTE, MON, 1_000);
        SilentHearthLimit older = new SilentHearthLimit();
        older.restore(mLimit.save());
        assertEquals(Long.valueOf(5 * MINUTE), older.msLeft(MON));
    }

    @Test
    public void unreadableIsIgnored() {
        mLimit.restore("junk");
        mLimit.restore("a|b|c|d");
        assertNull(mLimit.msLeft(MON));
        assertNull(mLimit.save());
    }
}
