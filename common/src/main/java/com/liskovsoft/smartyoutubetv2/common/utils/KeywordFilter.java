package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HearthTube: blocked words. A video with one in its title or channel name is left out wherever blocked channels
 * are (every row and grid, search, the player's suggestions), and doesn't play next on its own either. In a Google TV
 * kids profile the player also refuses it, however it got there (a link, a cast), the way it refuses Shorts. A grown-up
 * who opens one directly still gets it.
 * <p>
 * One list per Google TV profile: each is its own Android user, with its own copy of HearthTube's data. Empty at first.
 * How words are found is {@link KeywordMatcher}.
 */
public final class KeywordFilter {
    private static final String PREFS = "hearthtube_blocked_words";
    private static final String WORDS = "words";
    /** As added (shown in Settings); null until read */
    private static volatile List<String> sWords;
    private static volatile KeywordMatcher sMatcher = KeywordMatcher.EMPTY;
    /** Videos left out so far, so a search can tell its results were all blocked */
    private static final AtomicInteger sHiddenCount = new AtomicInteger();

    private KeywordFilter() {
    }

    public static List<String> getWords(Context context) {
        load(context);
        List<String> words = sWords;
        return words != null ? words : Collections.emptyList();
    }

    public static boolean contains(Context context, String word) {
        for (String blocked : getWords(context)) {
            if (KeywordMatcher.isSame(blocked, word)) {
                return true;
            }
        }

        return false;
    }

    /** False when it's already there, or has no letter or digit to go by */
    public static boolean add(Context context, String word) {
        String clean = word != null ? word.trim().replaceAll("\\s+", " ") : null;

        if (context == null || !KeywordMatcher.isUsable(clean) || contains(context, clean)) {
            return false;
        }

        List<String> words = new ArrayList<>(getWords(context));
        words.add(clean);
        save(context, words);
        return true;
    }

    public static void remove(Context context, String word) {
        List<String> words = new ArrayList<>(getWords(context));

        for (int i = words.size() - 1; i >= 0; i--) {
            if (KeywordMatcher.isSame(words.get(i), word)) {
                words.remove(i);
            }
        }

        save(context, words);
    }

    /** Rows, grids, search and suggestions (VideoGroup): leave this one out */
    public static boolean isHidden(Video video) {
        if (video == null || video.isChapter) {
            return false;
        }

        KeywordMatcher matcher = matcher();

        if (matcher.isEmpty() || !matcher.matches(video.title, video.deArrowTitle, video.getAuthor())) {
            return false;
        }

        sHiddenCount.incrementAndGet();
        return true;
    }

    /** Videos left out so far; compare two counts to see if any were */
    public static int getHiddenCount() {
        return sHiddenCount.get();
    }

    /**
     * Why a search shows nothing, given {@link #getHiddenCount()} from before it: its results had blocked words (or
     * were Shorts), or 0 when nothing was left out.
     */
    public static int getEmptySearchMessage(int hiddenBefore) {
        boolean words = getHiddenCount() != hiddenBefore;
        boolean shorts = KidsShorts.isHidingShorts();

        if (words) {
            return shorts ? R.string.search_only_shorts_or_blocked : R.string.search_only_blocked;
        }

        return shorts ? R.string.search_only_shorts : 0;
    }

    /**
     * What plays next on its own: YouTube's pick unless it has a blocked word, else the next one after it that
     * doesn't (in a playlist, the rest of it), else the first suggestion that doesn't. Null when none is left. In a
     * kids profile the same goes for videos from AI channels ({@link AiSlopList}).
     */
    public static MediaItem checkNext(MediaItem next, MediaItemMetadata metadata, String currentVideoId) {
        if (next == null || !isHiddenNext(next)) {
            return next;
        }

        List<MediaGroup> suggestions = metadata != null ? metadata.getSuggestions() : null;

        if (suggestions == null) {
            return null;
        }

        MediaItem fallback = null;

        for (MediaGroup group : suggestions) {
            List<MediaItem> items = group != null ? group.getMediaItems() : null;

            if (items == null) {
                continue;
            }

            // The group YouTube's pick came from (a playlist, Up next) goes on after it; any other, from the top
            boolean hasNext = hasVideo(items, next.getVideoId());
            boolean afterNext = false;

            for (MediaItem item : items) {
                if (item == null || item.getVideoId() == null) {
                    continue;
                }

                if (item.getVideoId().equals(next.getVideoId())) {
                    afterNext = true;
                } else if (!item.getVideoId().equals(currentVideoId) && !isHiddenNext(item)) {
                    if (afterNext) {
                        return item;
                    }

                    if (!hasNext && fallback == null) {
                        fallback = item;
                    }
                }
            }
        }

        return fallback;
    }

    /**
     * Kids profiles: the player refuses a video with a blocked word. Checked as the video loads (the tile's title, or
     * the player data's) and again once its details are in: a link or a cast has no title before that.
     */
    public static boolean isBlockedInPlayer(Context context, MediaItemFormatInfo formatInfo, Video video) {
        KeywordMatcher matcher = matcher(context);

        if (matcher.isEmpty() || video == null || !ParentGate.isKidsProfile(context)) {
            return false;
        }

        return matcher.matches(video.title, video.getTitleFull(), video.getAuthor())
                || (formatInfo != null && matcher.matches(formatInfo.getTitle(), formatInfo.getAuthor()));
    }

    private static boolean isHiddenNext(MediaItem item) {
        if (AiSlopList.isHidden(item)) {
            return true;
        }

        KeywordMatcher matcher = matcher();

        if (matcher.isEmpty()) {
            return false;
        }

        Video video = Video.from(item);
        return video != null && matcher.matches(video.title, video.getAuthor());
    }

    private static boolean hasVideo(List<MediaItem> items, String videoId) {
        for (MediaItem item : items) {
            if (item != null && videoId != null && videoId.equals(item.getVideoId())) {
                return true;
            }
        }

        return false;
    }

    private static KeywordMatcher matcher() {
        return matcher(null);
    }

    /** The list is read on first use, with whichever context is at hand */
    private static KeywordMatcher matcher(Context context) {
        if (sWords == null) {
            load(context != null ? context : GlobalPreferences.context());
        }

        return sMatcher;
    }

    private static synchronized void load(Context context) {
        if (sWords != null || context == null) {
            return;
        }

        String saved = prefs(context).getString(WORDS, "");
        List<String> words = new ArrayList<>();

        for (String word : saved.split("\n")) {
            if (!word.trim().isEmpty()) {
                words.add(word);
            }
        }

        apply(words);
    }

    private static synchronized void save(Context context, List<String> words) {
        if (context == null) {
            return;
        }

        StringBuilder joined = new StringBuilder();

        for (String word : words) {
            if (joined.length() > 0) {
                joined.append('\n');
            }
            joined.append(word);
        }

        prefs(context).edit().putString(WORDS, joined.toString()).apply();
        apply(words);
    }

    /** Folds the words once here, not for every video */
    private static void apply(List<String> words) {
        sMatcher = KeywordMatcher.compile(words);
        sWords = Collections.unmodifiableList(new ArrayList<>(words));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
