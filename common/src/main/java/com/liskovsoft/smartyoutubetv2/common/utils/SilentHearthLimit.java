package com.liskovsoft.smartyoutubetv2.common.utils;

import androidx.annotation.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HearthTube: the last allowance Hearth gave for each profile, kept for the rest of that day, so that a Hearth that
 * goes silent (suspended, its agent not connected, stopped) isn't a way out of the limit. HearthTube counts down what
 * it plays itself; Hearth gets the full figure when it is back. Plain Java, so it's unit-tested;
 * {@link HearthAllowance} feeds it. A profile Hearth never gave a limit for has none here.
 */
public final class SilentHearthLimit {
    private static final class Entry {
        final String day;
        final long checkedAt;
        long msLeft;

        Entry(String day, long checkedAt, long msLeft) {
            this.day = day;
            this.checkedAt = checkedAt;
            this.msLeft = msLeft;
        }
    }

    private final Map<String, Entry> mEntries = new LinkedHashMap<>();

    /**
     * Hearth gave this profile {@code msLeft} of playing time on {@code day}, at {@code checkedAt} (epoch ms); null
     * msLeft is no limit, which forgets what was kept.
     */
    public synchronized void remember(String profileKey, @Nullable Long msLeft, String day, long checkedAt) {
        if (profileKey == null || day == null) {
            return;
        }

        if (msLeft == null) {
            mEntries.remove(profileKey);
        } else {
            mEntries.put(profileKey, new Entry(day, checkedAt, Math.max(0, msLeft)));
        }
    }

    /** HearthTube played this long (ms): off every kept allowance, as without Hearth it can't tell which profile it was */
    public synchronized void played(long ms) {
        if (ms <= 0) {
            return;
        }

        for (Entry entry : mEntries.values()) {
            entry.msLeft = Math.max(0, entry.msLeft - ms);
        }
    }

    /**
     * Playing time left today (ms) by what was kept: the least of the profiles', as it can't tell which one is
     * watching. Null when nothing is kept for {@code day}. Older days are let go.
     */
    @Nullable
    public synchronized Long msLeft(String day) {
        Long least = null;

        for (Iterator<Entry> it = mEntries.values().iterator(); it.hasNext(); ) {
            Entry entry = it.next();

            if (day != null && day.equals(entry.day)) {
                least = least == null ? entry.msLeft : Math.min(least, entry.msLeft);
            } else if (day != null && day.compareTo(entry.day) > 0) {
                it.remove();
            }
        }

        return least;
    }

    /** key|day|msLeft|checkedAt, one per profile, to keep across restarts; null when none */
    @Nullable
    public synchronized String save() {
        if (mEntries.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, Entry> e : mEntries.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(encode(e.getKey())).append('|').append(e.getValue().day).append('|').append(e.getValue().msLeft)
                    .append('|').append(e.getValue().checkedAt);
        }

        return sb.toString();
    }

    /** Takes back what {@link #save} gave; ignores anything unreadable */
    public synchronized void restore(@Nullable String saved) {
        if (saved == null || saved.isEmpty()) {
            return;
        }

        for (String item : saved.split(";")) {
            String[] parts = item.split("\\|", -1);

            if (parts.length != 4) {
                continue;
            }

            try {
                String key = decode(parts[0]);

                if (key != null && !parts[1].isEmpty()) {
                    mEntries.put(key, new Entry(parts[1], Long.parseLong(parts[3]), Math.max(0, Long.parseLong(parts[2]))));
                }
            } catch (NumberFormatException e) {
                // Unreadable: skip it
            }
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    @Nullable
    private static String decode(String value) {
        try {
            return value.isEmpty() ? null : URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return null;
        }
    }
}
