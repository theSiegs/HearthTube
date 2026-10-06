package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

/**
 * No Shorts in Google TV kids profiles, ever: while a kids profile is active, every "hide Shorts" option is forced
 * on and the Shorts section is gone. The adult's own choices are saved first and put back on the next launch in an
 * adult profile. Settings are parent-locked in kids profiles, so a kid can't switch them back on.
 */
public final class KidsShorts {
    private static final String PREFS = "hearthtube_kids";
    private static final String FORCED = "shorts_forced";
    private static final String SAVED_PREFIX = "shorts_hidden_";
    /** Every Shorts flag, saved one by one: an adult may have hidden Shorts in some places only */
    private static final int[] FLAGS = {
            MediaServiceData.CONTENT_SHORTS_HOME, MediaServiceData.CONTENT_SHORTS_SEARCH, MediaServiceData.CONTENT_SHORTS_SUBSCRIPTIONS,
            MediaServiceData.CONTENT_SHORTS_HISTORY, MediaServiceData.CONTENT_SHORTS_TRENDING, MediaServiceData.CONTENT_SHORTS_CHANNEL,
            MediaServiceData.CONTENT_SHORTS_NEWS
    };

    private KidsShorts() {
    }

    /**
     * Call on every launch (profile switches always go through the launcher).
     */
    public static void apply(Context context) {
        if (context == null) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        MediaServiceData data = MediaServiceData.instance();
        boolean forced = prefs.getBoolean(FORCED, false);
        boolean kids = ParentGate.isKidsProfile(context);

        if (kids && !forced) {
            SharedPreferences.Editor editor = prefs.edit();
            for (int flag : FLAGS) {
                editor.putBoolean(SAVED_PREFIX + flag, data.isContentHidden(flag));
            }
            editor.putBoolean(FORCED, true).apply();

            data.setContentHidden(MediaServiceData.CONTENT_SHORTS_ALL, true);
            BrowsePresenter.instance(context).enableSection(MediaGroup.TYPE_SHORTS, false);
        } else if (kids) {
            // Already forced; make sure nothing switched it back in the meantime
            data.setContentHidden(MediaServiceData.CONTENT_SHORTS_ALL, true);
            BrowsePresenter.instance(context).enableSection(MediaGroup.TYPE_SHORTS, false);
        } else if (forced) {
            for (int flag : FLAGS) {
                data.setContentHidden(flag, prefs.getBoolean(SAVED_PREFIX + flag, false));
            }
            prefs.edit().putBoolean(FORCED, false).apply();

            // The Shorts section goes with "hide Shorts everywhere" (GeneralSettingsPresenter)
            BrowsePresenter.instance(context).enableSection(MediaGroup.TYPE_SHORTS,
                    !data.isContentHidden(MediaServiceData.CONTENT_SHORTS_ALL));
        }
    }

    /**
     * A Short that reached the player some other way (a link, a cast) doesn't play in a kids profile.
     */
    public static boolean isBlocked(Context context, boolean isShorts) {
        return isShorts && ParentGate.isKidsProfile(context);
    }
}
