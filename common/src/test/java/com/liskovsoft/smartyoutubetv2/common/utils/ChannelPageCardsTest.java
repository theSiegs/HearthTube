package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import org.junit.Test;

/** The lines are shaped like those of YouTube's TV channel pages; the channels are made up. */
public class ChannelPageCardsTest {
    private static final String PAGE = "UCaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OTHER = "UCbbbbbbbbbbbbbbbbbbbbbb";
    private static final String PLAYLIST = "VLPLcccccccccccccccccccc";

    // The page channel's own videos: menu line "Name • @handle", the line under the title "Name • views • age"
    private static Video own(String videoId) {
        return card(videoId, "Cat Tales • @CatTales", "Cat Tales • 1.2M views • 3 days ago");
    }

    // A collaboration the page channel owns, and one another channel owns
    private static Video ownCollab() {
        return card("ownCollab", "Cat Tales and Dog Days • @CatTales", "Cat Tales and Dog Days • 40K views • 1 week ago");
    }

    private static Video othersCollab() {
        return card("othersCollab", "Dog Days and Cat Tales • @dogdays", "Dog Days and Cat Tales • 2M views • 1 week ago");
    }

    // Another creator's video on a shelf of the page
    private static Video others(String videoId) {
        return card(videoId, "Dog Days • @dogdays", "Dog Days • 900K views • 1 year ago");
    }

    private static Video card(String videoId, String author, String secondTitle) {
        Video video = new Video();
        video.videoId = videoId;
        video.author = author;
        video.secondTitle = secondTitle;
        return video;
    }

    private static void learn(ChannelPageCards page, Video... cards) {
        for (Video card : cards) {
            page.learn(card.channelId, card.author, card.secondTitle);
        }
    }

    @Test
    public void readsTheHandleAndTheName() {
        assertEquals("@cattales", ChannelPageCards.handleOf("Cat Tales • @cattales"));
        assertEquals("@cattales", ChannelPageCards.handleOf("@cattales • 30.5M subscribers"));
        assertNull(ChannelPageCards.handleOf("Cat Tales • 1.2M views • 3 days ago"));
        assertNull(ChannelPageCards.handleOf("@ • 3 days ago"));
        assertNull(ChannelPageCards.handleOf(null));

        assertEquals("Cat Tales", ChannelPageCards.nameOf("Cat Tales • @cattales"));
        assertEquals("Cat Tales and Dog Days", ChannelPageCards.nameOf("Cat Tales and Dog Days • 40K views"));
        assertNull(ChannelPageCards.nameOf("@cattales • 30.5M subscribers"));
        assertNull(ChannelPageCards.nameOf(""));
        assertNull(ChannelPageCards.nameOf(null));
    }

    @Test
    public void onlyChannelIds() {
        assertNull(ChannelPageCards.of(null, null));
        assertNull(ChannelPageCards.of("@cattales", null)); // a link by handle: no ID to give
        assertNull(ChannelPageCards.of(PLAYLIST, null));
        assertEquals(PAGE, ChannelPageCards.of(PAGE, null).getChannelId());
    }

    @Test
    public void openedByLinkTheCardsTellTheChannel() {
        ChannelPageCards page = ChannelPageCards.of(PAGE, null);
        Video[] cards = {own("a"), own("b"), own("c"), own("d"), own("e"), ownCollab(), othersCollab(), others("x")};
        learn(page, cards); // 6 of 8 cards (3 in 4) are its own

        assertEquals("@CatTales", page.getHandle());
        assertEquals("Cat Tales", page.getName());

        for (int i = 0; i < 5; i++) {
            assertTrue(page.stamp(cards[i]));
            assertEquals(PAGE, cards[i].channelId);
        }
        assertTrue(page.stamp(cards[5])); // a collaboration it owns: its channel ID is the page's
        assertFalse(page.stamp(cards[6])); // one another channel owns
        assertFalse(page.stamp(cards[7])); // another creator's video

        assertEquals(PAGE, cards[5].channelId);
        assertNull(cards[6].channelId);
        assertNull(cards[7].channelId);
        assertEquals(6, page.getStamped());
    }

    @Test
    public void notQuiteThreeInFour() {
        ChannelPageCards page = ChannelPageCards.of(PAGE, null);
        learn(page, own("a"), own("b"), own("c"), own("d"), othersCollab(), others("x"));

        assertNull(page.getHandle()); // 4 of 6
        assertFalse(page.stamp(own("e")));
    }

    @Test
    public void noClearChannelNoStamps() {
        // A page of many creators' videos (a hub, a curator), opened by link: no handle stands out
        ChannelPageCards page = ChannelPageCards.of(PAGE, null);
        Video[] cards = {own("a"), own("b"), own("c"), others("x"), others("y"), card("z", "Fox Facts • @foxfacts", "Fox Facts")};
        learn(page, cards);

        assertNull(page.getHandle());
        for (Video card : cards) {
            assertFalse(page.stamp(card));
            assertNull(card.channelId);
        }
    }

    @Test
    public void tooFewCardsToTell() {
        ChannelPageCards page = ChannelPageCards.of(PAGE, null);
        Video[] cards = {own("a"), own("b")};
        learn(page, cards);

        assertNull(page.getHandle());
        assertFalse(page.stamp(cards[0]));
    }

    @Test
    public void keepsIdsCardsHave() {
        ChannelPageCards page = ChannelPageCards.of(PAGE, null);

        Video featured = card(null, null, "@dogdays • 30.5M subscribers");
        featured.channelId = OTHER;
        featured.title = "Dog Days";
        Video playlist = card(null, "Cat Tales", "Cat Tales • 75K views");
        playlist.playlistId = "PLcccccccccccccccccccc";
        playlist.channelId = PLAYLIST;
        Video ownWithId = own("d");
        ownWithId.channelId = OTHER; // whatever a card says stays

        learn(page, own("a"), own("b"), own("c"), featured, playlist, ownWithId);

        assertFalse(page.stamp(featured));
        assertFalse(page.stamp(playlist));
        assertFalse(page.stamp(ownWithId));
        assertEquals(OTHER, featured.channelId);
        assertEquals(PLAYLIST, playlist.channelId);
        assertEquals(OTHER, ownWithId.channelId);
    }

    @Test
    public void openedFromTheChannelsCard() {
        Video opener = card(null, null, "@cattales • 1.2M subscribers");
        opener.channelId = PAGE;
        opener.title = "Cat Tales";
        ChannelPageCards page = ChannelPageCards.of(PAGE, opener);

        // Known before any card: even a page of mostly other creators' videos is told apart
        Video mine = own("a");
        Video[] theirs = {others("x"), others("y"), others("z"), othersCollab()};
        learn(page, mine);
        learn(page, theirs);

        assertEquals("@cattales", page.getHandle());
        assertTrue(page.stamp(mine));
        for (Video card : theirs) {
            assertFalse(page.stamp(card));
        }
    }

    @Test
    public void openedFromTheChannelsVideoInThePlayer() {
        // The player's video has the channel's name from its details, no handle
        Video opener = card("v", "Cat Tales", "Cat Tales • 1.2M views");
        opener.channelId = PAGE;
        ChannelPageCards page = ChannelPageCards.of(PAGE, opener);

        Video collab = ownCollab();
        learn(page, collab, own("a"), others("x"));

        // A card with its name gives the handle: collaborations it owns count as its own too
        assertEquals("@CatTales", page.getHandle());
        assertTrue(page.stamp(collab));
        assertFalse(page.stamp(others("y")));
    }

    @Test
    public void anOpenerFromAnotherChannelDoesntCount() {
        Video opener = card("v", "Dog Days • @dogdays", "Dog Days • 1M views");
        opener.channelId = OTHER; // the last page's channel, still remembered
        ChannelPageCards page = ChannelPageCards.of(PAGE, opener);

        Video[] cards = {own("a"), own("b"), own("c"), others("x")};
        learn(page, cards);

        assertEquals("@CatTales", page.getHandle());
        assertTrue(page.stamp(cards[0]));
        assertFalse(page.stamp(cards[3]));
    }

    @Test
    public void cardsWithoutAHandleGoByTheExactName() {
        Video opener = card(null, null, "@cattales • 1.2M subscribers");
        opener.channelId = PAGE;
        opener.title = "Cat Tales";
        ChannelPageCards page = ChannelPageCards.of(PAGE, opener);

        Video mine = card("a", null, "CAT  TALES • 3 days ago");
        Video similar = card("b", null, "Cat Tales TV • 3 days ago");
        assertTrue(page.stamp(mine));
        assertFalse(page.stamp(similar));

        // Opened by link, nobody's handle known: a card without one gets nothing
        ChannelPageCards byLink = ChannelPageCards.of(PAGE, null);
        assertFalse(byLink.stamp(card("c", null, "Cat Tales • 3 days ago")));
    }

    @Test
    public void fillsAMissingName() {
        Video opener = card(null, null, "@cattales • 1.2M subscribers");
        opener.channelId = PAGE;
        opener.title = "Cat Tales";
        ChannelPageCards page = ChannelPageCards.of(PAGE, opener);

        Video nameless = card("a", null, "@cattales • 3 days ago");
        assertTrue(page.stamp(nameless));
        assertEquals("Cat Tales", nameless.author);

        Video named = own("b");
        assertTrue(page.stamp(named));
        assertEquals("Cat Tales • @CatTales", named.author); // its own name stays
    }

    @Test
    public void onlyVideos() {
        Video opener = card(null, null, "@cattales • 1.2M subscribers");
        opener.channelId = PAGE;
        opener.title = "Cat Tales";
        ChannelPageCards page = ChannelPageCards.of(PAGE, opener);

        Video chapter = own("a");
        chapter.isChapter = true;
        Video noVideo = own(null);

        assertFalse(page.stamp(chapter));
        assertFalse(page.stamp(noVideo));
        assertFalse(page.stamp(null));
    }

    // The channel's full uploads grid (ChannelUploadsPresenter), which opens playlists too

    private static Video channelCard() {
        Video channel = card(null, null, "@cattales • 1.2M subscribers");
        channel.channelId = PAGE;
        channel.title = "Cat Tales";
        return channel;
    }

    @Test
    public void uploadsOfAChannelGetItsId() {
        ChannelPageCards page = ChannelPageCards.ofUploads(channelCard());
        Video[] cards = {own("a"), own("b"), ownCollab(), othersCollab(), others("x")};
        learn(page, cards);

        assertEquals(PAGE, page.getChannelId());
        assertTrue(page.stamp(cards[0]));
        assertTrue(page.stamp(cards[1]));
        assertTrue(page.stamp(cards[2]));
        assertFalse(page.stamp(cards[3])); // a collaboration another channel owns
        assertFalse(page.stamp(cards[4]));
        assertEquals(PAGE, cards[0].channelId);
        assertNull(cards[3].channelId);
        assertNull(cards[4].channelId);
    }

    @Test
    public void uploadsOpenedFromTheChannelsCircleWithoutAHandle() {
        // A Subscriptions circle: the channel's name and ID only; the grid's cards tell its handle
        Video circle = card(null, null, null);
        circle.channelId = PAGE;
        circle.title = "Cat Tales";
        ChannelPageCards page = ChannelPageCards.ofUploads(circle);

        Video[] cards = {own("a"), others("x")};
        learn(page, cards);

        assertEquals("@CatTales", page.getHandle());
        assertTrue(page.stamp(cards[0]));
        assertFalse(page.stamp(cards[1]));
    }

    @Test
    public void uploadsKeepIdsCardsHave() {
        ChannelPageCards page = ChannelPageCards.ofUploads(channelCard());
        Video withId = own("a");
        withId.channelId = OTHER;
        learn(page, withId, own("b"));

        assertFalse(page.stamp(withId));
        assertEquals(OTHER, withId.channelId);
    }

    @Test
    public void playlistsAreNoChannelsUploads() {
        // A playlist card, and a playlist on the channel's page: both have the channel's ID
        Video playlist = card(null, "Cat Tales", "Cat Tales • 12 videos");
        playlist.playlistId = "PLcccccccccccccccccccc";
        playlist.channelId = PAGE;
        assertNull(ChannelPageCards.ofUploads(playlist));

        Video videoInAPlaylist = own("a");
        videoInAPlaylist.playlistId = "PLcccccccccccccccccccc";
        videoInAPlaylist.channelId = PAGE;
        assertNull(ChannelPageCards.ofUploads(videoInAPlaylist));

        // A channel that is a playlist (a mix, a topic): anybody's videos
        Video playlistChannel = card(null, null, null);
        playlistChannel.channelId = PAGE;
        playlistChannel.itemType = com.liskovsoft.mediaserviceinterfaces.data.MediaItem.TYPE_PLAYLIST;
        assertNull(ChannelPageCards.ofUploads(playlistChannel));

        Video byPlaylistId = card(null, null, null);
        byPlaylistId.channelId = PLAYLIST;
        assertNull(ChannelPageCards.ofUploads(byPlaylistId));
    }

    @Test
    public void uploadsWithoutAChannelId() {
        assertNull(ChannelPageCards.ofUploads(null));
        assertNull(ChannelPageCards.ofUploads(card("a", "Cat Tales • @CatTales", "Cat Tales • 3 days ago")));
    }

    @Test
    public void uploadsOfAnotherNameStayUnstamped() {
        // Opened by the channel's card, so who it is is known: a grid of someone else's videos gets nothing
        ChannelPageCards page = ChannelPageCards.ofUploads(channelCard());
        Video[] cards = {others("x"), others("y"), others("z"), card("w", null, "Cat Tales TV • 3 days ago")};
        learn(page, cards);

        for (Video card : cards) {
            assertFalse(page.stamp(card));
            assertNull(card.channelId);
        }
    }
}
