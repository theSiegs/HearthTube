package com.liskovsoft.smartyoutubetv2.common.utils;

/**
 * HearthTube: counts how long a video really played and keeps what Hearth hasn't been told yet. Plain Java, so it's
 * unit-tested; {@link HearthAllowance} feeds it the clock. One stretch of playing is capped (a missed stop mustn't
 * count for hours), and so is one report to Hearth, but the total waiting for Hearth is not: while Hearth is silent
 * nothing is forgotten, and a reconnect hands over the full figure, one report at a time.
 */
public final class PlayTimer {
    private final long mMaxStretchMs;
    private final long mMaxReportMs;
    private long mPlayingSince = -1;
    private long mUnreportedMs;

    public PlayTimer(long maxStretchMs, long maxReportMs) {
        mMaxStretchMs = maxStretchMs;
        mMaxReportMs = maxReportMs;
    }

    /** Playing starts at {@code now} (a monotonic clock, ms). True when it wasn't playing a moment ago. */
    public synchronized boolean start(long now) {
        if (mPlayingSince != -1) {
            return false;
        }

        mPlayingSince = now;
        return true;
    }

    public synchronized boolean isPlaying() {
        return mPlayingSince != -1;
    }

    /**
     * Counts the playing since the last time, and goes on counting when {@code stillPlaying}. Returns what it counted
     * now (ms), which also waits for the next report.
     */
    public synchronized long take(long now, boolean stillPlaying) {
        if (mPlayingSince == -1) {
            return 0;
        }

        long ms = Math.max(0, Math.min(now - mPlayingSince, mMaxStretchMs));
        mPlayingSince = stillPlaying ? now : -1;
        mUnreportedMs += ms;
        return ms;
    }

    /** Played and not taken by Hearth yet */
    public synchronized long unreportedMs() {
        return mUnreportedMs;
    }

    /**
     * The next report's worth (at most one report's cap), which leaves the waiting time; 0 when there's less than
     * {@code minMs}. If Hearth doesn't take it, {@link #giveBack} it.
     */
    public synchronized long nextReport(long minMs) {
        if (mUnreportedMs < Math.max(1, minMs)) {
            return 0;
        }

        long ms = Math.min(mUnreportedMs, mMaxReportMs);
        mUnreportedMs -= ms;
        return ms;
    }

    /** Hearth didn't take this report: it waits for the next */
    public synchronized void giveBack(long ms) {
        mUnreportedMs += Math.max(0, ms);
    }
}
