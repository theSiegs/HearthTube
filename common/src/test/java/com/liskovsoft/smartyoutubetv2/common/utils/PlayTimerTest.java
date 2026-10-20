package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlayTimerTest {
    private static final long MINUTE = 60_000;
    private static final long CAP = 10 * MINUTE;

    private final PlayTimer mTimer = new PlayTimer(CAP, CAP);

    @Test
    public void startsOnceUntilStopped() {
        assertTrue(mTimer.start(0));
        assertFalse(mTimer.start(5));
        assertTrue(mTimer.isPlaying());
        mTimer.take(1_000, false);
        assertFalse(mTimer.isPlaying());
        assertTrue(mTimer.start(2_000));
    }

    @Test
    public void takeCountsAndKeepsPlaying() {
        mTimer.start(0);
        assertEquals(30_000, mTimer.take(30_000, true));
        assertEquals(30_000, mTimer.take(60_000, true));
        assertEquals(60_000, mTimer.unreportedMs());
        assertTrue(mTimer.isPlaying());
    }

    @Test
    public void takeWhileNotPlayingCountsNothing() {
        assertEquals(0, mTimer.take(99_000, true));
        assertEquals(0, mTimer.unreportedMs());
    }

    @Test
    public void aMissedStopCountsOnlyOneStretchCap() {
        mTimer.start(0);
        assertEquals(CAP, mTimer.take(5 * 60 * MINUTE, false));
    }

    @Test
    public void silentHearthKeepsMoreThanTenMinutes() {
        mTimer.start(0);
        long now = 0;
        for (int i = 0; i < 6; i++) {
            now += 5 * MINUTE;
            mTimer.take(now, true);
        }
        mTimer.take(now, false);
        assertEquals(30 * MINUTE, mTimer.unreportedMs());
    }

    @Test
    public void aReconnectGetsTheFullFigureOneReportAtATime() {
        mTimer.start(0);
        for (int i = 1; i <= 5; i++) {
            mTimer.take(i * 5 * MINUTE, true);
        }
        mTimer.take(25 * MINUTE, false);
        long total = 0;
        long ms;
        while ((ms = mTimer.nextReport(1_000)) > 0) {
            assertTrue(ms <= CAP);
            total += ms;
        }
        assertEquals(25 * MINUTE, total);
        assertEquals(0, mTimer.unreportedMs());
    }

    @Test
    public void tinyTimeWaitsForTheNextReport() {
        mTimer.start(0);
        mTimer.take(500, false);
        assertEquals(0, mTimer.nextReport(1_000));
        assertEquals(500, mTimer.unreportedMs());
        mTimer.start(1_000);
        mTimer.take(1_600, false);
        assertEquals(1_100, mTimer.nextReport(1_000));
    }

    @Test
    public void aReportHearthRefusedIsGivenBack() {
        mTimer.start(0);
        mTimer.take(3 * MINUTE, false);
        long ms = mTimer.nextReport(1_000);
        assertEquals(0, mTimer.unreportedMs());
        mTimer.giveBack(ms);
        assertEquals(3 * MINUTE, mTimer.unreportedMs());
    }

    @Test
    public void stopWaitReportSequence() {
        // Playing, then buffering (counts, keeps for the next report), playing again, then stopped and reported
        mTimer.start(0);
        mTimer.take(20_000, false);
        assertFalse(mTimer.isPlaying());
        mTimer.start(25_000);
        mTimer.take(40_000, false);
        assertEquals(35_000, mTimer.nextReport(1_000));
        assertEquals(0, mTimer.nextReport(1_000));
    }
}
