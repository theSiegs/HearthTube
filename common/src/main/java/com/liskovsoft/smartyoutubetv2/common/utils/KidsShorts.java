package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

/**
 * No Shorts by default (Look and layout > Show Shorts turns them on), and never in Google TV kids profiles: while
 * hidden, every "hide Shorts" option is forced on, so YouTube's Shorts rows come up empty and don't show. The user's
 * own Shorts options are saved first and put back when Shorts are shown again (an adult profile with Show Shorts on).
 * Settings are parent-locked in kids profiles, so a kid can't switch them back on.
 */
public final class KidsShorts {
    private static final String PREFS = "hearthtube_kids";
    private static final String FORCED = "shorts_forced";
    private static final String SHOW_SHORTS = "show_shorts";
    private static final String SAVED_PREFIX = "shorts_hidden_";
    /** Every Shorts flag, saved one by one: an adult may have hidden Shorts in some places only */
    private static final int[] FLAGS = {
            MediaServiceData.CONTENT_SHORTS_HOME, MediaServiceData.CONTENT_SHORTS_SEARCH, MediaServiceData.CONTENT_SHORTS_SUBSCRIPTIONS,
            MediaServiceData.CONTENT_SHORTS_HISTORY, MediaServiceData.CONTENT_SHORTS_TRENDING, MediaServiceData.CONTENT_SHORTS_CHANNEL,
            MediaServiceData.CONTENT_SHORTS_NEWS
    };

    /** Longest video that counts as a Short (YouTube allows up to three minutes) */
    private static final long SHORT_MAX_MS = 3 * 60_000;
    /** Set by apply(): Shorts are hidden right now (kids profile, or Show Shorts off) */
    private static volatile boolean sHidden = true;
    /** Set by apply(): this is a kids profile */
    private static volatile boolean sKids;

    /**
     * Shorts from elsewhere: TikTok and Instagram clips, and compilations of them, are Shorts by another name. Matched
     * in the title or the channel name.
     */
    private static final java.util.regex.Pattern CLIPS = java.util.regex.Pattern.compile(
            "tik\\s*-?\\s*toks?|instagram|\\binsta\\s+reels?|#reels?\\b|\\breels compilation|#fyp\\b|\\bfyp\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private KidsShorts() {
    }

    /**
     * A video in a row that is a Short while Shorts are hidden: leave it out. YouTube marks only some Shorts, so also a
     * #shorts title, a Shorts thumbnail, a plain video with no length (search results list Shorts that way), and
     * TikTok or Instagram clips and their compilations.
     */
    public static boolean isHiddenShort(MediaItem item) {
        if (!sHidden || item == null) {
            return false;
        }

        if (item.isShorts()) {
            return true;
        }

        if (item.getVideoId() == null || item.isLive() || item.isUpcoming()) {
            return false;
        }

        String title = item.getTitle() != null ? item.getTitle().toLowerCase() : "";

        if (CLIPS.matcher(title).find() || (item.getAuthor() != null && CLIPS.matcher(item.getAuthor()).find())) {
            return true;
        }

        String image = item.getCardImageUrl() != null ? item.getCardImageUrl() : "";
        long durationMs = item.getDurationMs();

        // A mix or a playlist tile has no length either
        boolean noLength = durationMs <= 0 && item.getPlaylistId() == null;

        return title.contains("#short") || image.contains("/oar") || image.contains("-AG2CIACgA-") || noLength;
    }

    /**
     * The player's check, once the video's formats are known: a portrait video of up to three minutes is a Short, and
     * a TikTok or Instagram clip (or a compilation of them) counts too. In a kids profile neither plays, however it got
     * here (a search, a link, a cast, autoplay).
     */
    public static boolean isBlockedFormat(MediaItemFormatInfo formatInfo, String title, String author) {
        if (!sKids || formatInfo == null || formatInfo.isLive()) {
            return false;
        }

        if ((title != null && CLIPS.matcher(title).find()) || (author != null && CLIPS.matcher(author).find())) {
            return true;
        }

        MediaFormat format = formatInfo.containsDashFormats() && !formatInfo.getAdaptiveFormats().isEmpty() ?
                formatInfo.getAdaptiveFormats().get(0) : null;

        if (format == null || format.getWidth() <= 0 || format.getHeight() <= 0 || format.getWidth() >= format.getHeight()) {
            return false;
        }

        long lengthMs;
        try {
            lengthMs = Long.parseLong(formatInfo.getLengthSeconds()) * 1_000;
        } catch (NumberFormatException | NullPointerException e) {
            lengthMs = 0;
        }

        return lengthMs <= SHORT_MAX_MS;
    }

    /** Look and layout > Show Shorts. Off by default; never applies in a kids profile. */
    public static boolean isShowShortsEnabled(Context context) {
        return prefs(context).getBoolean(SHOW_SHORTS, false);
    }

    public static void setShowShortsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(SHOW_SHORTS, enabled).apply();
        apply(context);
    }

    /**
     * Call on every launch (profile switches always go through the launcher) and when Show Shorts changes.
     */
    public static void apply(Context context) {
        if (context == null) {
            return;
        }

        SharedPreferences prefs = prefs(context);
        MediaServiceData data = MediaServiceData.instance();
        boolean forced = prefs.getBoolean(FORCED, false);
        boolean kids = ParentGate.isKidsProfile(context);
        boolean hide = kids || !isShowShortsEnabled(context);
        sHidden = hide;
        sKids = kids;

        if (hide && !forced) {
            SharedPreferences.Editor editor = prefs.edit();
            for (int flag : FLAGS) {
                editor.putBoolean(SAVED_PREFIX + flag, data.isContentHidden(flag));
            }
            editor.putBoolean(FORCED, true).apply();
        }

        if (hide) {
            // Also makes sure nothing switched it back in the meantime
            data.setContentHidden(MediaServiceData.CONTENT_SHORTS_ALL, true);

            if (kids) {
                BrowsePresenter.instance(context).enableSection(MediaGroup.TYPE_SHORTS, false);
            }
        } else if (forced) {
            for (int flag : FLAGS) {
                data.setContentHidden(flag, prefs.getBoolean(SAVED_PREFIX + flag, false));
            }
            prefs.edit().putBoolean(FORCED, false).apply();
            // The Shorts section itself stays as Edit menu and tabs has it
        }
    }

    /**
     * A Short that reached the player some other way (a link, a cast) doesn't play in a kids profile.
     */
    public static boolean isBlocked(Context context, boolean isShorts) {
        return isShorts && ParentGate.isKidsProfile(context);
    }

    /**
     * A row YouTube titles "Shorts" (search results, Home) while Shorts are hidden: drop it, its videos aren't always
     * marked as Shorts.
     */
    public static boolean isShortsShelf(Context context, String title) {
        if (context == null || title == null || !"shorts".equalsIgnoreCase(title.trim())) {
            return false;
        }

        return ParentGate.isKidsProfile(context) || !isShowShortsEnabled(context);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
