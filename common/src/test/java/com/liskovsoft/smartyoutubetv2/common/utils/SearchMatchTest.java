package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchMatchTest {
    @Test
    public void everyWordHasToBeInTheTitleOrTheChannel() {
        assertTrue(SearchMatch.matches("sailing boat tour", "Boat tour of a sailing catamaran", "Sam"));
        assertTrue(SearchMatch.matches("sailing wynns", "Our sailing life", "Gone with the Wynns"));
        assertFalse(SearchMatch.matches("sailing boat tour", "Boat tour of a houseboat", "Sam"));
    }

    @Test
    public void caseAccentsAndPunctuationDoNotMatter() {
        assertTrue(SearchMatch.matches("cafe  RENE!", "Le Café de René", null));
    }

    @Test
    public void aMisspellingDoesNotMatch() {
        assertFalse(SearchMatch.matches("gone with the winds", "Fix One Thing, Break Another", "Gone with the Wynns"));
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
