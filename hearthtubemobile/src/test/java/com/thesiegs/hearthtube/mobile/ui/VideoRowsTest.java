package com.thesiegs.hearthtube.mobile.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class VideoRowsTest {
    /** Video.getAuthor needs Android's TextUtils: the tests read the author field */
    private final VideoRows mRows = new VideoRows(video -> video.author);

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

    // The strip of sub-pills

    private static List<String> titles(List<VideoRows.Pill> pills) {
        List<String> titles = new ArrayList<>();
        for (VideoRows.Pill pill : pills) {
            titles.add(pill.title);
        }
        return titles;
    }

    @Test
    public void oneRowHasNoStrip() {
        mRows.update("g", "Fireplace", VideoGroup.ACTION_APPEND, videos("a", "b"));
        assertFalse(mRows.hasStrip());
        assertEquals(Arrays.asList("a", "b"), ids(mRows.shown()));
    }

    @Test
    public void twoTitledRowsMakeAStripOfTheirTitles() {
        mRows.update("g1", "Fireplace", VideoGroup.ACTION_APPEND, videos("a"));
        mRows.update("g2", "Rain", VideoGroup.ACTION_APPEND, videos("b"));
        assertTrue(mRows.hasStrip());
        assertEquals(Arrays.asList("Fireplace", "Rain"), titles(mRows.pills()));
    }

    @Test
    public void theStripShowsOnlyTheFirstRowUntilAnotherIsPicked() {
        mRows.update("g1", "Fireplace", VideoGroup.ACTION_APPEND, videos("a", "b"));
        mRows.update("g2", "Rain", VideoGroup.ACTION_APPEND, videos("c"));
        assertEquals(0, mRows.selectedIndex());
        assertEquals(Arrays.asList("a", "b"), ids(mRows.shown()));

        mRows.select(1);
        assertEquals(1, mRows.selectedIndex());
        assertEquals(Arrays.asList("c"), ids(mRows.shown()));
    }

    @Test
    public void aPickedRowStaysPickedWhenItsNextPageArrives() {
        mRows.update("g1", "Fireplace", VideoGroup.ACTION_APPEND, videos("a"));
        mRows.update("g2", "Rain", VideoGroup.ACTION_APPEND, videos("c"));
        mRows.select(1);
        mRows.update("g2", "Rain", VideoGroup.ACTION_APPEND, videos("d"));
        assertEquals(Arrays.asList("c", "d"), ids(mRows.shown()));
    }

    @Test
    public void rowsStillLoadingAreNotPillsYet() {
        mRows.update("g1", "Fireplace", VideoGroup.ACTION_APPEND, videos("a"));
        mRows.update("g2", "Rain", VideoGroup.ACTION_APPEND, videos());
        assertFalse(mRows.hasStrip());
        assertEquals(Arrays.asList("Fireplace"), titles(mRows.pills()));
    }

    @Test
    public void untitledRowsAreNotPills() {
        mRows.update("g1", "Fireplace", VideoGroup.ACTION_APPEND, videos("a"));
        mRows.update("g2", "", VideoGroup.ACTION_APPEND, videos("b"));
        assertFalse(mRows.hasStrip());
    }

    @Test
    public void clearingASectionForgetsThePickedRow() {
        mRows.update("g1", "Fireplace", VideoGroup.ACTION_APPEND, videos("a"));
        mRows.update("g2", "Rain", VideoGroup.ACTION_APPEND, videos("c"));
        mRows.select(1);
        mRows.clear();
        mRows.update("g1", "Fireplace", VideoGroup.ACTION_APPEND, videos("a"));
        mRows.update("g2", "Rain", VideoGroup.ACTION_APPEND, videos("c"));
        assertEquals(0, mRows.selectedIndex());
    }

    @Test
    public void aChannelThatLeavesTakesItsPillAndItsVideosFromTheOthers() {
        Object channel = new Object();
        List<Video> uploads = videos("u1");
        mRows.update("all", "All", null, null, VideoGroup.ACTION_APPEND, videos("x"));
        mRows.update("all", "All", null, null, VideoGroup.ACTION_APPEND, uploads);
        mRows.update("ch", "Some Channel", "https://example.invalid/icon.png", channel, VideoGroup.ACTION_APPEND, uploads);
        mRows.update("other", "Other", null, null, VideoGroup.ACTION_APPEND, videos("o"));
        mRows.select(1);

        mRows.removeChannel(channel, uploads::contains);

        assertEquals(Arrays.asList("All", "Other"), titles(mRows.pills()));
        assertEquals(0, mRows.selectedIndex());
        assertEquals(Arrays.asList("x"), ids(mRows.shown()));
    }

    @Test
    public void aScreenWithoutAStripShowsEveryRowWithItsHeader() {
        // A channel page: Videos, Playlists, Live. With no pills to pick from, one row alone would hide the others
        mRows.update("g1", "Videos", VideoGroup.ACTION_APPEND, videos("a", "b"));
        mRows.update("g2", "Playlists", VideoGroup.ACTION_APPEND, videos("c"));
        mRows.update("g3", "Live", VideoGroup.ACTION_APPEND, videos("d"));

        assertEquals(Arrays.asList("a", "b"), ids(mRows.shown(true)));
        assertEquals(Arrays.asList("#Videos", "a", "b", "#Playlists", "c", "#Live", "d"), ids(mRows.shown(false)));
    }

    @Test
    public void aScreenWithoutAStripShowsOneRowPlainly() {
        mRows.update("g1", "Videos", VideoGroup.ACTION_APPEND, videos("a"));
        assertEquals(Arrays.asList("a"), ids(mRows.shown(false)));
    }

    // A channel hidden or blocked from a card's menu

    private static Video by(String id, String channelId, String author) {
        Video video = video(id);
        video.channelId = channelId;
        video.author = author;
        return video;
    }

    @Test
    public void removeAuthorTakesTheChannelsVideosOutOfEveryRow() {
        mRows.update("g1", "Home", VideoGroup.ACTION_APPEND, Arrays.asList(by("a", "UC1", "One"), by("b", "UC2", "Two")));
        mRows.update("g2", "Music", VideoGroup.ACTION_APPEND, Arrays.asList(by("c", "UC1", "One"), by("d", "UC3", "Three")));

        mRows.update("g1", "Home", VideoGroup.ACTION_REMOVE_AUTHOR, Arrays.asList(by("a", "UC1", "One")));

        assertEquals(Arrays.asList("#Home", "b", "#Music", "d"), ids(mRows.items()));
    }

    @Test
    public void removeAuthorMatchesByNameWhenACardHasNoChannelId() {
        mRows.update("g", "Home", VideoGroup.ACTION_APPEND, Arrays.asList(by("a", null, "One"), by("b", "UC2", "Two"), by("c", "UC1", "One")));

        mRows.update("g", "Home", VideoGroup.ACTION_REMOVE_AUTHOR, Arrays.asList(by("x", null, "One")));

        assertEquals(Arrays.asList("b"), ids(mRows.items()));
    }

    @Test
    public void removeAuthorDoesNotTouchAnotherChannelWithTheSameNameButAnotherId() {
        mRows.update("g", "Home", VideoGroup.ACTION_APPEND, Arrays.asList(by("a", "UC1", "One"), by("b", "UC9", "One")));

        mRows.update("g", "Home", VideoGroup.ACTION_REMOVE_AUTHOR, Arrays.asList(by("a", "UC1", "One")));

        assertEquals(Arrays.asList("b"), ids(mRows.items()));
    }

    @Test
    public void removeAuthorDropsARowLeftEmptyAndItsPill() {
        mRows.update("g1", "Fireplace", VideoGroup.ACTION_APPEND, Arrays.asList(by("a", "UC1", "One")));
        mRows.update("g2", "Rain", VideoGroup.ACTION_APPEND, Arrays.asList(by("b", "UC2", "Two")));
        mRows.update("g3", "Snow", VideoGroup.ACTION_APPEND, Arrays.asList(by("c", "UC3", "Three")));
        mRows.select(0);

        mRows.update("g1", "Fireplace", VideoGroup.ACTION_REMOVE_AUTHOR, Arrays.asList(by("a", "UC1", "One")));

        assertEquals(Arrays.asList("Rain", "Snow"), titles(mRows.pills()));
        assertEquals(Arrays.asList("b"), ids(mRows.shown()));
    }

    @Test
    public void removeAuthorWithNoVideoChangesNothing() {
        mRows.update("g", "Home", VideoGroup.ACTION_APPEND, videos("a"));
        mRows.update("g", "Home", VideoGroup.ACTION_REMOVE_AUTHOR, videos());
        assertEquals(Arrays.asList("a"), ids(mRows.items()));
    }

    @Test
    public void aChannelsPillCarriesItsPicture() {
        mRows.update("all", "All", VideoGroup.ACTION_APPEND, videos("x"));
        mRows.update("ch", "Some Channel", "https://example.invalid/icon.png", new Object(), VideoGroup.ACTION_APPEND, videos("y"));
        assertNull(mRows.pills().get(0).iconUrl);
        assertEquals("https://example.invalid/icon.png", mRows.pills().get(1).iconUrl);
    }
}
