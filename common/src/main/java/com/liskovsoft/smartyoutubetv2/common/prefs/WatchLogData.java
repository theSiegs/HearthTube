package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * HearthTube: what a kids profile started watching, for a parent to read (or hand to an AI agent for a safety and content
 * review). One line per video started, newest last, kept per profile. It lives apart from YouTube's history on purpose:
 * a kid removing a video from history, or hiding it, doesn't take it out of here. Only what was started is kept, not how
 * long it was watched.
 */
public class WatchLogData implements ProfileChangeListener {
    private static final String WATCH_LOG_DATA = "watch_log";
    private static final String WATCH_LOG_SENT = "watch_log_sent";
    static final int MAX = 2000;
    /** The same video started again within this time is one entry (a resume, a reload) */
    static final long SAME_VIDEO_MS = 30 * 60_000;
    @SuppressLint("StaticFieldLeak")
    private static WatchLogData sInstance;
    private final AppPrefs mPrefs;
    private final List<Entry> mEntries = new ArrayList<>();
    /** The time of the newest entry Hearth took (the parent channel); later ones are sent again */
    private long mSentMs;
    private final Runnable mPersistStateInt = this::persistStateInt;

    public static class Entry {
        public final long timeMs;
        public final String videoId;
        public final String title;
        public final String channel;
        /** Empty when not known (entries saved before it was kept) */
        public final String channelId;

        public Entry(long timeMs, String videoId, String title, String channel) {
            this(timeMs, videoId, title, channel, null);
        }

        public Entry(long timeMs, String videoId, String title, String channel, String channelId) {
            this.timeMs = timeMs;
            this.videoId = videoId;
            this.title = title == null ? "" : title;
            this.channel = channel == null ? "" : channel;
            this.channelId = channelId == null ? "" : channelId;
        }
    }

    private WatchLogData(Context context) {
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        restoreState();
    }

    public static WatchLogData instance(Context context) {
        if (sInstance == null) {
            sInstance = new WatchLogData(context.getApplicationContext());
        }

        return sInstance;
    }

    /** A video started playing in this profile */
    public synchronized void record(String videoId, String title, String channel, String channelId) {
        record(videoId, title, channel, channelId, System.currentTimeMillis());
    }

    synchronized void record(String videoId, String title, String channel, long nowMs) {
        record(videoId, title, channel, null, nowMs);
    }

    synchronized void record(String videoId, String title, String channel, String channelId, long nowMs) {
        if (videoId == null || videoId.isEmpty()) {
            return;
        }

        if (isRepeat(mEntries, videoId, nowMs)) {
            return;
        }

        mEntries.add(new Entry(nowMs, videoId, title, channel, channelId));

        while (mEntries.size() > MAX) {
            mEntries.remove(0);
        }

        persistState();
    }

    /** The same video was started a moment ago (looking back past other videos, so a short clip in between doesn't hide it) */
    static boolean isRepeat(List<Entry> entries, String videoId, long nowMs) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);

            if (nowMs - entry.timeMs > SAME_VIDEO_MS) {
                return false;
            }

            if (videoId.equals(entry.videoId)) {
                return true;
            }
        }

        return false;
    }

    /** Entries Hearth hasn't taken yet, oldest first, at most {@code limit} */
    public synchronized List<Entry> getUnsent(int limit) {
        List<Entry> unsent = new ArrayList<>();

        for (Entry entry : mEntries) {
            if (entry.timeMs > mSentMs && unsent.size() < limit) {
                unsent.add(entry);
            }
        }

        return unsent;
    }

    /** Hearth took everything up to this time */
    public synchronized void markSent(long timeMs) {
        if (timeMs > mSentMs) {
            mSentMs = timeMs;
            persistState();
        }
    }

    /** Oldest first */
    public synchronized List<Entry> getEntries() {
        return new ArrayList<>(mEntries);
    }

    public synchronized int size() {
        return mEntries.size();
    }

    /** One entry a person can read: "2026-10-27 14:05  Title - Channel  https://youtu.be/id" */
    static String line(Entry entry, TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        format.setTimeZone(zone);

        StringBuilder sb = new StringBuilder(format.format(new Date(entry.timeMs)));
        sb.append("  ").append(entry.title.isEmpty() ? entry.videoId : entry.title);

        if (!entry.channel.isEmpty()) {
            sb.append(" - ").append(entry.channel);
        }

        return sb.append("  https://youtu.be/").append(entry.videoId).toString();
    }

    public static String lineOf(Entry entry) {
        return line(entry, TimeZone.getDefault());
    }

    /** The whole log for a person: a header and a line per video, newest first */
    public static String toText(List<Entry> entries, TimeZone zone) {
        StringBuilder sb = new StringBuilder("Videos started in this profile (newest first)\n");

        for (int i = entries.size() - 1; i >= 0; i--) {
            sb.append(line(entries.get(i), zone)).append('\n');
        }

        return sb.toString();
    }

    /** The same for an AI agent: a JSON array, oldest first, time in UTC (ISO 8601) */
    public static String toJson(List<Entry> entries) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));

        StringBuilder sb = new StringBuilder("{\"started\":[");

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);

            sb.append(i == 0 ? "\n" : ",\n");
            sb.append("{\"time\":\"").append(format.format(new Date(entry.timeMs)))
                    .append("\",\"videoId\":").append(quote(entry.videoId))
                    .append(",\"title\":").append(quote(entry.title))
                    .append(",\"channel\":").append(quote(entry.channel))
                    .append(",\"channelId\":").append(quote(entry.channelId))
                    .append(",\"url\":").append(quote("https://youtu.be/" + entry.videoId)).append('}');
        }

        return sb.append("\n]}\n").toString();
    }

    static String quote(String text) {
        StringBuilder sb = new StringBuilder("\"");

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(Locale.US, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }

        return sb.append('"').toString();
    }

    /** One saved entry; null when it isn't one */
    static Entry parse(String spec) {
        String[] fields = Helpers.splitObj(spec);

        if (fields == null || fields.length < 2) {
            return null;
        }

        long time = Helpers.parseLong(fields[0], -1);
        String id = Helpers.parseStr(fields[1]);

        if (time < 0 || id == null) {
            return null;
        }

        String channelId = fields.length > 4 ? Helpers.parseStr(fields[4]) : null;

        return new Entry(time, id, fields.length > 2 ? Helpers.parseStr(fields[2]) : null, fields.length > 3 ? Helpers.parseStr(fields[3]) : null,
                "-".equals(channelId) ? null : channelId);
    }

    static String serialize(Entry entry) {
        // An empty field would not survive being saved (it is stored as a space)
        return Helpers.mergeObj(entry.timeMs, entry.videoId, entry.title.isEmpty() ? entry.videoId : entry.title,
                entry.channel.isEmpty() ? "-" : entry.channel, entry.channelId.isEmpty() ? "-" : entry.channelId);
    }

    private synchronized void restoreState() {
        String[] split = Helpers.splitData(mPrefs.getProfileData(WATCH_LOG_DATA));

        mEntries.clear();
        mSentMs = Helpers.parseLong(mPrefs.getProfileData(WATCH_LOG_SENT), 0);

        for (String spec : Helpers.parseList(split, 0, item -> item)) {
            Entry entry = parse(spec);

            if (entry != null) {
                mEntries.add(new Entry(entry.timeMs, entry.videoId, entry.title, "-".equals(entry.channel) ? "" : entry.channel, entry.channelId));
            }
        }
    }

    public void persistNow() {
        Utils.post(mPersistStateInt);
    }

    private void persistState() {
        Utils.postDelayed(mPersistStateInt, 2_000);
    }

    private synchronized void persistStateInt() {
        List<String> specs = new ArrayList<>();

        for (Entry entry : mEntries) {
            specs.add(serialize(entry));
        }

        mPrefs.setProfileData(WATCH_LOG_DATA, Helpers.mergeData(specs));
        mPrefs.setProfileData(WATCH_LOG_SENT, String.valueOf(mSentMs));
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }
}
