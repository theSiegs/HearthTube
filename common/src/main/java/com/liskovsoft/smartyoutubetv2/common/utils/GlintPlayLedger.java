package com.liskovsoft.smartyoutubetv2.common.utils;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * HearthTube: the playing time sent to Glint, by report, so that Glint's minutes left (counted up to its {@code asOf},
 * the ts of the last report it counted) can be brought up to now: less what played here since. Plain Java, so it's
 * unit-tested; {@link GlintChannel} feeds it.
 */
public final class GlintPlayLedger {
    /** At a report every few minutes at most, a couple of hours of reports */
    private static final int MAX_REPORTS = 32;
    /** {ts, total sent before this report, total sent with it} */
    private final ArrayDeque<long[]> mReports = new ArrayDeque<>();
    private long mSentTotal;

    /** A report went to Glint at {@code ts} (epoch ms), carrying {@code playedMs} */
    public synchronized void sent(long ts, long playedMs) {
        long before = mSentTotal;
        mSentTotal += Math.max(0, playedMs);
        mReports.addLast(new long[] {ts, before, mSentTotal});

        while (mReports.size() > MAX_REPORTS) {
            mReports.removeFirst();
        }
    }

    /**
     * What played here that Glint hadn't counted at {@code asOf}: what was sent after that report, and
     * {@code unsentMs} not sent yet. An asOf older than every report kept counts from the oldest; no asOf (Glint
     * counted nothing of ours) counts only what's unsent, as what was sent may be from an earlier day.
     */
    public synchronized long playedSince(@Nullable Long asOf, long unsentMs) {
        long unsent = Math.max(0, unsentMs);

        if (asOf == null || asOf <= 0 || mReports.isEmpty()) {
            return unsent;
        }

        long baseline = mReports.peekFirst()[1];

        for (Iterator<long[]> it = mReports.descendingIterator(); it.hasNext(); ) {
            long[] report = it.next();

            if (report[0] <= asOf) {
                baseline = report[2];
                break;
            }
        }

        return mSentTotal - baseline + unsent;
    }
}
