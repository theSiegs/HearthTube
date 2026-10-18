package com.thesiegs.hearthtube.mobile.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class VideoRowsTest {
    private final VideoRows mRows = new VideoRows();

    private static Video video(String id) {
        Video video = new Video();
        video.videoId = id;
        video.title = id;
        return video;
    }

    private static List<Video> videos(String... ids) {
        List<Video> videos = new ArrayList<>();
        for (String id : ids) {
            videos.add(video(id));
        }
        return videos;
    }

    private static List<String> ids(List<Object> items) {
        List<String> ids = new ArrayList<>();
        for (Object item : items) {
            ids.add(item instanceof Video ? ((Video) item).videoId : "#" + item);
        }
        return ids;
    }

    @Test
    public void aSingleGroupHasNoHeader() {
        mRows.update("g", "Home", VideoGroup.ACTION_APPEND, videos("a", "b"));
        assertEquals(Arrays.asList("a", "b"), ids(mRows.items()));
    }

    @Test
    public void severalGroupsEachGetTheirTitleAsAHeader() {
        mRows.update("g1", "Trending", VideoGroup.ACTION_APPEND, videos("a"));
        mRows.update("g2", "Music", VideoGroup.ACTION_APPEND, videos("b", "c"));
        assertEquals(Arrays.asList("#Trending", "a", "#Music", "b", "c"), ids(mRows.items()));
    }

    @Test
    public void nextPagesAppendToTheirGroupInOrder() {
        mRows.update("g", "Home", VideoGroup.ACTION_APPEND, videos("a", "b"));
        mRows.update("g", "Home", VideoGroup.ACTION_APPEND, videos("c"));
        assertEquals(Arrays.asList("a", "b", "c"), ids(mRows.items()));
    }

    @Test
    public void aPageThatRepeatsTheLastOnesEndAddsNothingTwice() {
        mRows.update("g", "Home", VideoGroup.ACTION_APPEND, videos("a", "b"));
        mRows.update("g", "Home", VideoGroup.ACTION_APPEND, videos("b", "c"));
        assertEquals(Arrays.asList("a", "b", "c"), ids(mRows.items()));
    }

    @Test
    public void replaceStartsTheGroupOver() {
        mRows.update("g", "Home", VideoGroup.ACTION_APPEND, videos("a", "b"));
        mRows.update("g", "Home", VideoGroup.ACTION_REPLACE, videos("x"));
        assertEquals(Arrays.asList("x"), ids(mRows.items()));
    }

    @Test
    public void prependGoesFirst() {
        mRows.update("g", "Home", VideoGroup.ACTION_APPEND, videos("a"));
        mRows.update("g", "Home", VideoGroup.ACTION_PREPEND, videos("z"));
        assertEquals(Arrays.asList("z", "a"), ids(mRows.items()));
    }

    @Test
    public void removeTakesVideosOutAndAnEmptyGroupGoes() {
        mRows.update("g", "Home", VideoGroup.ACTION_APPEND, videos("a", "b"));
        mRows.update("g", "Home", VideoGroup.ACTION_REMOVE, videos("a"));
        assertEquals(Arrays.asList("b"), ids(mRows.items()));
        mRows.update("g", "Home", VideoGroup.ACTION_REMOVE, videos("b"));
        assertTrue(mRows.items().isEmpty());
    }

    @Test
    public void syncChangesNothingInTheList() {
        mRows.update("g", "Home", VideoGroup.ACTION_APPEND, videos("a"));
        mRows.update("g", "Home", VideoGroup.ACTION_SYNC, videos("a"));
        assertEquals(Arrays.asList("a"), ids(mRows.items()));
    }

    @Test
    public void clearEmptiesEverything() {
        mRows.update("g", "Home", VideoGroup.ACTION_APPEND, videos("a"));
        mRows.clear();
        assertTrue(mRows.items().isEmpty());
    }
}
