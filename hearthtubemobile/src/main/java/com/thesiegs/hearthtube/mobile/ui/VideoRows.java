package com.thesiegs.hearthtube.mobile.ui;

import androidx.annotation.NonNull;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The groups a section arrives in (a grid is one; Home is a few rows, each with a title), kept in arrival order and
 * flattened into what the list shows: a header (the row's title, when it has one) and its videos. Plain Java, so the
 * way groups are applied is tested apart from Android.
 */
public class VideoRows {
    private final Map<Object, Row> mRows = new LinkedHashMap<>();

    private static final class Row {
        String title;
        final List<Video> videos = new ArrayList<>();
    }

    public void clear() {
        mRows.clear();
    }

    public void update(@NonNull VideoGroup group) {
        update(keyOf(group), group.getTitle(), group.getAction(), group.getVideos());
    }

    /** A group's identity: its position among the section's groups, else its title */
    private static Object keyOf(VideoGroup group) {
        return group.getPosition() + "/" + group.getId();
    }

    void update(Object key, String title, int action, List<Video> videos) {
        Row row = mRows.get(key);

        switch (action) {
            case VideoGroup.ACTION_REPLACE:
                row = new Row();
                row.title = title;
                row.videos.addAll(videos);
                mRows.put(key, row);
                break;
            case VideoGroup.ACTION_PREPEND:
                row = row != null ? row : newRow(key, title);
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
                row = row != null ? row : newRow(key, title);
                for (Video video : videos) {
                    if (!row.videos.contains(video)) { // a page that repeats the last one's end
                        row.videos.add(video);
                    }
                }
                break;
        }
    }

    private Row newRow(Object key, String title) {
        Row row = new Row();
        row.title = title;
        mRows.put(key, row);
        return row;
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
