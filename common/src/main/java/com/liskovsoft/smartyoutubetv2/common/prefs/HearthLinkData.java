package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Settings > Works with Hearth: how closely HearthTube follows the Hearth launcher. Following the Google TV
 * profile lives in {@link ProfileLinkData}. Everything is on by default and does nothing without Hearth.
 */
public class HearthLinkData {
    private static final String PREFS_NAME = "hearth_link";
    private static final String HEARTH_PARENT_PIN = "use_hearth_parent_pin";
    private static final String CONTINUE_WATCHING = "continue_watching";
    private static final String MATCH_CLOCK_LANGUAGE = "match_clock_language";
    private static final String SCREENSAVER_WHEN_PAUSED = "screensaver_when_paused";
    private static final String BACK_TO_HEARTH = "back_to_hearth";
    private static final String HOME_ASSISTANT = "home_assistant";
    @SuppressLint("StaticFieldLeak")
    private static HearthLinkData sInstance;
    private final SharedPreferences mPrefs;

    private HearthLinkData(Context context) {
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static HearthLinkData instance(Context context) {
        if (sInstance == null) {
            sInstance = new HearthLinkData(context.getApplicationContext());
        }

        return sInstance;
    }

    /** Kids lock asks for Hearth's parent PIN instead of HearthTube's own */
    public boolean isHearthParentPinEnabled() {
        return mPrefs.getBoolean(HEARTH_PARENT_PIN, true);
    }

    public void setHearthParentPinEnabled(boolean enabled) {
        put(HEARTH_PARENT_PIN, enabled);
    }

    /** Unfinished videos show in Hearth's Continue Watching row (Watch Next) */
    public boolean isContinueWatchingEnabled() {
        return mPrefs.getBoolean(CONTINUE_WATCHING, true);
    }

    public void setContinueWatchingEnabled(boolean enabled) {
        put(CONTINUE_WATCHING, enabled);
    }

    /** The clock pill uses Hearth's time format, and the app Hearth's language */
    public boolean isMatchClockAndLanguageEnabled() {
        return mPrefs.getBoolean(MATCH_CLOCK_LANGUAGE, true);
    }

    public void setMatchClockAndLanguageEnabled(boolean enabled) {
        put(MATCH_CLOCK_LANGUAGE, enabled);
    }

    /** A paused video lets the TV's screensaver (Hearth's clock) start instead of keeping the screen on */
    public boolean isScreensaverWhenPausedEnabled() {
        return mPrefs.getBoolean(SCREENSAVER_WHEN_PAUSED, true);
    }

    public void setScreensaverWhenPausedEnabled(boolean enabled) {
        put(SCREENSAVER_WHEN_PAUSED, enabled);
    }

    /** Back on the Home tab goes to Hearth instead of asking whether to exit */
    public boolean isBackToHearthEnabled() {
        return mPrefs.getBoolean(BACK_TO_HEARTH, true);
    }

    public void setBackToHearthEnabled(boolean enabled) {
        put(BACK_TO_HEARTH, enabled);
    }

    /** Video titles reach Home Assistant (through Hearth, which reports what every app plays) */
    public boolean isHomeAssistantEnabled() {
        return mPrefs.getBoolean(HOME_ASSISTANT, true);
    }

    public void setHomeAssistantEnabled(boolean enabled) {
        put(HOME_ASSISTANT, enabled);
    }

    private void put(String key, boolean value) {
        mPrefs.edit().putBoolean(key, value).apply();
    }
}
