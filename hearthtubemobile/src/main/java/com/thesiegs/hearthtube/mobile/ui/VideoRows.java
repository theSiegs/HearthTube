package com.thesiegs.hearthtube.mobile.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.HearthSections;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The groups a section arrives in (a grid is one; Home is a few rows, each with a title), kept in arrival order. With
 * two or more titled rows the section has a strip of sub-pills, one per row, and shows the picked row's videos, like
 * the TV's strip under its tabs; otherwise it is flattened into a header (the row's title, when it has one) and its
 * videos. Plain Java, so the way groups are applied is tested apart from Android.
 */
public class VideoRows {
    private final Map<Object, Row> mRows = new LinkedHashMap<>();
    /** The row on show when there is a strip; null is the first */
    private Object mSelectedKey;

    private static final class Row {
        String title;
        /** A channel's picture (Subscriptions): its pill is a circle */
        String iconUrl;
        /** What the row is, for the screen (a channel's menu); opaque here */
        Object tag;
        final List<Video> videos = new ArrayList<>();
    }

    /** One sub-pill */
    public static final class Pill {
        public final Object key;
        public final String title;
        @Nullable
        public final String iconUrl;
        @Nullable
        public final Object tag;

        Pill(Object key, String title, @Nullable String iconUrl, @Nullable Object tag) {
            this.key = key;
            this.title = title;
            this.iconUrl = iconUrl;
            this.tag = tag;
        }
    }

    public void clear() {
        mRows.clear();
        mSelectedKey = null;
    }

    public void update(@NonNull VideoGroup group) {
        String iconUrl = null;
        Object tag = null;

        if (group.getMediaGroup() instanceof HearthSections.TitledGroup) {
            HearthSections.TitledGroup titled = (HearthSections.TitledGroup) group.getMediaGroup();
            iconUrl = titled.getIconUrl();
            tag = titled;
        }

        update(keyOf(group), group.getTitle(), iconUrl, tag, group.getAction(), group.getVideos());
    }

    /** A group's identity: its position among the section's groups, else its title */
    private static Object keyOf(VideoGroup group) {
        return group.getPosition() + "/" + group.getId();
    }

    void update(Object key, String title, int action, List<Video> videos) {
        update(key, title, null, null, action, videos);
    }

    void update(Object key, String title, @Nullable String iconUrl, @Nullable Object tag, int action, List<Video> videos) {
        Row row = mRows.get(key);

        switch (action) {
            case VideoGroup.ACTION_REPLACE:
                row = new Row();
                row.title = title;
                row.iconUrl = iconUrl;
                row.tag = tag;
                row.videos.addAll(videos);
                mRows.put(key, row);
                break;
            case VideoGroup.ACTION_PREPEND:
                row = row != null ? row : newRow(key, title, iconUrl, tag);
                row.videos.addAll(0, videos);
                break;
            case VideoGroup.ACTION_REMOVE:
                if (row != null) {
                    row.videos.removeAll(videos);
                    if (row.videos.isEmpty()) {
                        mRows.remove(key);
                    }
                }
                break;
            case VideoGroup.ACTION_SYNC:
                // Nothing new to show: a card's watched progress changed
                break;
            case VideoGroup.ACTION_APPEND:
            default:
                row = row != null ? row : newRow(key, title, iconUrl, tag);
                for (Video video : videos) {
                    if (!row.videos.contains(video)) { // a page that repeats the last one's end
                        row.videos.add(video);
                    }
                }
                break;
        }
    }

    private Row newRow(Object key, String title, @Nullable String iconUrl, @Nullable Object tag) {
        Row row = new Row();
        row.title = title;
        row.iconUrl = iconUrl;
        row.tag = tag;
        mRows.put(key, row);
        return row;
    }

    /** Which videos are a channel's */
    public interface VideoMatcher {
        boolean matches(Video video);
    }

    /** The channel left Subscriptions (unsubscribed, blocked): its row goes, and its videos from the other rows */
    public void removeChannel(Object tag, @NonNull VideoMatcher channelVideos) {
        for (Iterator<Map.Entry<Object, Row>> it = mRows.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Object, Row> entry = it.next();

            if (entry.getValue().tag != null && entry.getValue().tag == tag) {
                if (entry.getKey().equals(mSelectedKey)) {
                    mSelectedKey = null;
                }
                it.remove();
            } else {
                for (Iterator<Video> videos = entry.getValue().videos.iterator(); videos.hasNext(); ) {
                    if (channelVideos.matches(videos.next())) {
                        videos.remove();
                    }
                }
            }
        }
    }

    // The strip

    /** The sub-pills: the rows that have a title and something to show */
    public List<Pill> pills() {
        List<Pill> pills = new ArrayList<>();

        for (Map.Entry<Object, Row> entry : mRows.entrySet()) {
            Row row = entry.getValue();

            if (!row.videos.isEmpty() && row.title != null && !row.title.isEmpty()) {
                pills.add(new Pill(entry.getKey(), row.title, row.iconUrl, row.tag));
            }
        }

        return pills;
    }

    /** Two or more titled rows: a strip of sub-pills, one row at a time */
    public boolean hasStrip() {
        return pills().size() >= 2;
    }

    /** The pill of the row on show (the first when none was picked, or the picked one is gone) */
    public int selectedIndex() {
        List<Pill> pills = pills();

        for (int i = 0; i < pills.size(); i++) {
            if (pills.get(i).key.equals(mSelectedKey)) {
                return i;
            }
        }

        return 0;
    }

    public void select(int index) {
        List<Pill> pills = pills();

        if (index >= 0 && index < pills.size()) {
            mSelectedKey = pills.get(index).key;
        }
    }

    /** What the list shows: the picked row's videos when there is a strip, else everything with headers */
    public List<Object> shown() {
        if (!hasStrip()) {
            return items();
        }

        Row row = mRows.get(pills().get(selectedIndex()).key);
        return new ArrayList<Object>(row.videos);
    }

    /** Headers (as Strings) and videos, in order */
    public List<Object> items() {
        List<Object> items = new ArrayList<>();

        for (Row row : mRows.values()) {
            if (row.videos.isEmpty()) {
                continue;
            }

            if (mRows.size() > 1 && row.title != null && !row.title.isEmpty()) {
                items.add(row.title);
            }

            items.addAll(row.videos);
        }

        return items;
    }
}
