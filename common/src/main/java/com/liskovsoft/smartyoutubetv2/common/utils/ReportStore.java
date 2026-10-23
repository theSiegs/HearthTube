package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.util.ArrayList;
import java.util.List;

/**
 * HearthTube: the reports made on this device ({@link ReportBook}), kept in its own prefs file so the local backup
 * takes them along. Reporting changes nothing on screen: the list is for whoever sends it on to the community lists.
 * One list per Android user (a kids profile on the TV is its own), no name of who reported.
 */
public final class ReportStore {
    private static final String PREFS = "hearthtube_reports";
    private static final String BOOK = "book";

    private ReportStore() {
    }

    /** Reports the video (or its channel, when {@code channel}), replacing an earlier report of the same kind */
    public static VideoReport report(Context context, Video video, boolean channel, VideoReport.Kind kind, String detail, int age) {
        String id = channel ? video.channelId : video.videoId;

        if (context == null || id == null || id.isEmpty()) {
            return null;
        }

        VideoReport report = new VideoReport(channel ? VideoReport.Target.CHANNEL : VideoReport.Target.VIDEO, id, kind, detail, age,
                video.getTitle(), video.getAuthor(), System.currentTimeMillis());
        synchronized (ReportStore.class) {
            ReportBook book = read(context);
            book.add(report);
            write(context, book);
        }

        return report;
    }

    public static synchronized List<VideoReport> getReports(Context context) {
        return new ArrayList<>(read(context).reports());
    }

    public static synchronized void remove(Context context, VideoReport report) {
        ReportBook book = read(context);

        if (book.remove(report)) {
            write(context, book);
        }
    }

    /** The text to paste into an issue on the community repo */
    public static synchronized String export(Context context) {
        return read(context).export();
    }

    private static ReportBook read(Context context) {
        return ReportBook.decode(prefs(context).getString(BOOK, ""));
    }

    private static void write(Context context, ReportBook book) {
        prefs(context).edit().putString(BOOK, book.encode()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
