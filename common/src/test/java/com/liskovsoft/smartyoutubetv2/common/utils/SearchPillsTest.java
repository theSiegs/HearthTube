package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SearchPillsTest {
    private static final KeywordMatcher NO_WORDS = KeywordMatcher.EMPTY;

    private static List<Tag> pills(String... texts) {
        List<Tag> result = new ArrayList<>();
        for (String text : texts) {
            result.add(new Tag(text));
        }
        return result;
    }

    private static List<String> texts(List<Tag> pills) {
        List<String> result = new ArrayList<>();
        if (pills != null) {
            for (Tag pill : pills) {
                result.add(pill.tag);
            }
        }
        return result;
    }

    private static List<String> shown(String query, boolean hidingShorts, KeywordMatcher words, String... pills) {
        return texts(SearchPills.filter(query, pills(pills), hidingShorts, words));
    }

    @Test
    public void clipPillsGoWhileShortsAreHidden() {
        assertEquals(Arrays.asList("minecraft", "mine song"),
                shown("min", true, NO_WORDS, "minecraft", "minecraft tiktok", "mine song", "minecraft shorts",
                        "minecraft #short", "mine instagram reels", "minecraft tik tok compilation", "mine #fyp"));
    }

    @Test
    public void clipPillsStayWhileShortsAreShown() {
        // A grown-up with Show Shorts on sees TikTok clips and Shorts in rows too
        assertEquals(Arrays.asList("minecraft", "minecraft tiktok", "minecraft shorts"),
                shown("min", false, NO_WORDS, "minecraft", "minecraft tiktok", "minecraft shorts"));
    }

    @Test
    public void searchForClipsHasNoPills() {
        // Even the ones that don't say so themselves
        assertNull(SearchPills.filter("tiktok", pills("tiktok dances", "charli dance"), true, NO_WORDS));
        assertNull(SearchPills.filter("TikTok", pills("charli dance"), true, NO_WORDS));
        assertNull(SearchPills.filter("tik tok", pills("charli dance"), true, NO_WORDS));
        assertNull(SearchPills.filter("insta reels", pills("funny"), true, NO_WORDS));
        assertNull(SearchPills.filter("youtube shorts", pills("funny"), true, NO_WORDS));

        assertTrue(SearchPills.isHidden("tiktok", true, NO_WORDS));
        assertFalse(SearchPills.isHidden("tiktok", false, NO_WORDS));
    }

    @Test
    public void shortsAsAWordOnly() {
        assertTrue(SearchPills.isHidden("#shorts", true, NO_WORDS));
        assertTrue(SearchPills.isHidden("funny shorts", true, NO_WORDS));
        assertFalse(SearchPills.isHidden("short film", true, NO_WORDS));
        assertFalse(SearchPills.isHidden("shortstop highlights", true, NO_WORDS));
        assertFalse(SearchPills.isHidden("reel fishing", true, NO_WORDS));
    }

    @Test
    public void blockedWordPillsGoInEveryProfile() {
        KeywordMatcher words = KeywordMatcher.compile(Arrays.asList("prank", "Pokémon"));

        for (boolean hidingShorts : new boolean[] {true, false}) {
            assertEquals(Arrays.asList("pokedex", "prankster"),
                    shown("p", hidingShorts, words, "pokedex", "pokemon cards", "funny prank", "PRANK!", "prankster"));
            assertNull(SearchPills.filter("prank", pills("funny"), hidingShorts, words));
            assertNull(SearchPills.filter("pokemon", pills("cards"), hidingShorts, words));
        }
    }

    @Test
    public void historyBeforeTypingIsFilteredToo() {
        assertEquals(Collections.singletonList("lego"),
                shown("", true, KeywordMatcher.compile(Collections.singletonList("prank")), "tiktok", "lego", "prank"));
    }

    @Test
    public void nothingLeftIsNoPills() {
        assertNull(SearchPills.filter("min", pills("minecraft tiktok"), true, NO_WORDS));
        assertNull(SearchPills.filter("min", null, true, NO_WORDS));
        assertNull(SearchPills.filter("min", Collections.emptyList(), true, NO_WORDS));
        assertNull(SearchPills.filter("min", pills((String) null), true, NO_WORDS));
    }

    @Test
    public void noQueryKeepsThePills() {
        assertEquals(Collections.singletonList("lego"), shown(null, true, NO_WORDS, "lego"));
    }
}
