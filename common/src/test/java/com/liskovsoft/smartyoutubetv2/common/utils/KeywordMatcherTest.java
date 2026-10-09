package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class KeywordMatcherTest {
    private static KeywordMatcher words(String... words) {
        return KeywordMatcher.compile(Arrays.asList(words));
    }

    @Test
    public void emptyListMatchesNothing() {
        KeywordMatcher none = KeywordMatcher.compile(Collections.emptyList());

        assertTrue(none.isEmpty());
        assertFalse(none.matches("Anything at all", "Any Channel"));
        assertFalse(KeywordMatcher.compile(null).matches("Anything"));
        assertFalse(words().matches("Anything"));
        // Nothing to go by in these
        assertTrue(words("", "   ", "!!!", "😂").isEmpty());
    }

    @Test
    public void nothingToCheck() {
        KeywordMatcher matcher = words("prank");

        assertFalse(matcher.matches());
        assertFalse(matcher.matches((String) null));
        assertFalse(matcher.matches(null, ""));
    }

    @Test
    public void ignoresCase() {
        KeywordMatcher matcher = words("Prank");

        assertTrue(matcher.matches("PRANK gone wrong"));
        assertTrue(matcher.matches("epic prank"));
        assertTrue(words("PRANK").matches("Prank Wars"));
    }

    @Test
    public void wholeWordsOnly() {
        KeywordMatcher matcher = words("live");

        assertFalse(matcher.matches("Delivery day"));
        assertFalse(matcher.matches("Lives of animals"));
        assertFalse(matcher.matches("Oliver"));
        assertTrue(matcher.matches("LIVE: launch day"));
        assertTrue(matcher.matches("We're live!"));
        assertTrue(matcher.matches("(live)"));
        assertTrue(matcher.matches("live"));
    }

    @Test
    public void punctuationSeparatesWords() {
        assertTrue(words("skibidi").matches("Skibidi's new episode"));
        assertTrue(words("toilet").matches("#skibidi_toilet"));
        assertTrue(words("shorts").matches("Fun clip #shorts"));
        assertTrue(words("2").matches("Part 2/3"));
        assertFalse(words("2").matches("Part 23"));
    }

    @Test
    public void phrases() {
        KeywordMatcher matcher = words("try not to laugh");

        assertTrue(matcher.matches("TRY NOT TO LAUGH Challenge #47"));
        assertTrue(matcher.matches("Try  not to laugh!"));
        assertTrue(matcher.matches("Try-not-to-laugh compilation"));
        assertFalse(matcher.matches("Try not to laughing"));
        assertFalse(matcher.matches("Try to laugh"));
        assertFalse(matcher.matches("not to laugh"));
        // Hyphens and spaces are the same
        assertTrue(words("spider man").matches("Spider-Man: Into the Spider-Verse"));
        assertTrue(words("Spider-Man").matches("spider man toys"));
        assertFalse(words("spiderman").matches("Spider-Man"));
    }

    @Test
    public void channelName() {
        KeywordMatcher matcher = words("Prank Channel");

        assertTrue(matcher.matches("Morning routine", "The Prank Channel"));
        assertFalse(matcher.matches("Morning routine", "Pranks Channel"));
        // A phrase can't run from the title into the channel name
        assertFalse(words("routine the").matches("Morning routine", "The Prank Channel"));
        assertTrue(words("routine").matches(null, "Routine Club"));
    }

    @Test
    public void anyWordOfTheList() {
        KeywordMatcher matcher = words("zombie", "scary", "try not to laugh");

        assertTrue(matcher.matches("Scary stories"));
        assertTrue(matcher.matches("A zombie walks in"));
        assertFalse(matcher.matches("Calm forest sounds", "Nature Channel"));
    }

    @Test
    public void accentsDontCount() {
        assertTrue(words("pokemon").matches("Pokémon Journeys"));
        assertTrue(words("Pokémon").matches("POKEMON cards"));
        assertTrue(words("creme brulee").matches("Crème Brûlée recipe"));
        // Decomposed é (e and a combining accent) is the same as é
        assertTrue(words("pokémon").matches("Pokémon"));
        assertTrue(words("viet nam").matches("Việt Nam trip"));
    }

    @Test
    public void unicodeCase() {
        assertTrue(words("ÉCOLE").matches("une école"));
        assertTrue(words("straße").matches("STRASSE"));
        assertTrue(words("strasse").matches("Straße"));
        assertTrue(words("kız").matches("KIZ KULESİ"));
        assertTrue(words("kulesi").matches("KIZ KULESİ"));
        assertTrue(words("οδος").matches("ΟΔΟΣ"));
        assertTrue(words("МУЛЬТИК").matches("новый мультик"));
        assertFalse(words("мульт").matches("новый мультик"));
        assertTrue(words("ёлка").matches("ЕЛКА"));
    }

    @Test
    public void compatibilityForms() {
        // Full-width letters, as some titles use for show
        assertTrue(words("tiktok").matches("ＴＩＫＴＯＫ compilation"));
        assertTrue(words("TikTok").matches("tiktok"));
    }

    @Test
    public void otherScriptsKeepWholeWords() {
        // Arabic, Hebrew, Hindi: spaced, so whole words; their marks are part of the word
        assertTrue(words("لعبة").matches("أفضل لعبة اليوم"));
        assertFalse(words("لعب").matches("أفضل لعبة اليوم"));
        assertTrue(words("משחק").matches("משחק חדש"));
        assertTrue(words("खेल").matches("नया खेल देखो"));
        assertFalse(words("खे").matches("नया खेल देखो"));
    }

    @Test
    public void languagesWithoutSpacesMatchInsideText() {
        assertTrue(words("鬼滅").matches("鬼滅の刃 第1話"));
        assertTrue(words("鬼滅の刃").matches("【鬼滅の刃】まとめ"));
        assertTrue(words("ゲーム").matches("ゲーム実況"));
        assertTrue(words("게임").matches("재미있는 게임을 해요"));
        assertFalse(words("가").matches("각"));
        assertTrue(words("เกม").matches("เล่นเกมสนุก"));
        // Latin next to Japanese is still a whole word
        assertTrue(words("minecraft").matches("マイクラMinecraft実況"));
        assertFalse(words("craft").matches("マイクラMinecraft実況"));
    }

    @Test
    public void sameWord() {
        assertTrue(KeywordMatcher.isSame("Pokémon", "POKEMON"));
        assertTrue(KeywordMatcher.isSame("try  not to laugh", "Try-not-to-laugh"));
        assertFalse(KeywordMatcher.isSame("live", "lives"));
        assertFalse(KeywordMatcher.isSame(null, "live"));
    }

    @Test
    public void usableWords() {
        assertTrue(KeywordMatcher.isUsable("a"));
        assertTrue(KeywordMatcher.isUsable(" #fyp "));
        assertFalse(KeywordMatcher.isUsable("!!!"));
        assertFalse(KeywordMatcher.isUsable("  "));
        assertFalse(KeywordMatcher.isUsable(null));
    }

    @Test
    public void splitsTitleIntoWords() {
        assertEquals(Arrays.asList("TRY", "NOT", "TO", "LAUGH", "Challenge", "47"),
                KeywordMatcher.splitWords("TRY NOT TO LAUGH Challenge #47 😂"));
        assertEquals(Arrays.asList("Spider-Man", "don't", "Official", "Trailer"),
                KeywordMatcher.splitWords("Spider-Man: don't (Official Trailer) - a"));
        // Repeats once, as first written
        assertEquals(Arrays.asList("Pokémon", "cards"), KeywordMatcher.splitWords("Pokémon cards POKEMON!"));
        assertEquals(Arrays.asList("鬼滅の刃", "第1話"), KeywordMatcher.splitWords("鬼滅の刃 第1話"));
        assertEquals(Collections.emptyList(), KeywordMatcher.splitWords(null));
        assertEquals(Collections.emptyList(), KeywordMatcher.splitWords("  !! "));
    }
}
