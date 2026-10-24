package com.liskovsoft.smartyoutubetv2.common.utils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * HearthTube: YouTube puts a "Previously watched" shelf among the search results. It's useful when those videos
 * are about what was searched for, and clutter when they're not: so only the ones that match stay.
 * <p>
 * Matching is soft, because what's searched is often not what was meant (voice search heard "wind" for "wynns"): a
 * word of the search matches a word of the title or channel when they're the same, one starts the other, or they are
 * spelled alike (Jaro-Winkler, the usual measure for names). A video matches when most of the search's words do
 * ({@link #ENOUGH}): one wrong word in four doesn't hide it.
 */
public final class SearchMatch {
    private static final String PREVIOUSLY_WATCHED = "previously watched";
    /** The share of the search's words that have to be found */
    static final double ENOUGH = 0.6;
    /** How alike two words are spelled to count as the same word */
    static final double ALIKE = 0.88;

    private SearchMatch() {
    }

    /** The results shelf of videos watched before (YouTube's own title for it) */
    public static boolean isPreviouslyWatchedShelf(String shelfTitle) {
        return shelfTitle != null && shelfTitle.toLowerCase(Locale.ROOT).contains(PREVIOUSLY_WATCHED);
    }

    /** Most words of the search are in the title or the channel's name, or something spelled like them */
    public static boolean matches(String search, String title, String author) {
        List<String> wanted = words(search);

        if (wanted.isEmpty()) {
            return true;
        }

        List<String> have = words((title != null ? title : "") + " " + (author != null ? author : ""));
        int found = 0;

        for (String word : wanted) {
            if (isIn(word, have)) {
                found++;
            }
        }

        return found >= Math.ceil(wanted.size() * ENOUGH);
    }

    private static boolean isIn(String word, List<String> have) {
        for (String other : have) {
            if (same(word, other)) {
                return true;
            }
        }

        return false;
    }

    static boolean same(String a, String b) {
        if (a.equals(b)) {
            return true;
        }

        // "wynn" for "wynns", "boat" for "boats": one starts the other (not for very short words)
        if (Math.min(a.length(), b.length()) >= 3 && (a.startsWith(b) || b.startsWith(a))) {
            return true;
        }

        return jaroWinkler(a, b) >= ALIKE;
    }

    /** 1 for the same word, 0 for nothing alike; weighs the start of the words most */
    static double jaroWinkler(String a, String b) {
        int la = a.length();
        int lb = b.length();

        if (la == 0 || lb == 0) {
            return 0;
        }

        int window = Math.max(0, Math.max(la, lb) / 2 - 1);
        boolean[] usedA = new boolean[la];
        boolean[] usedB = new boolean[lb];
        int matches = 0;

        for (int i = 0; i < la; i++) {
            for (int j = Math.max(0, i - window); j < Math.min(lb, i + window + 1); j++) {
                if (!usedB[j] && a.charAt(i) == b.charAt(j)) {
                    usedA[i] = true;
                    usedB[j] = true;
                    matches++;
                    break;
                }
            }
        }

        if (matches == 0) {
            return 0;
        }

        int swaps = 0;
        int k = 0;

        for (int i = 0; i < la; i++) {
            if (usedA[i]) {
                while (!usedB[k]) {
                    k++;
                }
                if (a.charAt(i) != b.charAt(k)) {
                    swaps++;
                }
                k++;
            }
        }

        double m = matches;
        double jaro = (m / la + m / lb + (m - swaps / 2.0) / m) / 3;
        int prefix = 0;

        while (prefix < Math.min(4, Math.min(la, lb)) && a.charAt(prefix) == b.charAt(prefix)) {
            prefix++;
        }

        return jaro + prefix * 0.1 * (1 - jaro);
    }

    private static List<String> words(String text) {
        List<String> words = new ArrayList<>();

        for (String word : normalize(text).split(" ")) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }

        return words;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }

        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");

        return plain.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
