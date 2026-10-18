package com.liskovsoft.smartyoutubetv2.common.utils;

import androidx.annotation.Nullable;

/**
 * HearthTube: a daily YouTube limit a parent sets on the device itself, for a phone or tablet with no Hearth to give
 * one ({@link HearthAllowance} uses it only then: with Hearth the limit lives there, in one place). Counts what played
 * today and starts again each day. Plain Java, so it's unit-tested; the caller says which day it is.
 */
public final class LocalAllowance {
    private static final long MINUTE_MS = 60_000;

    private int mLimitMinutes;
    private String mDay;
    private long mPlayedMs;

    /** Minutes a day; 0 or less is no limit */
    public synchronized void setLimitMinutes(int minutes) {
        mLimitMinutes = Math.max(0, minutes);
    }

    public synchronized int getLimitMinutes() {
        return mLimitMinutes;
    }

    /** Played this long (ms) on {@code day} (any stable text for the date, e.g. 2026-10-18) */
    public synchronized void played(String day, long ms) {
        rollTo(day);

        if (ms > 0) {
            mPlayedMs += ms;
        }
    }

    /** Playing time left today (ms); null when there's no limit */
    @Nullable
    public synchronized Long msLeft(String day) {
        rollTo(day);

        if (mLimitMinutes <= 0) {
            return null;
        }

        return Math.max(0, mLimitMinutes * MINUTE_MS - mPlayedMs);
    }

    /** May it play? True when there's no limit or some of it is left */
    public synchronized boolean mayPlay(String day) {
        Long left = msLeft(day);
        return left == null || left > 0;
    }

    private void rollTo(String day) {
        if (day != null && !day.equals(mDay)) {
            mDay = day;
            mPlayedMs = 0;
        }
    }

    /** day|limit|playedMs, to keep across restarts */
    public synchronized String save() {
        return (mDay != null ? mDay : "") + "|" + mLimitMinutes + "|" + mPlayedMs;
    }

    /** Takes back what {@link #save} gave; ignores anything unreadable */
    public synchronized void restore(@Nullable String saved) {
        String[] parts = saved != null ? saved.split("\\|", -1) : new String[0];

        if (parts.length != 3) {
            return;
        }

        try {
            int limit = Integer.parseInt(parts[1]);
            long played = Long.parseLong(parts[2]);
            mDay = parts[0].isEmpty() ? null : parts[0];
            mLimitMinutes = Math.max(0, limit);
            mPlayedMs = Math.max(0, played);
        } catch (NumberFormatException e) {
            // Unreadable: start afresh
        }
    }
}
