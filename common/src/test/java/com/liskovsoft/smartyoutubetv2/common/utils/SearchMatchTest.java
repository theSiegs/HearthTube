package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchMatchTest {
    @Test
    public void mostWordsHaveToBeFound() {
        assertTrue(SearchMatch.matches("sailing boat tour", "Boat tour of a sailing catamaran", "Sam"));
        assertTrue(SearchMatch.matches("sailing wynns", "Our sailing life", "Gone with the Wynns"));
        assertFalse(SearchMatch.matches("sailing boat tour", "Cooking pasta at home", "Sam"));
        assertFalse(SearchMatch.matches("sailing", "Cooking pasta at home", "Sam"));
    }

    @Test
    public void oneWordHeardWrongDoesNotHideTheVideo() {
        // voice search heard "wind" for "wynns"
        assertTrue(SearchMatch.matches("gone with the wind", "Fix One Thing, Break Another", "Gone with the Wynns"));
        assertFalse(SearchMatch.matches("gone with the wind", "Fix One Thing, Break Another", "Sam"));
    }

    @Test
    public void wordsSpelledAlikeAreTheSameWord() {
        assertTrue(SearchMatch.matches("catamarans", "Sailing a catamaran home", null));
        assertTrue(SearchMatch.matches("wynn", "Gone with the Wynns", null));
        assertTrue(SearchMatch.matches("boat tours", "Boat tour in Maine", null));
        assertFalse(SearchMatch.same("pasta", "boat"));
    }

    @Test
    public void caseAccentsAndPunctuationDoNotMatter() {
        assertTrue(SearchMatch.matches("cafe  RENE!", "Le Café de René", null));
    }

    @Test
    public void anEmptySearchMatchesEverything() {
        assertTrue(SearchMatch.matches("", "Anything", "Anyone"));
        assertTrue(SearchMatch.matches(null, null, null));
    }

    @Test
    public void thePreviouslyWatchedShelfIsRecognisedByItsTitle() {
        assertTrue(SearchMatch.isPreviouslyWatchedShelf("Previously watched"));
        assertTrue(SearchMatch.isPreviouslyWatchedShelf("previously watched"));
        assertFalse(SearchMatch.isPreviouslyWatchedShelf("Videos for you"));
        assertFalse(SearchMatch.isPreviouslyWatchedShelf(null));
    }
}
