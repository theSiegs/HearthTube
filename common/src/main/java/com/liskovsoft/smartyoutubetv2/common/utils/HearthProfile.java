package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;

/**
 * What the Hearth launcher shares through its content provider: the Google TV profile that's active right now
 * (Google TV has no API for it; Hearth watches its profile chooser), and Hearth's look and settings, so HearthTube
 * can match them. Null when Hearth isn't installed or is too old.
 */
public class HearthProfile {
    private static final String TAG = HearthProfile.class.getSimpleName();
    /**
     * Hearth's app ID; its provider is "{@code <id>.profile"}. A debug HearthTube also talks to Hearth's debug build
     * ("{@code <id>.debug}"), after the release one.
     */
    private static final String[] HEARTH_PACKAGES = com.liskovsoft.smartyoutubetv2.common.BuildConfig.DEBUG
            ? new String[] {"com.thesiegs.hearth", "com.thesiegs.hearth.debug"}
            : new String[] {"com.thesiegs.hearth"};
    /**
     * Hearth's signing certificates (SHA-256): its release key, and the developer key its debug builds share with
     * HearthTube. Anything else answering as Hearth could otherwise say "not a kids profile" or pass any PIN.
     */
    private static final String[] HEARTH_CERTS = {
            "0438047b1a5eefe8693cad8f2b57189a418337bbcbd3c7dbdb79d20884beaf6e",
            "6748528ff4d17fd57c30b6c5d522c467920d9951ea5d208597f91b66df9a2bfe"};
    /** The Hearth install (its ID and last update time) whose certificate checked out, so it's checked once per install */
    private static String sGenuinePackage;
    private static long sGenuineInstall = -1;
    /**
     * Hearth's one-row cursor (also the debug Hearth's, in a debug HearthTube); Hearth notifies it when anything in the
     * row changes (the wallpaper too).
     */
    public static final Uri[] ACTIVE_URIS = activeUris();

    /** The Google TV profile, or null when Hearth couldn't tell. */
    @Nullable
    public final String name;
    /** Hearth's accent color, or null when it's the default or unknown. */
    @Nullable
    public final Integer accentColor;
    /** Hearth's clock format ("h:mm a" by default; ICU pattern), or null when Hearth is too old to tell. */
    @Nullable
    public final String timeFormat;
    /** Hearth's date format ("EEE, MMM d" by default; ICU pattern), or null when Hearth is too old to tell. */
    @Nullable
    public final String dateFormat;
    /** Hearth's language ("de"...), or "" for the system's. Null when Hearth is too old to tell. */
    @Nullable
    public final String appLanguage;
    /** Hearth has a parent PIN that {@link #verifyParentPin} can check */
    public final boolean hasParentPin;
    @Nullable
    public final String gradientUuid;
    /** Changes when Hearth's wallpaper picture does; 0 when it shows a gradient instead. */
    public final long wallpaperStamp;
    /** Hearth sees a Google TV kids profile; null when Hearth is too old to say */
    @Nullable
    public final Boolean kidsProfile;
    /** Google TV's time-up or bedtime screen is on (kids screen time) */
    public final boolean screenTimeUp;
    /**
     * Hearth's accessibility service is running: without it Hearth sees no profile switches and no screen time, so
     * {@link #kidsProfile} and {@link #screenTimeUp} can't be trusted. Null when Hearth is too old to say.
     */
    @Nullable
    public final Boolean serviceRunning;
    /**
     * The Google TV profile's lasting key ("user:11"): known before its {@link #name} and unchanged when the
     * profile is renamed. Null when Hearth can't tell or is too old to say.
     */
    @Nullable
    public final String profileId;
    /**
     * Hearth has finished settling into the active profile after a switch or start (contract 3); until then its
     * profile can be a step behind. Null when Hearth is too old to say.
     */
    @Nullable
    public final Boolean profileReady;
    /** Goes up by one with every profile switch and every Hearth start (contract 3); 0 when Hearth is too old. */
    public final long switchGeneration;
    /**
     * Hearth checks for and silently installs HearthTube updates (updates_hearthtube = 1, contract 4: its automatic
     * companion updates are on and it installed HearthTube), so HearthTube's own updater steps aside. Null before
     * contract 4.
     */
    @Nullable
    public final Boolean updatesHearthTube;
    /** Hearth's provider contract version (docs/provider-contract.md in Hearth); 1 before it was reported. */
    public final int contractVersion;
    /** What Hearth shows (contract 5): "picture", "bing" or "gradient"; null with an older Hearth */
    @Nullable
    public final String wallpaperKind;
    /** Changes whenever what Hearth shows does (contract 5): the cache key */
    public final long wallpaperVersion;
    /** The shown wallpaper's average brightness, 0 (black) .. 1 (white); null until Hearth has measured it */
    @Nullable
    public final Double wallpaperBrightness;
    /** Hearth's gradient as JSON (colors, stops, begin/end or center/radius, rotation, brightness), or null */
    @Nullable
    public final String wallpaperGradient;
    /** Bing's photo title and credit, only when {@link #wallpaperKind} is "bing" */
    @Nullable
    public final String wallpaperTitle;
    @Nullable
    public final String wallpaperCredit;
    /** The newest contract this code was written against: a newer Hearth may mean columns changed meaning. */
    private static final int KNOWN_CONTRACT = 5;
    /** HearthTube was last opened by Hearth (Android 14+, Hearth's share-identity launch); null when unknown */
    private static Boolean sLaunchedFromHearth;
    private static boolean sWarnedNewerContract;

    private HearthProfile(Cursor cursor) {
        String name = getString(cursor, "name");
        this.name = name != null && !name.trim().isEmpty() ? name.trim() : null;
        this.accentColor = parseColor(getString(cursor, "accent_color"));
        this.timeFormat = getString(cursor, "time_format");
        this.dateFormat = getString(cursor, "date_format");
        this.appLanguage = getString(cursor, "app_language");
        this.hasParentPin = getLong(cursor, "has_parent_pin") == 1;
        this.gradientUuid = getString(cursor, "gradient_uuid");
        this.wallpaperStamp = getLong(cursor, "wallpaper_stamp");
        this.kidsProfile = cursor.getColumnIndex("kids_profile") != -1 ? getLong(cursor, "kids_profile") == 1 : null;
        this.screenTimeUp = getLong(cursor, "screen_time_up") == 1;
        this.serviceRunning = cursor.getColumnIndex("service_running") != -1 ? getLong(cursor, "service_running") == 1 : null;
        this.profileId = getString(cursor, "profile_id");
        this.profileReady = cursor.getColumnIndex("profile_ready") != -1 ? getLong(cursor, "profile_ready") == 1 : null;
        this.switchGeneration = getLong(cursor, "switch_generation");
        this.updatesHearthTube = cursor.getColumnIndex("updates_hearthtube") != -1 ? getLong(cursor, "updates_hearthtube") == 1 : null;
        this.contractVersion = cursor.getColumnIndex("contract_version") != -1 ? (int) getLong(cursor, "contract_version") : 1;
        this.wallpaperKind = getString(cursor, "wallpaper_kind");
        this.wallpaperVersion = getLong(cursor, "wallpaper_version");
        int brightness = cursor.getColumnIndex("wallpaper_brightness");
        this.wallpaperBrightness = brightness != -1 && !cursor.isNull(brightness) ? cursor.getDouble(brightness) : null;
        this.wallpaperGradient = getString(cursor, "wallpaper_gradient");
        this.wallpaperTitle = getString(cursor, "wallpaper_title");
        this.wallpaperCredit = getString(cursor, "wallpaper_credit");

        if (contractVersion > KNOWN_CONTRACT && !sWarnedNewerContract) {
            sWarnedNewerContract = true;
            Log.e(TAG, "Hearth's provider contract is version %s; HearthTube knows up to %s", contractVersion, KNOWN_CONTRACT);
        }
    }

    /**
     * Notes who opened a screen (once per onStart). Android 14+ tells, since Hearth launches apps with share-identity
     * options; a screen HearthTube opened itself keeps the earlier answer.
     */
    public static void noteLaunch(android.app.Activity activity) {
        if (activity == null || android.os.Build.VERSION.SDK_INT < 34) {
            return;
        }

        try {
            String from = (String) android.app.Activity.class.getMethod("getLaunchedFromPackage").invoke(activity);
            if (from != null && !from.equals(activity.getPackageName())) {
                sLaunchedFromHearth = isHearthPackage(from);
            }
        } catch (Exception e) {
            // Can't tell: Hearth being the home app decides
        }
    }

    /** Hearth is the TV's home app */
    public static boolean isHearthHome(Context context) {
        try {
            android.content.pm.ResolveInfo home = context.getPackageManager().resolveActivity(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            return home != null && home.activityInfo != null && isHearthPackage(home.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    /** Hearth, release or debug ("<id>.debug"). Exact, since HearthTube's own ID starts like Hearth's. */
    private static boolean isHearthPackage(String packageName) {
        for (String hearth : HEARTH_PACKAGES) {
            if (packageName.equals(hearth) || packageName.equals(hearth + ".debug")) {
                return true;
            }
        }

        return false;
    }

    private static Uri[] activeUris() {
        Uri[] uris = new Uri[HEARTH_PACKAGES.length];
        for (int i = 0; i < uris.length; i++) {
            uris[i] = uri(HEARTH_PACKAGES[i], "active");
        }
        return uris;
    }

    /**
     * The installed Hearth to talk to: the release one first, then (in a debug HearthTube) the debug one; one that
     * Google TV left unsuspended comes before a suspended one. Null when none is installed.
     */
    @Nullable
    private static String hearthPackage(Context context) {
        String suspended = null;

        for (String hearth : HEARTH_PACKAGES) {
            try {
                android.content.pm.ApplicationInfo info = context.getPackageManager().getApplicationInfo(hearth, 0);
                if (android.os.Build.VERSION.SDK_INT < 24 || (info.flags & android.content.pm.ApplicationInfo.FLAG_SUSPENDED) == 0) {
                    return hearth;
                }
                if (suspended == null) {
                    suspended = hearth;
                }
            } catch (Exception e) {
                // Not installed
            }
        }

        return suspended;
    }

    private static Uri uri(String hearthPackage, String path) {
        return Uri.parse("content://" + hearthPackage + ".profile/" + path);
    }

    /** Hearth's current wallpaper picture (no picture = a gradient, see {@link #gradientUuid}) */
    public static Uri wallpaperUri(Context context) {
        String hearth = trustedPackage(context);
        return uri(hearth != null ? hearth : HEARTH_PACKAGES[0], "wallpaper");
    }

    /**
     * HearthTube runs "in Hearth" (wallpaper sync, Hearth's docs/design/wallpaper-sync.md): opened from Hearth, or
     * Hearth is the home app.
     */
    public static boolean isInHearth(Context context) {
        if (Boolean.TRUE.equals(sLaunchedFromHearth)) {
            return true;
        }

        return isHearthHome(context);
    }

    /** What to save per-profile things under: the lasting {@link #profileId}, or the name with an older Hearth. */
    @Nullable
    public String key() {
        return profileId != null ? profileId : name;
    }

    /**
     * The active Google TV profile. Null when Hearth is missing or couldn't tell who's watching (it may know the
     * profile's {@link #profileId} before its name).
     */
    @Nullable
    public static HearthProfile query(Context context) {
        // A Google TV kids profile can suspend Hearth too (when it isn't the home app): it then never sees
        // profile switches, and its last profile is stale. Never follow it.
        if (isHearthSuspended(context)) {
            return null;
        }

        HearthProfile hearth = live(context, queryHearth(context));
        // Mid-switch, the profile may still be the last one: can't tell yet (the next screen asks again)
        return hearth != null && hearth.key() != null && !Boolean.FALSE.equals(hearth.profileReady) ? hearth : null;
    }

    /** Hearth is installed but suspended by Google TV (a kids profile that hasn't approved it) */
    public static boolean isHearthSuspended(Context context) {
        if (context == null || android.os.Build.VERSION.SDK_INT < 24) {
            return false;
        }

        String hearth = hearthPackage(context);

        try {
            return hearth != null && (context.getPackageManager().getApplicationInfo(hearth, 0).flags
                    & android.content.pm.ApplicationInfo.FLAG_SUSPENDED) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Hearth's settings and look, even when it can't tell the profile. Null when Hearth is missing.
     */
    @Nullable
    public static HearthProfile queryHearth(Context context) {
        String hearth = context != null ? trustedPackage(context) : null;
        if (hearth == null) {
            return null;
        }

        try (Cursor cursor = context.getContentResolver().query(uri(hearth, "active"), null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }

            return new HearthProfile(cursor);
        } catch (Exception e) {
            // Hearth missing or without the provider (SecurityException, IllegalArgumentException...)
            Log.d(TAG, "Hearth unavailable: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Hearth's answer, when it can be relied on, else null. In Hearth's own user (the TV's owner) it always can.
     * In a profile user it comes from Hearth's agent there, which repeats Hearth's live row only while connected
     * to Hearth (service_running = 1); otherwise it's a stale copy, as good as no answer.
     */
    @Nullable
    public static HearthProfile live(Context context, @Nullable HearthProfile hearth) {
        if (hearth == null || isOwnerUser(context)) {
            return hearth;
        }

        return Boolean.TRUE.equals(hearth.serviceRunning) ? hearth : null;
    }

    /** The TV's owner (Android user 0), where Hearth itself runs; other Google TV profiles are other users */
    public static boolean isOwnerUser(Context context) {
        if (android.os.Build.VERSION.SDK_INT < 23) {
            return true;
        }

        android.os.UserManager users = (android.os.UserManager) context.getSystemService(Context.USER_SERVICE);
        return users == null || users.isSystemUser();
    }

    /** Result of {@link #verifyParentPin}: {@link #PIN_OK}, {@link #PIN_WRONG}, {@link #PIN_UNAVAILABLE} or seconds to wait. */
    public static final int PIN_OK = 0;
    public static final int PIN_WRONG = -1;
    /** Hearth is missing or too old to check PINs */
    public static final int PIN_UNAVAILABLE = -2;

    /**
     * Checks a PIN against Hearth's parent PIN. Hearth never hands out the PIN, and locks checks for a minute
     * after 5 wrong tries; then this returns the seconds left (a positive number).
     */
    public static int verifyParentPin(Context context, String pin) {
        String hearth = trustedPackage(context);
        if (hearth == null) {
            return PIN_UNAVAILABLE;
        }

        try {
            Bundle result = context.getContentResolver().call(uri(hearth, "active"), "verify_parent_pin", pin, null);

            if (result == null) {
                return PIN_UNAVAILABLE;
            }

            if (result.getBoolean("ok")) {
                return PIN_OK;
            }

            int wait = result.getInt("wait_seconds", 0);
            return wait > 0 ? wait : PIN_WRONG;
        } catch (Exception e) {
            Log.d(TAG, "Hearth PIN check unavailable: %s", e.getMessage());
            return PIN_UNAVAILABLE;
        }
    }

    /** Hearth is installed (and new enough to talk to HearthTube) */
    public static boolean isInstalled(Context context) {
        return context != null && trustedPackage(context) != null;
    }

    /**
     * The Hearth whose provider HearthTube talks to (see {@link #hearthPackage}): its provider belongs to it and it's
     * signed with Hearth's key. Null when there's none.
     */
    @Nullable
    private static String trustedPackage(Context context) {
        String hearth = hearthPackage(context);

        try {
            android.content.pm.ProviderInfo provider = hearth == null ? null
                    : context.getPackageManager().resolveContentProvider(hearth + ".profile", 0);
            return provider != null && hearth.equals(provider.packageName) && isGenuine(context, hearth) == Boolean.TRUE
                    ? hearth : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Whether the installed Hearth is signed with Hearth's key: null when it isn't installed (or can't
     * be read), false for an app that only took Hearth's name.
     */
    @Nullable
    public static Boolean isGenuine(Context context) {
        String hearth = hearthPackage(context);
        return hearth != null ? isGenuine(context, hearth) : null;
    }

    @Nullable
    private static Boolean isGenuine(Context context, String hearth) {
        android.content.pm.PackageManager packageManager = context.getPackageManager();

        try {
            long installed = packageManager.getPackageInfo(hearth, 0).lastUpdateTime;

            if (hearth.equals(sGenuinePackage) && installed == sGenuineInstall) {
                return true;
            }

            android.content.pm.Signature[] signers;
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                android.content.pm.SigningInfo signing = packageManager.getPackageInfo(
                        hearth, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).signingInfo;
                if (signing == null) {
                    return false;
                }
                signers = signing.hasMultipleSigners() ? signing.getApkContentsSigners() : signing.getSigningCertificateHistory();
                if (!signing.hasMultipleSigners() && signers != null && signers.length > 0) {
                    signers = new android.content.pm.Signature[] {signers[signers.length - 1]}; // the current key
                }
            } else {
                signers = packageManager.getPackageInfo(hearth, android.content.pm.PackageManager.GET_SIGNATURES).signatures;
            }

            if (signers == null || signers.length == 0) {
                return false;
            }

            java.security.MessageDigest sha256 = java.security.MessageDigest.getInstance("SHA-256");
            for (android.content.pm.Signature signer : signers) {
                String digest = toHex(sha256.digest(signer.toByteArray()));
                if (!java.util.Arrays.asList(HEARTH_CERTS).contains(digest)) {
                    Log.e(TAG, "Not Hearth's signing key: %s", digest);
                    return false;
                }
            }

            sGenuinePackage = hearth;
            sGenuineInstall = installed;
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Hearth's signature unreadable: %s", e.getMessage());
            return null;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Goes to the launcher, like the Home button, when it's Hearth. False when Hearth isn't installed.
     */
    public static boolean goHome(Context context) {
        if (!isInstalled(context)) {
            return false;
        }

        try {
            context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String getString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index != -1 ? cursor.getString(index) : null;
    }

    private static long getLong(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index != -1 && !cursor.isNull(index) ? cursor.getLong(index) : 0;
    }

    /** "7C4DFF" (Hearth's format) to an opaque color. */
    @Nullable
    static Integer parseColor(String hex) {
        if (hex == null || !hex.matches("[0-9A-Fa-f]{6}")) {
            return null;
        }

        return Color.parseColor("#" + hex);
    }
}
