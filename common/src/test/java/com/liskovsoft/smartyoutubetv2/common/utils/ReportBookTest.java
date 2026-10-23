package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class ReportBookTest {
    private static VideoReport report(VideoReport.Target target, String id, VideoReport.Kind kind, String detail, int age) {
        return new VideoReport(target, id, kind, detail, age, "A title", "A channel", 1_700_000_000_000L);
    }

    @Test
    public void aReportSurvivesSavingAndReading() {
        VideoReport original = report(VideoReport.Target.VIDEO, "abc123", VideoReport.Kind.AI_GENERATED, "music", 0);
        VideoReport read = VideoReport.decode(original.encode());

        assertEquals(VideoReport.Target.VIDEO, read.target);
        assertEquals("abc123", read.id);
        assertEquals(VideoReport.Kind.AI_GENERATED, read.kind);
        assertEquals("music", read.detail);
        assertEquals("A title", read.title);
        assertEquals(1_700_000_000_000L, read.timeMs);
    }

    @Test
    public void noDetailStaysNoDetail() {
        VideoReport read = VideoReport.decode(report(VideoReport.Target.CHANNEL, "UC1", VideoReport.Kind.OTHER, null, 0).encode());
        assertNull(read.detail);
    }

    @Test
    public void tabsAndLineBreaksInTitlesCannotBreakTheSave() {
        VideoReport tricky = new VideoReport(VideoReport.Target.VIDEO, "x", VideoReport.Kind.OTHER, null, 0, "a\tb\nc", "d\re", 1);
        ReportBook book = new ReportBook();
        book.add(tricky);

        List<VideoReport> read = ReportBook.decode(book.encode()).reports();
        assertEquals(1, read.size());
        assertEquals("a b c", read.get(0).title);
    }

    @Test
    public void damagedLinesAreSkipped() {
        ReportBook book = ReportBook.decode("garbage\nVIDEO\t\tAI_GENERATED\t\t0\t1\tt\tc\nVIDEO\tid\tNOPE\t\t0\t1\tt\tc\n"
                + report(VideoReport.Target.VIDEO, "ok", VideoReport.Kind.SPONSORED, null, 0).encode() + "\n");
        assertEquals(1, book.reports().size());
        assertEquals("ok", book.reports().get(0).id);
    }

    @Test
    public void reportingAgainReplacesAndPutsItFirst() {
        ReportBook book = new ReportBook();
        book.add(report(VideoReport.Target.VIDEO, "a", VideoReport.Kind.AI_GENERATED, null, 0));
        book.add(report(VideoReport.Target.VIDEO, "b", VideoReport.Kind.AI_GENERATED, null, 0));
        book.add(report(VideoReport.Target.VIDEO, "a", VideoReport.Kind.AI_GENERATED, "voiceover", 0));

        assertEquals(2, book.reports().size());
        assertEquals("a", book.reports().get(0).id);
        assertEquals("voiceover", book.reports().get(0).detail);
    }

    @Test
    public void aVideoAndItsChannelAreSeparateReports() {
        ReportBook book = new ReportBook();
        book.add(report(VideoReport.Target.VIDEO, "same", VideoReport.Kind.AI_GENERATED, null, 0));
        book.add(report(VideoReport.Target.CHANNEL, "same", VideoReport.Kind.AI_GENERATED, null, 0));
        book.add(report(VideoReport.Target.VIDEO, "same", VideoReport.Kind.SPONSORED, null, 0));
        assertEquals(3, book.reports().size());
    }

    @Test
    public void aReportCanBeTakenOff() {
        ReportBook book = new ReportBook();
        VideoReport one = report(VideoReport.Target.VIDEO, "a", VideoReport.Kind.OTHER, null, 0);
        book.add(one);
        assertTrue(book.remove(one));
        assertFalse(book.remove(one));
        assertTrue(book.isEmpty());
    }

    @Test
    public void theBookKeepsTheNewest500() {
        ReportBook book = new ReportBook();

        for (int i = 0; i < 520; i++) {
            book.add(report(VideoReport.Target.VIDEO, "v" + i, VideoReport.Kind.OTHER, null, 0));
        }

        assertEquals(500, book.reports().size());
        assertEquals("v519", book.reports().get(0).id);
    }

    @Test
    public void exportIsOneLinePerReportWithoutPipesInText() {
        ReportBook book = new ReportBook();
        book.add(new VideoReport(VideoReport.Target.VIDEO, "abc", VideoReport.Kind.AGE, null, 13, "Hi | there", "Chan", 1_700_000_000_000L));
        String[] lines = book.export().split("\n");

        assertEquals(2, lines.length);
        assertEquals("video | abc | age |  | 13 | 2023-11-14 | Chan | Hi / there", lines[1]);
    }

    @Test
    public void aReportReadsAsAPersonWouldSayIt() {
        VideoReport ai = new VideoReport(VideoReport.Target.VIDEO, "v", VideoReport.Kind.AI_GENERATED, "music", 0, "Song", "Band", 1);
        VideoReport age = new VideoReport(VideoReport.Target.CHANNEL, "UC", VideoReport.Kind.AGE, null, 13, "", "Band", 1);

        assertEquals("AI generated (music): Song - Band", ai.describe("AI generated", "Not for under %d", "Sponsored", "Other", "Channel"));
        assertEquals("Not for under 13: Channel Band", age.describe("AI generated", "Not for under %d", "Sponsored", "Other", "Channel"));
    }
}
