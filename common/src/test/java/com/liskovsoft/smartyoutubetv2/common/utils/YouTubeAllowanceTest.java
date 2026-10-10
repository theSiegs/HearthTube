package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.common.utils.YouTubeAllowance.Decision;
import com.liskovsoft.smartyoutubetv2.common.utils.YouTubeAllowance.Row;
import com.liskovsoft.smartyoutubetv2.common.utils.YouTubeAllowance.Verdict;

import org.junit.Test;

public class YouTubeAllowanceTest {
    private static final long MINUTE = 60_000;
    /** 2026-10-10 18:00 UTC */
    private static final long NOW = 1_791_655_200_000L;
    private static final String KID = "user:11";
    private static final String HEARTH = YouTubeAllowance.SOURCE_HEARTH;
    private static final String HA = YouTubeAllowance.SOURCE_HOME_ASSISTANT;

    private final YouTubeAllowance mAllowance = new YouTubeAllowance();

    private static Row row(Integer minutesLeft, String source, Long checkedAt) {
        return new Row(6, KID, minutesLeft, false, null, source, checkedAt);
    }

    private static Row haRow(Integer minutesLeft, String message, Long checkedAt) {
        return new Row(6, KID, minutesLeft, false, message, HA, checkedAt);
    }

    private static Row locked(Integer minutesLeft, String message, Long checkedAt) {
        return new Row(6, KID, minutesLeft, true, message, HA, checkedAt);
    }

    /** Plays for {@code minutes} from {@code start}, reported every half minute as HearthTube does */
    private void play(long start, double minutes) {
        long ms = (long) (minutes * MINUTE);
        for (long at = start; at < start + ms; at += 30_000) {
            long part = Math.min(30_000, start + ms - at);
            mAllowance.played(part, at + part);
        }
    }

    @Test
    public void noLimit() {
        Decision decision = mAllowance.decide(row(null, null, null), NOW);

        assertEquals(Verdict.PLAY, decision.verdict);
        assertNull(decision.msLeft);
        assertFalse(decision.refuses());
    }

    @Test
    public void noHearthOrAnOlderOne() {
        // No Hearth to rely on, or one from before contract 6 (no allowance columns: their values read as none)
        assertEquals(Verdict.PLAY, mAllowance.decide(null, NOW).verdict);
        assertEquals(Verdict.PLAY, mAllowance.decide(new Row(5, KID, null, false, null, null, null), NOW).verdict);
        assertEquals(Verdict.PLAY, mAllowance.decide(new Row(1, null, null, false, null, null, null), NOW).verdict);
        // Even if an older Hearth somehow had such columns, they aren't its contract's
        assertEquals(Verdict.PLAY, mAllowance.decide(new Row(5, KID, 0, true, null, HA, NOW), NOW).verdict);
    }

    @Test
    public void hearthLimit() {
        Decision some = mAllowance.decide(row(12, HEARTH, null), NOW);
        assertEquals(Verdict.PLAY, some.verdict);
        assertEquals(Long.valueOf(12 * MINUTE), some.msLeft);

        Decision none = mAllowance.decide(row(0, HEARTH, null), NOW);
        assertEquals(Verdict.USED_UP, none.verdict);
        assertTrue(none.refuses());
        assertNull(none.message);
    }

    @Test
    public void hearthLimitCountsAsGiven() {
        // Hearth counts its own limit down from HearthTube's reports: nothing's taken off locally, however long ago
        // Home Assistant was read
        mAllowance.decide(row(5, HEARTH, NOW - 30 * MINUTE), NOW - 10 * MINUTE);
        play(NOW - 10 * MINUTE, 10);

        Decision decision = mAllowance.decide(row(5, HEARTH, NOW - 30 * MINUTE), NOW);
        assertEquals(Verdict.PLAY, decision.verdict);
        assertEquals(Long.valueOf(5 * MINUTE), decision.msLeft);
    }

    @Test
    public void homeAssistantLimit() {
        Decision some = mAllowance.decide(haRow(7, null, NOW - 20_000), NOW);
        assertEquals(Verdict.PLAY, some.verdict);

        Decision none = mAllowance.decide(haRow(0, "The family's YouTube time is gone", NOW - 20_000), NOW);
        assertEquals(Verdict.USED_UP_HOME_ASSISTANT, none.verdict);
        assertEquals("The family's YouTube time is gone", none.message);

        // No wording from Home Assistant: HearthTube's own
        Decision plain = new YouTubeAllowance().decide(haRow(0, null, NOW - 20_000), NOW);
        assertEquals(Verdict.USED_UP_HOME_ASSISTANT, plain.verdict);
        assertNull(plain.message);
    }

    @Test
    public void homeAssistantStaleCountsDownLocally() {
        long readAt = NOW - 40 * MINUTE;
        // Home Assistant said 10 minutes, then went away: Hearth keeps saying 10
        assertEquals(Long.valueOf(10 * MINUTE), mAllowance.decide(haRow(10, "Pool's empty", readAt), readAt + MINUTE).msLeft);

        play(readAt + 2 * MINUTE, 6);
        Decision some = mAllowance.decide(haRow(10, "Pool's empty", readAt), readAt + 9 * MINUTE);
        assertEquals(Verdict.PLAY, some.verdict);
        assertEquals(Long.valueOf(4 * MINUTE), some.msLeft);

        play(readAt + 20 * MINUTE, 4);
        Decision none = mAllowance.decide(haRow(10, "Pool's empty", readAt), NOW);
        assertEquals(Verdict.USED_UP_HOME_ASSISTANT, none.verdict);
        assertEquals("Pool's empty", none.message);
    }

    @Test
    public void homeAssistantStaleNeverStopsByItself() {
        // Read hours ago and nothing played since: its minutes still stand
        Decision decision = mAllowance.decide(haRow(10, null, NOW - 3 * 60 * MINUTE), NOW);

        assertEquals(Verdict.PLAY, decision.verdict);
        assertEquals(Long.valueOf(10 * MINUTE), decision.msLeft);
    }

    @Test
    public void playingBeforeTheReadIsInItAlready() {
        long readAt = NOW - 5 * MINUTE;
        mAllowance.decide(row(null, null, null), NOW - 20 * MINUTE);
        // 10 minutes up to 2 minutes before the read, then 1 minute across it (half before, half after)
        play(NOW - 17 * MINUTE, 10);
        play(readAt - 30_000, 1);

        Decision decision = mAllowance.decide(haRow(10, null, readAt), NOW);
        assertEquals(Long.valueOf(10 * MINUTE - 30_000), decision.msLeft);
    }

    @Test
    public void freshReadsReplaceTheCountdown() {
        long firstRead = NOW - 10 * MINUTE;
        mAllowance.decide(haRow(10, null, firstRead), firstRead);
        play(firstRead, 8);
        assertEquals(Long.valueOf(2 * MINUTE), mAllowance.decide(haRow(10, null, firstRead), firstRead + 8 * MINUTE).msLeft);

        // Home Assistant is back, and a parent gave more time: the 8 minutes before this read are in it already
        long secondRead = NOW - 2 * MINUTE;
        assertEquals(Long.valueOf(30 * MINUTE), mAllowance.decide(haRow(30, null, secondRead), secondRead).msLeft);
        play(secondRead, 1);
        assertEquals(Long.valueOf(29 * MINUTE), mAllowance.decide(haRow(30, null, secondRead), NOW).msLeft);
    }

    @Test
    public void homeAssistantStillCountsWhenHearthsOwnLimitComesUnder() {
        // Home Assistant said 10 (Hearth's own limit: 15 left), then went away
        long readAt = NOW - 30 * MINUTE;
        mAllowance.decide(haRow(10, "Shared time's up", readAt), readAt);
        play(readAt, 6);
        // Hearth's own is down to 9 now, under Home Assistant's stale 10: Hearth says "hearth"
        Decision some = mAllowance.decide(row(9, HEARTH, readAt), readAt + 6 * MINUTE);
        assertEquals(Long.valueOf(4 * MINUTE), some.msLeft);

        play(readAt + 6 * MINUTE, 4);
        Decision none = mAllowance.decide(row(5, HEARTH, readAt), readAt + 10 * MINUTE);
        assertEquals(Verdict.USED_UP_HOME_ASSISTANT, none.verdict);
        assertEquals("Shared time's up", none.message);
    }

    @Test
    public void hearthsOwnLimitWhenItsTheSmaller() {
        long readAt = NOW - 30 * MINUTE;
        mAllowance.decide(haRow(10, null, readAt), readAt);
        play(readAt, 2);

        Decision decision = mAllowance.decide(row(0, HEARTH, readAt), readAt + 2 * MINUTE);
        assertEquals(Verdict.USED_UP, decision.verdict);
    }

    @Test
    public void scheduleLock() {
        Decision bedtime = mAllowance.decide(locked(null, null, NOW - 30_000), NOW);
        assertEquals(Verdict.LOCKED, bedtime.verdict);
        assertNull(bedtime.message);

        Decision worded = mAllowance.decide(locked(25, "School time", NOW - 14 * MINUTE), NOW);
        assertEquals(Verdict.LOCKED, worded.verdict);
        assertEquals("School time", worded.message);
    }

    @Test
    public void staleLockLapses() {
        // Home Assistant locked it, then went away: after 15 minutes without a read the lock no longer counts
        Decision decision = mAllowance.decide(locked(null, null, NOW - 16 * MINUTE), NOW);
        assertEquals(Verdict.PLAY, decision.verdict);

        // Minutes left with it still count, as given (Hearth's own limit, maybe: a lock makes it say "home_assistant")
        Decision withMinutes = mAllowance.decide(locked(20, null, NOW - 16 * MINUTE), NOW);
        assertEquals(Long.valueOf(20 * MINUTE), withMinutes.msLeft);
        Decision noneLeft = mAllowance.decide(locked(0, "Bedtime", NOW - 16 * MINUTE), NOW);
        assertEquals(Verdict.USED_UP_HOME_ASSISTANT, noneLeft.verdict);
    }

    @Test
    public void dayChange() {
        // Yesterday Home Assistant's time ran out, and it stayed away
        long yesterday = NOW - 24 * 60 * MINUTE;
        mAllowance.decide(haRow(5, null, yesterday), yesterday);
        play(yesterday, 5);
        assertEquals(Verdict.USED_UP_HOME_ASSISTANT, mAllowance.decide(haRow(5, null, yesterday), yesterday + 5 * MINUTE).verdict);

        // A new day: Hearth forgets the read (null), its own limit starts afresh
        Decision today = mAllowance.decide(row(30, HEARTH, null), NOW);
        assertEquals(Verdict.PLAY, today.verdict);
        assertEquals(Long.valueOf(30 * MINUTE), today.msLeft);
        // And no limit at all without one
        assertEquals(Verdict.PLAY, mAllowance.decide(row(null, null, null), NOW).verdict);
        // Today's first read counts from its own time
        assertEquals(Long.valueOf(45 * MINUTE), mAllowance.decide(haRow(45, null, NOW), NOW).msLeft);
    }

    @Test
    public void anotherProfilesPlayingDoesntCount() {
        long readAt = NOW - 30 * MINUTE;
        mAllowance.decide(haRow(10, null, readAt), readAt);
        play(readAt, 3);
        // A grown-up's profile (same Android user) watches for a while
        mAllowance.decide(new Row(6, "user:0:sam", null, false, null, null, readAt), readAt + 3 * MINUTE);
        play(readAt + 3 * MINUTE, 20);

        Decision decision = mAllowance.decide(haRow(10, null, readAt), NOW);
        assertEquals(Long.valueOf(7 * MINUTE), decision.msLeft);
    }

    @Test
    public void countdownSurvivesARestart() {
        long readAt = NOW - 30 * MINUTE;
        mAllowance.decide(haRow(10, "Pool's empty | for today", readAt), readAt);
        play(readAt, 6);

        YouTubeAllowance restarted = new YouTubeAllowance();
        restarted.restore(mAllowance.save());
        Decision decision = restarted.decide(haRow(10, "Pool's empty | for today", readAt), NOW);
        assertEquals(Long.valueOf(4 * MINUTE), decision.msLeft);

        restarted.played(4 * MINUTE, NOW);
        Decision none = restarted.decide(row(8, HEARTH, readAt), NOW);
        assertEquals(Verdict.USED_UP_HOME_ASSISTANT, none.verdict);
        assertEquals("Pool's empty | for today", none.message);

        // Nothing saved, or something unreadable: a fresh start
        assertNull(new YouTubeAllowance().save());
        YouTubeAllowance garbled = new YouTubeAllowance();
        garbled.restore("user%3A11|not a number|10|0|");
        assertNull(garbled.save());
        garbled.restore(null);
        assertNull(garbled.save());
    }
}
