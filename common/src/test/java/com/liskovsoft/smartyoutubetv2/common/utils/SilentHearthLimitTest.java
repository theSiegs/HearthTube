package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
    public void aNewDayHasNoKeptLimit() {
        mLimit.remember("alex", 5 * MINUTE, MON, 1_000);
        assertNull(mLimit.msLeft(TUE));
        // and it was let go: an earlier date doesn't bring it back
        assertNull(mLimit.msLeft(MON));
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
    public void unreadableIsIgnored() {
        mLimit.restore("junk");
        mLimit.restore("a|b|c|d");
        assertNull(mLimit.msLeft(MON));
        assertNull(mLimit.save());
    }
}
