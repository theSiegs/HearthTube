package com.liskovsoft.smartyoutubetv2.common.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.common.prefs.WatchLogData.Entry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

public class WatchLogDataTest {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    /** 2026-10-27 14:05:00 UTC */
    private static final long T0 = 1_793_109_900_000L;

    @Test
    public void aLineReadsForAPerson() {
        assertEquals("2026-10-27 14:05  Sailing yacht - Boats Inc  https://youtu.be/abc123",
                WatchLogData.line(new Entry(T0, "abc123", "Sailing yacht", "Boats Inc"), UTC));
    }

    @Test
    public void aLineWithoutTitleOrChannelStillSaysWhat() {
        assertEquals("2026-10-27 14:05  abc123  https://youtu.be/abc123", WatchLogData.line(new Entry(T0, "abc123", "", ""), UTC));
    }

    @Test
    public void theTextIsNewestFirst() {
        String text = WatchLogData.toText(Arrays.asList(new Entry(T0, "a", "First", ""), new Entry(T0 + 60_000, "b", "Second", "")), UTC);

        assertTrue(text.indexOf("Second") < text.indexOf("First"));
    }

    @Test
    public void theJsonEscapesWhatItMust() {
        String json = WatchLogData.toJson(Arrays.asList(new Entry(T0, "abc123", "A \"quoted\" title\nnew line \\", "Chan")));

        assertTrue(json.contains("\"time\":\"2026-10-27T14:05:00Z\""));
        assertTrue(json.contains("\"title\":\"A \\\"quoted\\\" title\\nnew line \\\\\""));
        assertTrue(json.contains("\"url\":\"https://youtu.be/abc123\""));
    }

    @Test
    public void anEmptyLogIsStillValidJson() {
        assertEquals("{\"started\":[\n]}\n", WatchLogData.toJson(new ArrayList<Entry>()));
    }

    @Test
    public void anEntrySurvivesSaving() {
        Entry entry = WatchLogData.parse(WatchLogData.serialize(new Entry(T0, "abc123", "Sailing yacht", "Boats Inc")));

        assertEquals(T0, entry.timeMs);
        assertEquals("abc123", entry.videoId);
        assertEquals("Sailing yacht", entry.title);
        assertEquals("Boats Inc", entry.channel);
    }

    @Test
    public void noTitleOrChannelSurvivesSaving() {
        Entry entry = WatchLogData.parse(WatchLogData.serialize(new Entry(T0, "abc123", null, null)));

        assertEquals("abc123", entry.videoId);
        assertEquals("abc123", entry.title);
    }

    @Test
    public void somethingThatIsNotAnEntryIsIgnored() {
        assertNull(WatchLogData.parse(null));
        assertNull(WatchLogData.parse(""));
        assertNull(WatchLogData.parse("junk"));
    }

    @Test
    public void theSameVideoAMomentLaterIsOneEntry() {
        List<Entry> entries = Arrays.asList(new Entry(T0, "a", "A", ""), new Entry(T0 + 60_000, "b", "B", ""));

        assertTrue(WatchLogData.isRepeat(entries, "a", T0 + 5 * 60_000));
        assertFalse(WatchLogData.isRepeat(entries, "c", T0 + 5 * 60_000));
    }

    @Test
    public void theSameVideoMuchLaterIsAnotherEntry() {
        List<Entry> entries = Arrays.asList(new Entry(T0, "a", "A", ""));

        assertFalse(WatchLogData.isRepeat(entries, "a", T0 + WatchLogData.SAME_VIDEO_MS + 1));
    }
}
