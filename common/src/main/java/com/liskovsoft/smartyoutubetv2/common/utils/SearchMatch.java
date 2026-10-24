package com.liskovsoft.smartyoutubetv2.common.utils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * HearthTube: YouTube puts a "Previously watched" shelf among the search results. It's useful when those videos
 * are about what was searched for, and clutter when they're not: so only the ones that match stay. A video matches
 * when every word of the search is in its title or its channel's name (no case, no accents, no punctuation).
 */
public final class SearchMatch {
    private static final String PREVIOUSLY_WATCHED = "previously watched";

    private SearchMatch() {
    }

    /** The results shelf of videos watched before (YouTube's own title for it) */
    public static boolean isPreviouslyWatchedShelf(String shelfTitle) {
        return shelfTitle != null && shelfTitle.toLowerCase(Locale.ROOT).contains(PREVIOUSLY_WATCHED);
    }

    /** Every word of the search is in the title or the channel's name */
    public static boolean matches(String search, String title, String author) {
        List<String> words = words(search);

        if (words.isEmpty()) {
            return true;
        }

        String text = normalize((title != null ? title : "") + " " + (author != null ? author : ""));

        for (String word : words) {
            if (!text.contains(word)) {
                return false;
            }
        }

        return true;
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
