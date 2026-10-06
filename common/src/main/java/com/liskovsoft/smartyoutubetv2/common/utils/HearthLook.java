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
 * launch or return to the app rather than on every clock tick.
 */
public final class HearthLook {
    @Nullable
    private static String sTimeFormat;

    private HearthLook() {
    }

    /**
     * Call when the app comes to the front: Hearth's settings may have changed meanwhile.
     */
    public static void refresh(Context context) {
        HearthProfile hearth = HearthLinkData.instance(context).isMatchClockAndLanguageEnabled() ?
                HearthProfile.queryHearth(context) : null;

        sTimeFormat = hearth != null ? hearth.timeFormat : null;

        if (hearth != null && hearth.appLanguage != null) {
            LocaleUpdater locale = new LocaleUpdater(context);

            // "" is the system's language in both apps. Takes effect the next time HearthTube starts.
            if (!hearth.appLanguage.equals(locale.getPreferredLanguage())) {
                locale.setPreferredLanguage(hearth.appLanguage);
            }
        }
    }

    /**
     * The time in Hearth's format ("HH:mm", "h:mm a"...), or null to use HearthTube's own.
     */
    @Nullable
    public static String formatTime(Date date) {
        String format = sTimeFormat;

        if (format == null || format.isEmpty()) {
            return null;
        }

        try {
            // Hearth's formats are Dart DateFormat patterns, which share these letters with Java's
            return new SimpleDateFormat(format, Locale.getDefault()).format(date);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
