package com.liskovsoft.smartyoutubetv2.common.utils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * HearthTube: the reports made on this device, newest first. Adding the same thing again (same video or channel, same
 * kind) replaces the earlier one; a person can take any off the list. {@link #export} is the text a person pastes
 * into an issue on the community repo: ids, kinds and tags, with the title and channel for whoever reads them.
 * Plain Java.
 */
public final class ReportBook {
    private static final int MAX = 500;
    private final List<VideoReport> mReports = new ArrayList<>();

    public ReportBook() {
    }

    public ReportBook(List<VideoReport> reports) {
        mReports.addAll(reports);
    }

    /** Newest first */
    public List<VideoReport> reports() {
        return Collections.unmodifiableList(mReports);
    }

    public void add(VideoReport report) {
        remove(report);
        mReports.add(0, report);

        while (mReports.size() > MAX) {
            mReports.remove(mReports.size() - 1);
        }
    }

    /** True if it was on the list */
    public boolean remove(VideoReport report) {
        return mReports.removeIf(report::isSameAs);
    }

    public void clear() {
        mReports.clear();
    }

    public boolean isEmpty() {
        return mReports.isEmpty();
    }

    /** The saved form: one report per line */
    public String encode() {
        StringBuilder text = new StringBuilder();

        for (VideoReport report : mReports) {
            text.append(report.encode()).append('\n');
        }

        return text.toString();
    }

    public static ReportBook decode(String text) {
        return new ReportBook(VideoReport.decodeAll(text));
    }

    /**
     * The text to paste into an issue: one line per report, "target | id | kind | detail | under-age | date | channel |
     * title". The date is UTC, to the day.
     */
    public String export() {
        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        day.setTimeZone(TimeZone.getTimeZone("UTC"));
        StringBuilder text = new StringBuilder("target | id | kind | detail | under-age | date | channel | title\n");

        for (VideoReport report : mReports) {
            text.append(report.target.name().toLowerCase(Locale.US)).append(" | ")
                    .append(report.id).append(" | ")
                    .append(report.kind.name().toLowerCase(Locale.US)).append(" | ")
                    .append(report.detail != null ? report.detail : "").append(" | ")
                    .append(report.age > 0 ? String.valueOf(report.age) : "").append(" | ")
                    .append(day.format(new Date(report.timeMs))).append(" | ")
                    .append(report.channel.replace('|', '/')).append(" | ")
                    .append(report.title.replace('|', '/')).append('\n');
        }

        return text.toString();
    }
}
