package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The channels are made up. */
public class HearthSectionsTest {
    private static final String CHANNEL = "UCaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OTHER = "UCbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    public void idsDecideWhenBothHaveOne() {
        assertTrue(HearthSections.isSameChannel(CHANNEL, "Cat Tales", CHANNEL, "Someone Else"));
        assertFalse(HearthSections.isSameChannel(CHANNEL, "Cat Tales", OTHER, "Cat Tales"));
    }

    @Test
    public void nameWhenAnIdIsMissing() {
        // The list of channels has no ids
        assertTrue(HearthSections.isSameChannel(null, "Cat Tales", OTHER, "Cat Tales"));
        assertTrue(HearthSections.isSameChannel(CHANNEL, "Cat Tales", null, "Cat Tales"));
        assertFalse(HearthSections.isSameChannel(null, "Cat Tales", null, "Dog Days"));
    }

    @Test
    public void feedAuthorCarriesTheHandle() {
        assertTrue(HearthSections.isSameChannel(null, "Cat Tales", null, "Cat Tales • @CatTales"));
        assertFalse(HearthSections.isSameChannel(null, "Cat Tales", null, "Dog Days • @CatTales"));
    }

    @Test
    public void nameIgnoresCaseAndSpaces() {
        assertTrue(HearthSections.isSameChannel(null, " Cat Tales ", null, "cat tales"));
    }

    @Test
    public void collaborationIsNotTheChannels() {
        // Another channel's video with ours in the title of the collaboration
        assertFalse(HearthSections.isSameChannel(null, "Cat Tales", null, "Dog Days and Cat Tales • @dogdays"));
    }

    @Test
    public void nothingToGoBy() {
        assertFalse(HearthSections.isSameChannel(null, null, null, "Cat Tales"));
        assertFalse(HearthSections.isSameChannel(null, "Cat Tales", null, null));
        assertFalse(HearthSections.isSameChannel(null, null, null, null));
    }

    // Unsubscribed from a video card's menu: which circle is the video's channel's (All and topics have none)

    @Test
    public void findsTheVideosCircle() {
        java.util.List<String> ids = java.util.Arrays.asList(null, null, CHANNEL, null);
        java.util.List<String> titles = java.util.Arrays.asList(null, "Dog Days", "Cat Tales", "Fox Facts");

        assertEquals(2, HearthSections.indexOfChannel(ids, titles, CHANNEL, "Someone Else"));
        assertEquals(2, HearthSections.indexOfChannel(ids, titles, null, "Cat Tales • @CatTales"));
        assertEquals(3, HearthSections.indexOfChannel(ids, titles, OTHER, "Fox Facts • @foxfacts"));
        assertEquals(1, HearthSections.indexOfChannel(ids, titles, null, "dog days"));
    }

    @Test
    public void noCircleNoIndex() {
        java.util.List<String> ids = java.util.Arrays.asList(null, CHANNEL);
        java.util.List<String> titles = java.util.Arrays.asList(null, "Cat Tales");

        // Another channel, a collaboration another channel posted, a circle's id that isn't the video's
        assertEquals(-1, HearthSections.indexOfChannel(ids, titles, null, "Owl Hour • @owlhour"));
        assertEquals(-1, HearthSections.indexOfChannel(ids, titles, null, "Dog Days and Cat Tales • @dogdays"));
        assertEquals(-1, HearthSections.indexOfChannel(ids, titles, OTHER, "Cat Tales"));
        assertEquals(-1, HearthSections.indexOfChannel(ids, titles, null, null));
        assertEquals(-1, HearthSections.indexOfChannel(java.util.Collections.<String>emptyList(),
                java.util.Collections.<String>emptyList(), CHANNEL, "Cat Tales"));
    }
}
