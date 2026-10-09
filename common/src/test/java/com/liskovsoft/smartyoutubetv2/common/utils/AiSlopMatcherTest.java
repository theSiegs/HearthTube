package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AiSlopMatcherTest {
    // Made up, but shaped like channel IDs: "UC" and 22 more
    private static final String BLOCKED = "UCaaaaaaaaaaaaaaaaaaaaaa";
    private static final String BLOCKED_2 = "UC-b_bbbbbbbbbbbbbbbbbbb";
    private static final String WARNED = "UCcccccccccccccccccccccc";
    private static final String OTHER = "UCdddddddddddddddddddddd";

    private static final String HEADER = "! AiSList blocklist by YouTube channel ID\n"
            + "! License: CC BY-NC 4.0 (https://creativecommons.org/licenses/by-nc/4.0/), by the AiSList contributors;\n"
            + "! Channels: 2 of 3 entries\n";

    private static Set<String> parse(String text) throws IOException {
        return AiSlopMatcher.parse(new StringReader(text));
    }

    private static Set<String> set(String... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    private static AiSlopMatcher lists() {
        return AiSlopMatcher.of(set(BLOCKED, BLOCKED_2), set(WARNED));
    }

    @Test
    public void readsIdsAndSkipsComments() throws IOException {
        Set<String> ids = parse(HEADER + BLOCKED + "\n" + BLOCKED_2 + "\n");

        assertEquals(set(BLOCKED, BLOCKED_2), ids);
    }

    @Test
    public void skipsBlankAndBadLines() throws IOException {
        Set<String> ids = parse(HEADER
                + "\n"
                + "   \n"
                + BLOCKED + "\n"
                + "@SomeHandle\n" // a handle, not an ID
                + "UCtooshort\n"
                + BLOCKED + "x\n" // one too many
                + "UXaaaaaaaaaaaaaaaaaaaaaa\n" // not UC
                + "UCaaaaaaaaaaaaaaaaaaaa a\n" // a space inside
                + "UCaaaaaaaaaaaaaaaaaaaaa!\n"
                + "! UCeeeeeeeeeeeeeeeeeeeeee\n" // commented out
                + "  " + BLOCKED_2 + "  \n"); // spaces around are fine

        assertEquals(set(BLOCKED, BLOCKED_2), ids);
    }

    @Test
    public void duplicatesCountOnce() throws IOException {
        Set<String> ids = parse(HEADER + BLOCKED + "\n" + BLOCKED + "\n" + BLOCKED + "\n");

        assertEquals(1, ids.size());
        assertTrue(ids.contains(BLOCKED));
    }

    @Test
    public void windowsLineEndsAndByteOrderMark() throws IOException {
        Set<String> ids = parse("﻿" + HEADER.replace("\n", "\r\n") + BLOCKED + "\r\n" + BLOCKED_2 + "\r\n");

        assertEquals(set(BLOCKED, BLOCKED_2), ids);
    }

    @Test
    public void lastLineWithoutLineEnd() throws IOException {
        assertEquals(set(BLOCKED), parse(HEADER + BLOCKED));
    }

    @Test
    public void listWithNoIdsYet() throws IOException {
        // The warnlist, while the IDs are still being looked up
        Set<String> ids = parse("! AiSList warnlist by YouTube channel ID\n! Channels: 0 of 1003 entries\n");

        assertTrue(ids != null && ids.isEmpty());
    }

    @Test
    public void notAListAtAll() throws IOException {
        assertNull(parse(""));
        assertNull(parse("\n\n"));
        assertNull(parse("<!DOCTYPE html>\n<html>404: Not Found</html>\n"));
        assertNull(parse("404: Not Found"));
        assertNull(parse(BLOCKED + "\n")); // no header: not one of these lists
        assertNull(AiSlopMatcher.parse(null));
    }

    @Test
    public void channelIdShape() {
        assertTrue(AiSlopMatcher.isChannelId(BLOCKED));
        assertTrue(AiSlopMatcher.isChannelId(BLOCKED_2));
        assertFalse(AiSlopMatcher.isChannelId(null));
        assertFalse(AiSlopMatcher.isChannelId(""));
        assertFalse(AiSlopMatcher.isChannelId("UC"));
        assertFalse(AiSlopMatcher.isChannelId("ucaaaaaaaaaaaaaaaaaaaaaa"));
        assertFalse(AiSlopMatcher.isChannelId("UCaaaaaaaaaaaaaaaaaaaaa+"));
    }

    @Test
    public void listedOnEither() {
        AiSlopMatcher matcher = lists();

        assertTrue(matcher.isListed(BLOCKED));
        assertTrue(matcher.isListed(WARNED));
        assertFalse(matcher.isListed(OTHER));
        assertFalse(matcher.isListed(null));
        assertFalse(matcher.isListed(BLOCKED.toLowerCase())); // IDs are case-sensitive
        assertEquals(2, matcher.getBlocklistSize());
        assertEquals(1, matcher.getWarnlistSize());
    }

    @Test
    public void kidsProfileHidesBothLists() {
        AiSlopMatcher matcher = lists();

        assertEquals(AiSlopMatcher.HIDE, matcher.decide(BLOCKED, true, true));
        assertEquals(AiSlopMatcher.HIDE, matcher.decide(WARNED, true, true));
        // Labels off is a grown-up's choice; kids still don't get these
        assertEquals(AiSlopMatcher.HIDE, matcher.decide(BLOCKED, true, false));
        assertEquals(AiSlopMatcher.HIDE, matcher.decide(WARNED, true, false));
        assertEquals(AiSlopMatcher.SHOW, matcher.decide(OTHER, true, true));
        assertEquals(AiSlopMatcher.SHOW, matcher.decide(null, true, true));
    }

    @Test
    public void grownUpsGetALabel() {
        AiSlopMatcher matcher = lists();

        assertEquals(AiSlopMatcher.LABEL, matcher.decide(BLOCKED, false, true));
        assertEquals(AiSlopMatcher.LABEL, matcher.decide(WARNED, false, true));
        assertEquals(AiSlopMatcher.SHOW, matcher.decide(OTHER, false, true));
        assertEquals(AiSlopMatcher.SHOW, matcher.decide(null, false, true));
    }

    @Test
    public void labelsOffShowsAsIs() {
        AiSlopMatcher matcher = lists();

        assertEquals(AiSlopMatcher.SHOW, matcher.decide(BLOCKED, false, false));
        assertEquals(AiSlopMatcher.SHOW, matcher.decide(WARNED, false, false));
    }

    @Test
    public void emptyListsDoNothing() {
        assertTrue(AiSlopMatcher.EMPTY.isEmpty());
        assertEquals(AiSlopMatcher.SHOW, AiSlopMatcher.EMPTY.decide(BLOCKED, true, true));
        assertTrue(AiSlopMatcher.of(null, Collections.emptySet()).isEmpty());
        assertFalse(lists().isEmpty());
    }
}
