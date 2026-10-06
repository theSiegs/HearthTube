package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
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

    private KidsShorts() {
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

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
