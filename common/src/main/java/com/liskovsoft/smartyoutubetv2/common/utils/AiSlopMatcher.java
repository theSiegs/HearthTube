package com.liskovsoft.smartyoutubetv2.common.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * HearthTube: AiSList's two lists of channels that mainly post AI-made videos, by channel ID, and what to do with a
 * video from one of them. Plain Java, no Android: {@link AiSlopList} keeps the lists up to date and asks this.
 * <p>
 * List format: lines starting with ! are comments (the header: source, license, counts), every other line is one
 * channel ID ("UC" and 22 more characters).
 */
public final class AiSlopMatcher {
    public static final AiSlopMatcher EMPTY = new AiSlopMatcher(null, null);

    /** What a card does: shows as it is, shows with a "Likely AI" label, or is left out */
    public static final int SHOW = 0;
    public static final int LABEL = 1;
    public static final int HIDE = 2;

    private static final int CHANNEL_ID_LENGTH = 24;

    /** High confidence */
    private final Set<String> mBlocklist;
    /** Medium confidence */
    private final Set<String> mWarnlist;

    private AiSlopMatcher(Set<String> blocklist, Set<String> warnlist) {
        mBlocklist = blocklist != null ? blocklist : Collections.emptySet();
        mWarnlist = warnlist != null ? warnlist : Collections.emptySet();
    }

    public static AiSlopMatcher of(Set<String> blocklist, Set<String> warnlist) {
        return new AiSlopMatcher(blocklist, warnlist);
    }

    /**
     * The channel IDs in a list. Skips comments, blank lines and anything that isn't a channel ID; Windows line ends
     * and a byte order mark are fine. Null when it isn't one of these lists at all (its first line isn't a ! comment:
     * an error page, say), so a bad download doesn't replace a good copy.
     */
    public static Set<String> parse(Reader reader) throws IOException {
        if (reader == null) {
            return null;
        }

        BufferedReader lines = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
        Set<String> ids = new HashSet<>();
        boolean first = true;
        String line;

        while ((line = lines.readLine()) != null) {
            line = line.replace("﻿", "").trim();

            if (first) {
                if (line.isEmpty()) {
                    continue;
                }
                if (!line.startsWith("!")) {
                    return null;
                }
                first = false;
            }

            if (isChannelId(line)) {
                ids.add(line);
            }
        }

        return first ? null : ids;
    }

    /** "UC" and 22 letters, digits, - or _ */
    public static boolean isChannelId(String text) {
        if (text == null || text.length() != CHANNEL_ID_LENGTH || !text.startsWith("UC")) {
            return false;
        }

        for (int i = 2; i < CHANNEL_ID_LENGTH; i++) {
            char c = text.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!ok) {
                return false;
            }
        }

        return true;
    }

    /** On either list */
    public boolean isListed(String channelId) {
        return channelId != null && (mBlocklist.contains(channelId) || mWarnlist.contains(channelId));
    }

    public boolean isEmpty() {
        return mBlocklist.isEmpty() && mWarnlist.isEmpty();
    }

    public int getBlocklistSize() {
        return mBlocklist.size();
    }

    public int getWarnlistSize() {
        return mWarnlist.size();
    }

    /**
     * Kids profiles: a video from a channel on either list is left out. Everyone else sees it, labeled "Likely AI"
     * unless labels are off.
     */
    public int decide(String channelId, boolean kids, boolean labels) {
        if (!isListed(channelId)) {
            return SHOW;
        }

        if (kids) {
            return HIDE;
        }

        return labels ? LABEL : SHOW;
    }
}
