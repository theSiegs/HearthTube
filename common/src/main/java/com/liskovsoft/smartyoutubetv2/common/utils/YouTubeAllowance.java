package com.liskovsoft.smartyoutubetv2.common.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayDeque;

/**
 * HearthTube: Hearth's daily YouTube allowance (Hearth's provider contract 6), decided. Hearth's /active row says how
 * many minutes the active profile has left today, whose limit that is ("hearth": the limit set in Hearth;
 * "home_assistant": Home Assistant's, a pool shared with the family's other devices, say), whether Home Assistant's
 * schedule is locked, and when Hearth last read Home Assistant. This says whether HearthTube may play, and if not, why.
 * Plain Java, so it can be unit-tested; {@link HearthAllowance} feeds it.
 * <p>
 * Fail-open: HearthTube never stops just because Home Assistant can't be reached.
 * <ul>
 * <li>Hearth's own limit doesn't depend on Home Assistant: it counts as given.</li>
 * <li>Home Assistant's minutes are as of Hearth's last read of them ({@code allowance_checked_at}), which Hearth keeps
 * for the rest of the day when Home Assistant goes away. HearthTube takes off what it played since that read (a local
 * countdown), so Home Assistant's say keeps counting down without it. What's played after the read can't be in
 * Home Assistant's figure yet, so nothing counts twice.</li>
 * <li>Home Assistant's schedule lock counts while that read is fresh ({@link #STALE_AFTER_MS}); a stale one lapses.</li>
 * <li>No Hearth, or one older than contract 6: no allowance (the caller passes no row).</li>
 * </ul>
 */
public final class YouTubeAllowance {
    public static final String SOURCE_HEARTH = "hearth";
    public static final String SOURCE_HOME_ASSISTANT = "home_assistant";
    /** Hearth's provider contract with the allowance */
    public static final int CONTRACT = 6;
    /**
     * Home Assistant's read is stale after this long, and its schedule lock lapses. Hearth reads it every minute, but
     * waits 10 minutes after a failed read: 15 minutes rides out one failed read without dropping a bedtime, while
     * Home Assistant really being away (two failed reads in a row) doesn't keep the TV locked.
     */
    public static final long STALE_AFTER_MS = 15 * 60_000;
    /** Playing is remembered this long, for a Home Assistant read that HearthTube hears of a while after it happened */
    private static final long RECENT_MS = 30 * 60_000;
    private static final long MINUTE_MS = 60_000;

    public enum Verdict {
        /** HearthTube may play */
        PLAY,
        /** The limit set in Hearth is used up for today */
        USED_UP,
        /** Home Assistant's limit is used up for today */
        USED_UP_HOME_ASSISTANT,
        /** Home Assistant's schedule is locked: bedtime, school time */
        LOCKED
    }

    public static final class Decision {
        static final Decision NO_LIMIT = new Decision(Verdict.PLAY, null, null);
        public final Verdict verdict;
        /** Home Assistant's own wording, when it's Home Assistant's say that stops playing and it gave some */
        public final String message;
        /** Playing time left today, ms (0 once it's up); null when there's no limit */
        public final Long msLeft;

        Decision(Verdict verdict, String message, Long msLeft) {
            this.verdict = verdict;
            this.message = message;
            this.msLeft = msLeft;
        }

        public boolean refuses() {
            return verdict != Verdict.PLAY;
        }

        @Override
        public String toString() {
            return verdict + (msLeft != null ? " " + msLeft / 1000 + "s left" : "") + (message != null ? " \"" + message + "\"" : "");
        }
    }

    /** The allowance columns of Hearth's /active row */
    public static final class Row {
        final int contractVersion;
        final String profileKey;
        final Integer minutesLeft;
        final boolean scheduleLocked;
        final String message;
        final String source;
        final Long checkedAt;

        public Row(int contractVersion, String profileKey, Integer minutesLeft, boolean scheduleLocked, String message,
                   String source, Long checkedAt) {
            this.contractVersion = contractVersion;
            this.profileKey = profileKey;
            this.minutesLeft = minutesLeft;
            this.scheduleLocked = scheduleLocked;
            this.message = message;
            this.source = source;
            this.checkedAt = checkedAt;
        }
    }

    /** A stretch of playing: when it ended (epoch ms), how long, and the profile it was for */
    private static final class Played {
        final long endedAt;
        final long ms;
        final String profileKey;

        Played(long endedAt, long ms, String profileKey) {
            this.endedAt = endedAt;
            this.ms = ms;
            this.profileKey = profileKey;
        }
    }

    private final ArrayDeque<Played> mRecent = new ArrayDeque<>();
    /** The profile Hearth said was active at the last decision: what's played now is for it */
    private String mProfileKey;
    /** Home Assistant's last read of its own minutes: for whom, when, what it said, and HearthTube's playing since */
    private boolean mHasHaRead;
    private String mHaProfileKey;
    private long mHaCheckedAt;
    private int mHaMinutes;
    private String mHaMessage;
    private long mHaPlayedMs;

    /** HearthTube played this long (ms), until endedAt (epoch ms) */
    public synchronized void played(long ms, long endedAt) {
        if (ms <= 0) {
            return;
        }

        mRecent.addLast(new Played(endedAt, ms, mProfileKey));
        while (mRecent.peekFirst().endedAt < endedAt - RECENT_MS) {
            mRecent.removeFirst();
        }

        if (mHasHaRead && equal(mHaProfileKey, mProfileKey)) {
            mHaPlayedMs += after(ms, endedAt, mHaCheckedAt);
        }
    }

    /**
     * May HearthTube play now? {@code row} is Hearth's /active row (null without a Hearth that can be relied on: no
     * allowance then).
     */
    public synchronized Decision decide(Row row, long now) {
        if (row == null || row.contractVersion < CONTRACT) {
            return Decision.NO_LIMIT;
        }

        mProfileKey = row.profileKey;
        boolean fromHa = SOURCE_HOME_ASSISTANT.equals(row.source);

        // Home Assistant's own minutes (with a lock they may be Hearth's, which a lock marks as Home Assistant's too):
        // remembered as of the read, with what's been played since
        if (fromHa && !row.scheduleLocked && row.minutesLeft != null && row.checkedAt != null && row.profileKey != null) {
            if (!isHaRead(row.profileKey, row.checkedAt)) {
                mHasHaRead = true;
                mHaProfileKey = row.profileKey;
                mHaCheckedAt = row.checkedAt;
                mHaPlayedMs = playedSince(row.profileKey, row.checkedAt);
            }
            mHaMinutes = row.minutesLeft;
            mHaMessage = row.message;
        }

        boolean fresh = row.checkedAt != null && now - row.checkedAt <= STALE_AFTER_MS;

        if (row.scheduleLocked && fresh) {
            return new Decision(Verdict.LOCKED, row.message, 0L);
        }

        // Hearth's minutes as given: its own limit, or Home Assistant's as read
        Long msLeft = row.minutesLeft != null ? row.minutesLeft * MINUTE_MS : null;
        boolean haBinds = fromHa;
        String message = fromHa ? row.message : null;

        // Home Assistant's minutes less what HearthTube played since they were read. They still count when Hearth's
        // own limit has come down under their stale figure, and Hearth says "hearth" again
        if (isHaRead(row.profileKey, row.checkedAt)) {
            long haLeft = Math.max(0, mHaMinutes * MINUTE_MS - mHaPlayedMs);

            if (msLeft == null || haLeft <= msLeft) {
                msLeft = haLeft;
                haBinds = true;
                message = mHaMessage;
            }
        }

        if (msLeft == null || msLeft > 0) {
            return new Decision(Verdict.PLAY, null, msLeft);
        }

        return haBinds ? new Decision(Verdict.USED_UP_HOME_ASSISTANT, message, 0L) : new Decision(Verdict.USED_UP, null, 0L);
    }

    /** Home Assistant's last read and the playing since, to keep across restarts; null when there's none */
    public synchronized String save() {
        if (!mHasHaRead) {
            return null;
        }

        return encode(mHaProfileKey) + "|" + mHaCheckedAt + "|" + mHaMinutes + "|" + mHaPlayedMs + "|" + encode(mHaMessage);
    }

    /** Takes back what {@link #save} gave; ignores anything unreadable */
    public synchronized void restore(String saved) {
        String[] parts = saved != null ? saved.split("\\|", -1) : new String[0];

        if (parts.length != 5) {
            return;
        }

        try {
            long checkedAt = Long.parseLong(parts[1]);
            int minutes = Integer.parseInt(parts[2]);
            long playedMs = Long.parseLong(parts[3]);
            String profileKey = decode(parts[0]);

            if (profileKey == null) {
                return;
            }

            mHasHaRead = true;
            mHaProfileKey = profileKey;
            mHaCheckedAt = checkedAt;
            mHaMinutes = minutes;
            mHaPlayedMs = playedMs;
            mHaMessage = decode(parts[4]);
        } catch (NumberFormatException e) {
            // Unreadable: start afresh
        }
    }

    private boolean isHaRead(String profileKey, Long checkedAt) {
        return mHasHaRead && checkedAt != null && mHaCheckedAt == checkedAt && equal(mHaProfileKey, profileKey);
    }

    private long playedSince(String profileKey, long since) {
        long ms = 0;

        for (Played played : mRecent) {
            if (equal(played.profileKey, profileKey)) {
                ms += after(played.ms, played.endedAt, since);
            }
        }

        return ms;
    }

    /** The part of a stretch of playing (ms, ending at endedAt) after {@code since} */
    private static long after(long ms, long endedAt, long since) {
        return Math.max(0, Math.min(ms, endedAt - since));
    }

    private static boolean equal(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String encode(String value) {
        try {
            return value != null ? URLEncoder.encode(value, "UTF-8") : "";
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    private static String decode(String value) {
        try {
            return value.isEmpty() ? null : URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return null;
        }
    }
}
