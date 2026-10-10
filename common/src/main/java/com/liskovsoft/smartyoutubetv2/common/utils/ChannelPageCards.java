package com.liskovsoft.smartyoutubetv2.common.utils;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HearthTube: YouTube's TV channel pages leave the channel's ID off their video cards, so what goes by the channel
 * (blocked channels; AiSList, where kids profiles leave a channel's videos out and everyone else sees "Likely AI") had
 * only the channel's name to go by there, or nothing. The page is one channel's, though, and its ID is the one the page
 * was asked for: this puts it on the page's own cards before VideoGroup.add filters them.
 * <p>
 * Not every card on a channel page is the channel's own. Featured channels and playlists have IDs of their own, and a
 * card keeps any ID it has. Shelves of other creators' videos and collaborations other channels own have none, but a
 * card's long-press menu line names the channel that owns its video, "Name • @handle" (a collaboration's says "A and
 * B • @handle", the owner's): a card gets the page's ID only when that handle is the page channel's, or, on a card
 * without one, when its channel name is exactly the page channel's. Who the page channel is comes from the card that
 * opened the page; with none (a link, a cast), from the cards themselves, when nearly all of them share one handle.
 * Without that, nothing is stamped.
 * <p>
 * Plain Java on the cards' text, no Android.
 */
public final class ChannelPageCards {
    /** Cards that must share a handle before it's taken to be the page channel's */
    static final int MIN_VOTES = 3;
    /** ... and the share of the page's cards that must have it: 3 in 4 */
    private static final int SHARE_NUMERATOR = 3;
    private static final int SHARE_DENOMINATOR = 4;
    private static final String DELIM = Video.TERTIARY_TEXT_DELIM;

    private final String mChannelId;
    /** The page channel's handle, as the card that opened the page or a card with its name had it */
    private String mHandle;
    /** The page channel's name, from the card that opened the page */
    private final String mName;
    /** Cards without an ID, by handle (lower case) */
    private final Map<String, Integer> mVotes = new HashMap<>();
    /** The first name seen with each handle (lower case) */
    private final Map<String, String> mNames = new HashMap<>();
    /** Each handle (lower case) as the first card with it spelled it */
    private final Map<String, String> mSpellings = new HashMap<>();
    private int mVoteCount;
    private int mStamped;

    ChannelPageCards(String channelId, String handle, String name) {
        mChannelId = channelId;
        mHandle = isBlank(handle) ? null : handle.trim();
        mName = isBlank(name) ? null : name.trim();
    }

    /**
     * The page of a channel, by the ID it was asked for; {@code opener} is the card that opened it (any card, it only
     * counts when it has this same channel ID). Null when the ID isn't a channel ID ("UC..."): an "@handle" from a link,
     * say.
     */
    public static ChannelPageCards of(String channelId, Video opener) {
        if (!AiSlopMatcher.isChannelId(channelId)) {
            return null;
        }

        if (opener == null || !channelId.equals(opener.channelId)) {
            return new ChannelPageCards(channelId, null, null);
        }

        // A channel's own card: its name is the title, its handle in the line under it ("@handle • 1.2M subscribers")
        boolean channelCard = opener.videoId == null && opener.playlistId == null;
        String handle = handleOf(opener.author);
        if (handle == null) {
            handle = handleOf(opener.secondTitle);
        }
        String name = channelCard ? opener.title : firstNonNull(nameOf(opener.author), nameOf(opener.secondTitle));

        return new ChannelPageCards(channelId, handle, name);
    }

    public String getChannelId() {
        return mChannelId;
    }

    /** Cards given the page's ID so far */
    public int getStamped() {
        return mStamped;
    }

    /** A row of the page, before its cards are made: who the page channel is, from them */
    public void learn(MediaGroup group) {
        List<MediaItem> items = group != null ? group.getMediaItems() : null;

        if (items == null) {
            return;
        }

        for (MediaItem item : items) {
            if (item != null) {
                learn(item.getChannelId(), item.getAuthor(), item.getSecondTitle());
            }
        }
    }

    /** One card of the page: its own channel ID (most have none), its menu line and the line under its title */
    void learn(String channelId, String authorLine, CharSequence secondTitle) {
        if (!isBlank(channelId)) {
            return; // a featured channel, a playlist: theirs
        }

        String handle = handleOf(authorLine);

        if (handle == null) {
            return;
        }

        String key = handle.toLowerCase(Locale.ROOT);
        String name = firstNonNull(nameOf(authorLine), nameOf(secondTitle));

        // The opener's name on a card: now the page channel's handle is known too
        if (mHandle == null && mName != null && sameName(name, mName)) {
            mHandle = handle;
        }

        Integer votes = mVotes.get(key);
        mVotes.put(key, votes != null ? votes + 1 : 1);
        mVoteCount++;

        if (name != null && !mNames.containsKey(key)) {
            mNames.put(key, name);
        }

        if (!mSpellings.containsKey(key)) {
            mSpellings.put(key, handle);
        }
    }

    /**
     * The page channel's handle: known from the card that opened the page, or one with its name, or, when the page was
     * opened without one, the handle nearly all the page's cards have. Null when it can't be told.
     */
    String getHandle() {
        if (mHandle != null || mName != null) {
            return mHandle;
        }

        String top = null;
        int topVotes = 0;

        for (Map.Entry<String, Integer> entry : mVotes.entrySet()) {
            if (entry.getValue() > topVotes) {
                top = entry.getKey();
                topVotes = entry.getValue();
            }
        }

        boolean clear = topVotes >= MIN_VOTES && topVotes * SHARE_DENOMINATOR >= mVoteCount * SHARE_NUMERATOR;
        return clear ? mSpellings.get(top) : null;
    }

    /** The page channel's name, when known: the opener's, or the one its cards have */
    String getName() {
        if (mName != null) {
            return mName;
        }

        String handle = getHandle();
        return handle != null ? mNames.get(handle.toLowerCase(Locale.ROOT)) : null;
    }

    /**
     * Gives a card of the page the page's channel ID, and its name when the card has none, if it's the channel's own
     * video and has no channel ID yet. True when it did.
     */
    public boolean stamp(Video video) {
        if (video == null || video.isChapter || video.videoId == null || !isBlank(video.channelId)
                || !isOwn(video.author, video.secondTitle)) {
            return false;
        }

        video.channelId = mChannelId;

        String name = getName();
        if (isBlank(video.author) && name != null) {
            video.author = name;
        }

        mStamped++;
        return true;
    }

    /** The card's video is the page channel's: by its owner's handle, or, without one, by its channel name exactly */
    boolean isOwn(String authorLine, CharSequence secondTitle) {
        String handle = firstNonNull(handleOf(authorLine), handleOf(secondTitle));
        String pageHandle = getHandle();

        if (handle != null && pageHandle != null) {
            return handle.equalsIgnoreCase(pageHandle);
        }

        String name = getName();
        return name != null && sameName(firstNonNull(nameOf(authorLine), nameOf(secondTitle)), name);
    }

    /** The "@handle" part of a card's line ("Name • @handle"), or null */
    static String handleOf(CharSequence line) {
        if (line == null) {
            return null;
        }

        for (String part : line.toString().split(DELIM)) {
            String trimmed = part.trim();
            if (trimmed.length() > 1 && trimmed.startsWith("@") && trimmed.indexOf(' ') < 0) {
                return trimmed;
            }
        }

        return null;
    }

    /** The channel name a card's line starts with ("Name • 1.2M views • 3 days ago"); null when it starts with a handle */
    static String nameOf(CharSequence line) {
        if (line == null) {
            return null;
        }

        String first = line.toString().split(DELIM)[0].trim();
        return first.isEmpty() || first.startsWith("@") ? null : first;
    }

    private static boolean sameName(String a, String b) {
        return a != null && b != null && AiSlopMatcher.normalizeName(a).equals(AiSlopMatcher.normalizeName(b));
    }

    private static boolean isBlank(CharSequence text) {
        return text == null || text.toString().trim().isEmpty();
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
