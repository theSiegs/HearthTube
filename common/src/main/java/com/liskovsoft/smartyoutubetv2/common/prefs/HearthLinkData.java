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
    private static final String CONTINUE_WATCHING = "continue_watching";
    private static final String MATCH_CLOCK_LANGUAGE = "match_clock_language";
    private static final String SCREENSAVER_WHEN_PAUSED = "screensaver_when_paused";
    private static final String BACK_TO_HEARTH = "back_to_hearth";
    private static final String HOME_ASSISTANT = "home_assistant";
    /** Hearth's last answer to "share what's playing" that was applied (applyHearthAnswer) */
    private static final String HOME_ASSISTANT_HEARTH_ANSWER = "home_assistant_hearth_answer";
    private static final String ACCENT = "accent_color";
    private static final String WALLPAPER = "wallpaper";
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

    /**
     * Hearth asks during its Home Assistant setup whether HearthTube may share what's playing (and has a switch for it).
     * Its answer sets this one whenever it changes; a change made here stands until then. Never asked: on.
     */
    public void applyHearthAnswer(@androidx.annotation.Nullable Integer answer) {
        Integer applied = mPrefs.contains(HOME_ASSISTANT_HEARTH_ANSWER) ? mPrefs.getInt(HOME_ASSISTANT_HEARTH_ANSWER, -1) : null;
        Boolean enable = hearthAnswerToApply(answer, applied);

        if (enable != null) {
            mPrefs.edit().putBoolean(HOME_ASSISTANT, enable).putInt(HOME_ASSISTANT_HEARTH_ANSWER, answer).apply();
        }
    }

    /** The switch's new state for Hearth's answer, or null to leave it: no answer, or the one already applied */
    static Boolean hearthAnswerToApply(@androidx.annotation.Nullable Integer answer, @androidx.annotation.Nullable Integer applied) {
        if (answer == null || answer.equals(applied)) {
            return null;
        }

        return answer != 0;
    }

    /** Look and layout > Accent color: a color, or null to match Hearth (the default) */
    @androidx.annotation.Nullable
    public Integer getAccentColor() {
        return mPrefs.contains(ACCENT) ? mPrefs.getInt(ACCENT, 0) : null;
    }

    public void setAccentColor(@androidx.annotation.Nullable Integer color) {
        if (color == null) {
            mPrefs.edit().remove(ACCENT).apply();
        } else {
            mPrefs.edit().putInt(ACCENT, color).apply();
        }
    }

    /** Look and layout > Wallpaper: one of HearthWallpaper's choices; Match Hearth by default */
    public String getWallpaper() {
        return mPrefs.getString(WALLPAPER, com.liskovsoft.smartyoutubetv2.common.utils.HearthWallpaper.MATCH_HEARTH);
    }

    public void setWallpaper(String wallpaper) {
        mPrefs.edit().putString(WALLPAPER, wallpaper).apply();
    }

    private void put(String key, boolean value) {
        mPrefs.edit().putBoolean(key, value).apply();
    }
}
