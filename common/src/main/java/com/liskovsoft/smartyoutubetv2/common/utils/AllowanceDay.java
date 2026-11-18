package com.liskovsoft.smartyoutubetv2.common.utils;

import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * HearthTube: which day it is for a daily allowance, so that changing the device's date or time zone doesn't hand out a
 * fresh one. Plain Java, so it's unit-tested; the caller gives the wall clock, a monotonic clock
 * (SystemClock.elapsedRealtime) and a boot id.
 * <ul>
 * <li>Within one boot, the monotonic clock says how far the wall clock should have moved. A wall clock that jumped
 * differently (by more than {@link #TOLERANCE_MS}: a daylight saving change or a small sync stay under) was changed
 * by hand, and is ignored.</li>
 * <li>The day never goes back: a date earlier than the one kept is the same day, so moving the date back and forth
 * doesn't reset it either.</li>
 * <li>A day lasts at least {@link #MIN_DAY_MS} from its start (its midnight, or when it began if that was later). A time
 * zone moved ahead, to where it's tomorrow already, waits for that; so does moving it back and forth.</li>
 * <li>After a reboot nothing can vouch for the wall clock: a later date counts (the phone may have been off overnight),
 * an earlier one doesn't.</li>
 * </ul>
 */
public final class AllowanceDay {
    public static final long TOLERANCE_MS = 65 * 60_000;
    /** The shortest day: a spring-forward day is 23 hours */
    public static final long MIN_DAY_MS = 23 * 60 * 60_000;
    /** The device's zone, when it's fixed (tests); null: whatever the device says at the time */
    @Nullable
    private final TimeZone mDeviceZone;
    private String mDay;
    /** The wall clock as trusted at {@link #mElapsed}, ms since the epoch */
    private long mTrustedWall;
    private long mElapsed;
    private long mBoot;
    /** When {@link #mDay} started (ms since the epoch); 0 when not known (saved by an older HearthTube) */
    private long mDayStart;

    public AllowanceDay() {
        this(null);
    }

    public AllowanceDay(@Nullable TimeZone deviceZone) {
        mDeviceZone = deviceZone;
    }

    /** The day it is now (yyyy-MM-dd; later days sort after earlier ones), as far as this can tell */
    public synchronized String today(long wallMs, long elapsedMs, long boot) {
        return today(wallMs, elapsedMs, boot, mDeviceZone != null ? mDeviceZone : TimeZone.getDefault());
    }

    /** As {@link #today(long, long, long)}, with the device's time zone right now */
    public synchronized String today(long wallMs, long elapsedMs, long boot, TimeZone zone) {
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
        String day = format(trusted, zone);

        if (mDay == null) {
            mDay = day;
            mDayStart = midnight(day, zone, trusted);
        } else if (day.compareTo(mDay) > 0 && trusted >= mDayStart + MIN_DAY_MS) {
            mDayStart = mDayStart > 0 ? Math.max(midnight(day, zone, trusted), mDayStart + MIN_DAY_MS)
                    : midnight(day, zone, trusted);
            mDay = day;
        }

        return mDay;
    }

    private static SimpleDateFormat dateFormat(TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(zone);
        return format;
    }

    private static String format(long ms, TimeZone zone) {
        return dateFormat(zone).format(new Date(ms));
    }

    /** The start of {@code day} in {@code zone}, or {@code fallback} if it can't be read */
    private static long midnight(String day, TimeZone zone, long fallback) {
        try {
            return dateFormat(zone).parse(day).getTime();
        } catch (ParseException | NullPointerException e) {
            return fallback;
        }
    }

    /** day|trustedWall|elapsed|boot|dayStart, to keep across restarts */
    public synchronized String save() {
        return (mDay != null ? mDay : "") + "|" + mTrustedWall + "|" + mElapsed + "|" + mBoot + "|" + mDayStart;
    }

    /** Takes back what {@link #save} gave (also one saved without the day's start); ignores anything unreadable */
    public synchronized void restore(@Nullable String saved) {
        String[] parts = saved != null ? saved.split("\\|", -1) : new String[0];

        if ((parts.length != 4 && parts.length != 5) || parts[0].isEmpty()) {
            return;
        }

        try {
            mTrustedWall = Long.parseLong(parts[1]);
            mElapsed = Long.parseLong(parts[2]);
            mBoot = Long.parseLong(parts[3]);
            mDayStart = parts.length == 5 ? Long.parseLong(parts[4]) : 0;
            mDay = parts[0];
        } catch (NumberFormatException e) {
            // Unreadable: start afresh
            mDay = null;
            mDayStart = 0;
        }
    }
}
