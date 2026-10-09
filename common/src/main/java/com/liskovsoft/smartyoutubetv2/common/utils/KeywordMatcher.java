package com.liskovsoft.smartyoutubetv2.common.utils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * HearthTube: finds blocked words the way people read them. Whole words or phrases; case, accents and punctuation
 * don't count ("pokemon" finds "Pokémon!", "spider man" finds "Spider-Man"), but a word has to be whole: "live"
 * doesn't find "Delivery". Languages written without spaces between words (Chinese, Japanese, Korean, Thai and their
 * neighbors) have no whole words to go by, so there a word is found anywhere. No regex: what's typed is what's found.
 * The words are folded once, when the list is made; each title is folded as it's checked.
 */
public final class KeywordMatcher {
    public static final KeywordMatcher EMPTY = new KeywordMatcher(new String[0]);
    private final String[] mWords;

    private KeywordMatcher(String[] words) {
        mWords = words;
    }

    /** Words that fold to nothing (only punctuation or emoji) are left out, and so are repeats */
    public static KeywordMatcher compile(Collection<String> words) {
        if (words == null || words.isEmpty()) {
            return EMPTY;
        }

        Set<String> folded = new LinkedHashSet<>();

        for (String word : words) {
            String key = word != null ? fold(word) : "";

            if (!key.isEmpty()) {
                folded.add(key);
            }
        }

        return folded.isEmpty() ? EMPTY : new KeywordMatcher(folded.toArray(new String[0]));
    }

    public boolean isEmpty() {
        return mWords.length == 0;
    }

    /** One of the texts (a title, a channel name) has one of the words. Each text on its own: a phrase can't span two. */
    public boolean matches(String... texts) {
        if (mWords.length == 0 || texts == null) {
            return false;
        }

        for (String text : texts) {
            if (text == null || text.isEmpty()) {
                continue;
            }

            String folded = fold(text);

            for (String word : mWords) {
                if (contains(folded, word)) {
                    return true;
                }
            }
        }

        return false;
    }

    /** Two words (or phrases) are the same once folded: "Pokémon" and "POKEMON" */
    public static boolean isSame(String first, String second) {
        return first != null && second != null && fold(first).equals(fold(second));
    }

    /** Has a letter or a digit to go by: "!!!" or an emoji alone would find nothing */
    public static boolean isUsable(String word) {
        return word != null && !fold(word).isEmpty();
    }

    /**
     * The words of a title, as written, for picking from: split at spaces, punctuation trimmed from the ends ("(Official"
     * is "Official"; "Spider-Man" and "don't" stay whole), single letters and repeats left out.
     */
    public static List<String> splitWords(String title) {
        List<String> result = new ArrayList<>();

        if (title == null) {
            return result;
        }

        Set<String> seen = new LinkedHashSet<>();

        for (String part : title.trim().split("\\s+")) {
            int start = 0;
            int end = part.length();

            while (start < end && !isWordChar(part.codePointAt(start))) {
                start += Character.charCount(part.codePointAt(start));
            }

            while (end > start && !isWordChar(part.codePointBefore(end))) {
                end -= Character.charCount(part.codePointBefore(end));
            }

            String word = part.substring(start, end);
            String key = fold(word);

            if (key.isEmpty() || (key.codePointCount(0, key.length()) == 1 && !isUnspaced(key.codePointAt(0)))) {
                continue;
            }

            if (seen.add(key)) {
                result.add(word);
            }
        }

        return result;
    }

    static boolean contains(String text, String word) {
        int from = 0;

        while (true) {
            int at = text.indexOf(word, from);

            if (at < 0) {
                return false;
            }

            if (isBoundary(text, at) && isBoundary(text, at + word.length())) {
                return true;
            }

            from = at + 1;
        }
    }

    /** Where a word may start or stop: an end, a space, or next to a language written without spaces */
    private static boolean isBoundary(String text, int index) {
        if (index == 0 || index == text.length()) {
            return true;
        }

        int before = text.codePointBefore(index);
        int after = text.codePointAt(index);

        return !isWordChar(before) || !isWordChar(after) || isUnspaced(before) || isUnspaced(after);
    }

    /**
     * Lower case; compatibility forms made plain (full-width letters, ligatures, circled digits); accents off Latin,
     * Greek and Cyrillic letters (other scripts keep their marks, they're part of the letter); each run of spaces,
     * punctuation and symbols one space, or none next to a language written without spaces.
     */
    static String fold(String text) {
        String decomposed = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD);
        StringBuilder result = new StringBuilder(decomposed.length());
        int last = 0; // last letter or digit written
        boolean gap = false;

        for (int i = 0; i < decomposed.length(); ) {
            int c = decomposed.codePointAt(i);
            i += Character.charCount(c);

            if (isMark(c)) {
                if (last != 0 && !gap && !isAccentFolded(last)) {
                    result.appendCodePoint(c);
                }
                continue;
            }

            if (!Character.isLetterOrDigit(c)) {
                gap = last != 0;
                continue;
            }

            c = foldLetter(c);

            if (gap && !isUnspaced(last) && !isUnspaced(c)) {
                result.append(' ');
            }

            if (c == 'ß') {
                result.append("ss");
            } else {
                result.appendCodePoint(c);
            }

            last = c;
            gap = false;
        }

        // Puts Korean syllables (and other scripts' marks) back together
        return Normalizer.normalize(result, Normalizer.Form.NFC);
    }

    /** Case folding that lower case alone misses */
    private static int foldLetter(int c) {
        switch (c) {
            case 'ı': // Turkish dotless i: "KIZ" lower-cases to "kiz"
                return 'i';
            case 'ς': // Greek final sigma
                return 'σ';
            default:
                return c;
        }
    }

    private static boolean isWordChar(int c) {
        return Character.isLetterOrDigit(c) || isMark(c);
    }

    private static boolean isMark(int c) {
        int type = Character.getType(c);
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    /** Latin, Greek and Cyrillic: their accents don't count */
    private static boolean isAccentFolded(int c) {
        return c < 0x0530 || (c >= 0x1E00 && c <= 0x1FFF);
    }

    /** Scripts written without spaces between words (Korean is spaced, but its endings join the word: "게임을") */
    private static boolean isUnspaced(int c) {
        return (c >= 0x0E00 && c <= 0x0EFF) // Thai, Lao
                || (c >= 0x1000 && c <= 0x109F) // Myanmar
                || (c >= 0x1100 && c <= 0x11FF) // Hangul jamo
                || (c >= 0x1780 && c <= 0x17FF) // Khmer
                || (c >= 0x2E80 && c <= 0x9FFF) // CJK radicals, kana, bopomofo, Hangul compatibility jamo, ideographs
                || (c >= 0xA960 && c <= 0xA97F) // Hangul jamo extended A
                || (c >= 0xAC00 && c <= 0xD7FF) // Hangul syllables, jamo extended B
                || (c >= 0xF900 && c <= 0xFAFF) // CJK compatibility ideographs
                || (c >= 0xFF66 && c <= 0xFF9F) // half-width katakana
                || (c >= 0x20000 && c <= 0x3FFFF); // CJK extensions
    }
}
