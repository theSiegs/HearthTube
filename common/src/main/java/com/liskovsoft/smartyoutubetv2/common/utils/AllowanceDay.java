package com.liskovsoft.smartyoutubetv2.common.utils;

import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * HearthTube: which day it is for a daily allowance, so that changing the device's date doesn't hand out a fresh one.
 * Plain Java, so it's unit-tested; the caller gives the wall clock, a monotonic clock (SystemClock.elapsedRealtime)
 * and a boot id.
 * <ul>
 * <li>Within one boot, the monotonic clock says how far the wall clock should have moved. A wall clock that jumped
 * differently (by more than {@link #TOLERANCE_MS}: a time zone or daylight saving change or a small sync stay under)
 * was changed by hand, and is ignored.</li>
 * <li>The day never goes back: a date earlier than the one kept is the same day, so moving the date back and forth
 * doesn't reset it either.</li>
 * <li>After a reboot nothing can vouch for the wall clock: a later date counts (the phone may have been off overnight),
 * an earlier one doesn't.</li>
 * </ul>
 */
public final class AllowanceDay {
    public static final long TOLERANCE_MS = 65 * 60_000;
    private final SimpleDateFormat mFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private String mDay;
    /** The wall clock as trusted at {@link #mElapsed}, ms since the epoch */
    private long mTrustedWall;
    private long mElapsed;
    private long mBoot;

    public AllowanceDay() {
        this(TimeZone.getDefault());
    }

    public AllowanceDay(TimeZone zone) {
        mFormat.setTimeZone(zone);
    }

    /** The day it is now (yyyy-MM-dd; later days sort after earlier ones), as far as this can tell */
    public synchronized String today(long wallMs, long elapsedMs, long boot) {
        long trusted;

        if (mDay == null) {
            trusted = wallMs;
        } else if (boot == mBoot && elapsedMs >= mElapsed) {
            long expected = mTrustedWall + (elapsedMs - mElapsed);
            trusted = Math.abs(wallMs - expected) <= TOLERANCE_MS ? wallMs : expected;
        } else {
            trusted = Math.max(wallMs, mTrustedWall);
        }

        mTrustedWall = trusted;
        mElapsed = elapsedMs;
        mBoot = boot;
        String day = mFormat.format(new Date(trusted));

        if (mDay == null || day.compareTo(mDay) > 0) {
            mDay = day;
        }

        return mDay;
    }

    /** day|trustedWall|elapsed|boot, to keep across restarts */
    public synchronized String save() {
        return (mDay != null ? mDay : "") + "|" + mTrustedWall + "|" + mElapsed + "|" + mBoot;
    }

    /** Takes back what {@link #save} gave; ignores anything unreadable */
    public synchronized void restore(@Nullable String saved) {
        String[] parts = saved != null ? saved.split("\\|", -1) : new String[0];

        if (parts.length != 4 || parts[0].isEmpty()) {
            return;
        }

        try {
            mTrustedWall = Long.parseLong(parts[1]);
            mElapsed = Long.parseLong(parts[2]);
            mBoot = Long.parseLong(parts[3]);
            mDay = parts[0];
        } catch (NumberFormatException e) {
            // Unreadable: start afresh
            mDay = null;
        }
    }
}
