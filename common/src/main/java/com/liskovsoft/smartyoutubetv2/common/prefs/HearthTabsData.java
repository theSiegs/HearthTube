package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.HearthSections;

import java.util.ArrayList;
import java.util.List;

/**
 * Which sections are tabs in the pill at the top. Every other section that's switched on (SidebarService) goes
 * in the side menu, except the queue (its own "Queued (n)" tab) and notifications (the bell).
 */
public class HearthTabsData {
    private static final String PREFS_NAME = "hearth_tabs";
    private static final String TABS = "tabs";
    /** Kinds of video. Library, Watch later and the like are places, so they go in the side menu */
    private static final int[] DEFAULT_TABS = {
            MediaGroup.TYPE_HOME, MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_MUSIC, HearthSections.TYPE_AMBIANCE
    };
    /** Switched on in a new profile; everything else starts off (and can be switched on in Edit menu and tabs) */
    private static final int[] DEFAULT_ON = {
            MediaGroup.TYPE_HOME, MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_MUSIC,
            HearthSections.TYPE_AMBIANCE, HearthSections.TYPE_LIBRARY,
            MediaGroup.TYPE_SPORTS, MediaGroup.TYPE_LIVE, MediaGroup.TYPE_NEWS,
            HearthSections.TYPE_WATCH_LATER, HearthSections.TYPE_PODCASTS,
            MediaGroup.TYPE_NOTIFICATIONS, MediaGroup.TYPE_PLAYBACK_QUEUE
    };
    @SuppressLint("StaticFieldLeak")
    private static HearthTabsData sInstance;
    private final SharedPreferences mPrefs;

    private HearthTabsData(Context context) {
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static HearthTabsData instance(Context context) {
        if (sInstance == null) {
            sInstance = new HearthTabsData(context.getApplicationContext());
        }

        return sInstance;
    }

    public static boolean isOnByDefault(int sectionId) {
        for (int id : DEFAULT_ON) {
            if (id == sectionId) {
                return true;
            }
        }

        return false;
    }

    /** Never in the menu or the pill: they have their own buttons */
    public static boolean hasOwnButton(int sectionId) {
        return sectionId == MediaGroup.TYPE_PLAYBACK_QUEUE || sectionId == MediaGroup.TYPE_NOTIFICATIONS
                || sectionId == MediaGroup.TYPE_SETTINGS;
    }

    /** Tab section ids, in order */
    public List<Integer> getTabs() {
        List<Integer> tabs = new ArrayList<>();
        String saved = mPrefs.getString(TABS, null);

        if (saved == null) {
            for (int id : DEFAULT_TABS) {
                tabs.add(id);
            }
            return tabs;
        }

        for (String id : saved.split(",")) {
            try {
                tabs.add(Integer.parseInt(id.trim()));
            } catch (NumberFormatException e) {
                // skip
            }
        }

        return tabs;
    }

    public boolean isTab(int sectionId) {
        return getTabs().contains(sectionId);
    }

    public void setTab(int sectionId, boolean isTab) {
        List<Integer> tabs = getTabs();
        tabs.remove(Integer.valueOf(sectionId));

        if (isTab) {
            tabs.add(sectionId);
        }

        StringBuilder saved = new StringBuilder();
        for (int id : tabs) {
            if (saved.length() > 0) {
                saved.append(',');
            }
            saved.append(id);
        }

        mPrefs.edit().putString(TABS, saved.toString()).apply();
    }
}
