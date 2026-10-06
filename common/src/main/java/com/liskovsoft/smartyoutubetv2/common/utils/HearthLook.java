package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.locale.LocaleUpdater;
import com.liskovsoft.smartyoutubetv2.common.prefs.HearthLinkData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Hearth's settings that HearthTube copies (Works with Hearth > Match Hearth's clock and language), read once per
 * launch or return to the app rather than on every clock tick. The clock always looks like Hearth's: its formats, or
 * Hearth's own US defaults when Hearth isn't there (or the match is switched off).
 */
public final class HearthLook {
    /** Hearth's own defaults (US): used when Hearth isn't there or doesn't say */
    public static final String DEFAULT_DATE_FORMAT = "EEE, MMM d";
    public static final String DEFAULT_TIME_FORMAT = "h:mm a";
    @Nullable
    private static String sTimeFormat;
    @Nullable
    private static String sDateFormat;

    private HearthLook() {
    }

    /**
     * Call when the app comes to the front: Hearth's settings may have changed meanwhile.
     */
    public static void refresh(Context context) {
        HearthProfile hearth = HearthLinkData.instance(context).isMatchClockAndLanguageEnabled() ?
                HearthProfile.queryHearth(context) : null;

        sTimeFormat = hearth != null ? hearth.timeFormat : null;
        sDateFormat = hearth != null ? hearth.dateFormat : null;

        if (hearth != null && hearth.appLanguage != null) {
            LocaleUpdater locale = new LocaleUpdater(context);

            // "" is the system's language in both apps. Takes effect the next time HearthTube starts.
            if (!hearth.appLanguage.equals(locale.getPreferredLanguage())) {
                locale.setPreferredLanguage(hearth.appLanguage);
            }
        }
    }

    /**
     * The time in Hearth's format, "2:20 PM" by default.
     */
    public static String formatTime(Date date) {
        return format(sTimeFormat, DEFAULT_TIME_FORMAT, date);
    }

    /**
     * The date in Hearth's format, "Tue, Oct 6" by default.
     */
    public static String formatDate(Date date) {
        return format(sDateFormat, DEFAULT_DATE_FORMAT, date);
    }

    private static String format(@Nullable String format, String fallback, Date date) {
        if (format != null && !format.isEmpty()) {
            try {
                // Hearth's formats are ICU (Dart intl) patterns, which share these letters with Java's
                return new SimpleDateFormat(format, Locale.getDefault()).format(date);
            } catch (IllegalArgumentException e) {
                // fall through to Hearth's default
            }
        }

        return new SimpleDateFormat(fallback, Locale.getDefault()).format(date);
    }
}
