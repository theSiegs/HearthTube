package com.liskovsoft.smartyoutubetv2.common.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.text.Normalizer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * HearthTube: AiSList's two lists of channels that mainly post AI-made videos, by channel ID, and what to do with a
 * video from one of them. Plain Java, no Android: {@link AiSlopList} keeps the lists up to date and asks this.
 * <p>
 * List format: lines starting with ! are comments (the header: source, license, counts), every other line is one
 * channel ID ("UC" and 22 more characters). The channel lists (.tsv) have the channel's name after its ID and a tab:
 * most video tiles from YouTube's TV pages have the channel's name but not its ID, so kids profiles also go by the
 * name there. Only an exact name counts (see {@link #normalizeName}), and only for hiding: a label always needs the ID.
 */
public final class AiSlopMatcher {
    public static final AiSlopMatcher EMPTY = new AiSlopMatcher(null, null, null);

    /** What a card does: shows as it is, shows with a "Likely AI" label, or is left out */
    public static final int SHOW = 0;
    public static final int LABEL = 1;
    public static final int HIDE = 2;

    private static final int CHANNEL_ID_LENGTH = 24;
    /** Any run of spaces, tabs, line ends and the like */
    private static final Pattern SPACES = Pattern.compile("[\\s\\p{Z}]+");

    /** High confidence */
    private final Set<String> mBlocklist;
    /** Medium confidence */
    private final Set<String> mWarnlist;
    /** Both lists' channel names, as {@link #normalizeName} has them */
    private final Set<String> mNames;

    private AiSlopMatcher(Set<String> blocklist, Set<String> warnlist, Set<String> names) {
        mBlocklist = blocklist != null ? blocklist : Collections.emptySet();
        mWarnlist = warnlist != null ? warnlist : Collections.emptySet();
        mNames = names != null ? names : Collections.emptySet();
    }

    public static AiSlopMatcher of(Set<String> blocklist, Set<String> warnlist) {
        return new AiSlopMatcher(blocklist, warnlist, null);
    }

    /** @param names from {@link #parseNames}, both lists' together */
    public static AiSlopMatcher of(Set<String> blocklist, Set<String> warnlist, Set<String> names) {
        return new AiSlopMatcher(blocklist, warnlist, names);
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
            line = line.replace("\uFEFF", "").trim();

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

    /**
     * The channel names in a channel list (ID, tab, name), as {@link #normalizeName} has them. Skips comments, blank
     * lines, lines without a channel ID and a tab, and names with nothing left once normalized. Null when it isn't one
     * of these lists at all, like {@link #parse}.
     */
    public static Set<String> parseNames(Reader reader) throws IOException {
        if (reader == null) {
            return null;
        }

        BufferedReader lines = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
        Set<String> names = new HashSet<>();
        boolean first = true;
        String line;

        while ((line = lines.readLine()) != null) {
            line = line.replace("\uFEFF", "");

            if (first) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                if (!line.trim().startsWith("!")) {
                    return null;
                }
                first = false;
            }

            int tab = line.indexOf('\t');

            if (tab < 0 || !isChannelId(line.substring(0, tab).trim())) {
                continue;
            }

            String name = normalizeName(line.substring(tab + 1));

            if (!name.isEmpty()) {
                names.add(name);
            }
        }

        return first ? null : names;
    }

    /**
     * A channel name the way names are compared: Unicode compatibility forms folded (NFKC: full-width letters,
     * ligatures, styled letters), case folded, spaces trimmed and each run of them made one. Nothing else: "Cats" and
     * "Cats TV" stay different names. Never null.
     */
    public static String normalizeName(String name) {
        if (name == null) {
            return "";
        }

        String folded = Normalizer.normalize(name, Normalizer.Form.NFKC);
        // Upper then lower case folds more than lower case alone (ß and ss, final and other sigma)
        folded = folded.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
        folded = Normalizer.normalize(folded, Normalizer.Form.NFKC);

        return SPACES.matcher(folded).replaceAll(" ").trim();
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

    /** A listed channel's name, exactly (see {@link #normalizeName}) */
    public boolean isListedName(String channelName) {
        return channelName != null && !mNames.isEmpty() && mNames.contains(normalizeName(channelName));
    }

    public boolean isEmpty() {
        return mBlocklist.isEmpty() && mWarnlist.isEmpty() && mNames.isEmpty();
    }

    public int getBlocklistSize() {
        return mBlocklist.size();
    }

    public int getWarnlistSize() {
        return mWarnlist.size();
    }

    public int getNamesSize() {
        return mNames.size();
    }

    /** By the channel ID alone */
    public int decide(String channelId, boolean kids, boolean labels) {
        return decide(channelId, null, kids, labels);
    }

    /**
     * Kids profiles: a video from a channel on either list is left out; so is one with no channel ID whose channel name
     * is exactly a listed channel's. Everyone else sees it, labeled "Likely AI" unless labels are off, but only by its
     * ID: different channels can have the same name.
     */
    public int decide(String channelId, String channelName, boolean kids, boolean labels) {
        if (channelId == null || channelId.isEmpty()) {
            return kids && isListedName(channelName) ? HIDE : SHOW;
        }

        if (!isListed(channelId)) {
            return SHOW;
        }

        if (kids) {
            return HIDE;
        }

        return labels ? LABEL : SHOW;
    }
}
