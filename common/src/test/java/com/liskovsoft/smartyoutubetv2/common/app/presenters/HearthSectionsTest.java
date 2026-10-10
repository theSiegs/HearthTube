package com.liskovsoft.smartyoutubetv2.common.app.presenters;

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
}
