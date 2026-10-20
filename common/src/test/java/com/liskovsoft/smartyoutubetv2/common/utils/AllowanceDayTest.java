package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class AllowanceDayTest {
    private static final long MINUTE = 60_000;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final TimeZone ZONE = TimeZone.getTimeZone("UTC");

    private final AllowanceDay mDay = new AllowanceDay(ZONE);

    private static long at(int day, int hour, int minute) {
        Calendar c = Calendar.getInstance(ZONE);
        c.clear();
        c.set(2026, Calendar.OCTOBER, day, hour, minute);
        return c.getTimeInMillis();
    }

    @Test
    public void firstLookTakesTheDeviceDate() {
        assertEquals("2026-10-19", mDay.today(at(19, 10, 0), 1_000, 7));
    }

    @Test
    public void midnightRolloverIsGenuine() {
        mDay.today(at(19, 23, 50), 0, 7);
        assertEquals("2026-10-19", mDay.today(at(19, 23, 59), 9 * MINUTE, 7));
        assertEquals("2026-10-20", mDay.today(at(20, 0, 2), 12 * MINUTE, 7));
    }

    @Test
    public void movingTheDateForwardDoesNotRoll() {
        mDay.today(at(19, 10, 0), 0, 7);
        // A minute later the device says it's the 21st
        assertEquals("2026-10-19", mDay.today(at(21, 10, 1), MINUTE, 7));
        // and it stays so while the wrong date stays
        assertEquals("2026-10-19", mDay.today(at(21, 10, 30), 30 * MINUTE, 7));
    }

    @Test
    public void movingItBackAndForthDoesNotResetEither() {
        mDay.today(at(19, 10, 0), 0, 7);
        assertEquals("2026-10-19", mDay.today(at(18, 10, 1), MINUTE, 7));
        assertEquals("2026-10-19", mDay.today(at(19, 10, 2), 2 * MINUTE, 7));
        assertEquals("2026-10-19", mDay.today(at(25, 10, 3), 3 * MINUTE, 7));
        assertEquals("2026-10-19", mDay.today(at(19, 10, 4), 4 * MINUTE, 7));
    }

    @Test
    public void theRealNextDayStillComesAfterTheDateWasMoved() {
        mDay.today(at(19, 10, 0), 0, 7);
        mDay.today(at(25, 10, 1), MINUTE, 7);
        // 14 hours later by the monotonic clock: it really is the 20th now
        assertEquals("2026-10-20", mDay.today(at(25, 0, 0), 14 * HOUR, 7));
    }

    @Test
    public void aSmallClockSyncOrDaylightSavingIsKept() {
        mDay.today(at(19, 22, 30), 0, 7);
        // the clock was set an hour on (daylight saving) and it is past midnight now
        assertEquals("2026-10-20", mDay.today(at(20, 0, 40), 70 * MINUTE, 7));
    }

    @Test
    public void afterAnOvernightRebootALaterDateCounts() {
        mDay.today(at(19, 22, 0), 5 * HOUR, 7);
        assertEquals("2026-10-20", mDay.today(at(20, 7, 0), 2 * MINUTE, 8));
    }

    @Test
    public void afterARebootAnEarlierDateDoesNot() {
        mDay.today(at(19, 22, 0), 5 * HOUR, 7);
        assertEquals("2026-10-19", mDay.today(at(10, 7, 0), 2 * MINUTE, 8));
        // the wrong date can't pull it forward while it is behind
        assertEquals("2026-10-19", mDay.today(at(10, 8, 0), 62 * MINUTE, 8));
    }

    @Test
    public void rebootWithoutABootIdIsSeenByTheClockGoingBack() {
        mDay.today(at(19, 22, 0), 5 * HOUR, -1);
        assertEquals("2026-10-19", mDay.today(at(12, 22, 0), MINUTE, -1));
    }

    @Test
    public void savedAndRestored() {
        mDay.today(at(19, 10, 0), 100_000, 7);
        AllowanceDay restored = new AllowanceDay(ZONE);
        restored.restore(mDay.save());
        assertEquals("2026-10-19", restored.today(at(21, 10, 1), 100_000 + MINUTE, 7));
        assertEquals("2026-10-20", restored.today(at(20, 10, 0), 100_000 + DAY, 7));
    }

    @Test
    public void unreadableStartsAfresh() {
        mDay.restore("junk");
        mDay.restore("2026-10-19|x|1|2");
        assertEquals("2026-10-22", mDay.today(at(22, 10, 0), 0, 7));
    }
}
