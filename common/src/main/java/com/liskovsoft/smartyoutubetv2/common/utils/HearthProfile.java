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
    private static final String HEARTH_PACKAGE = "com.leanbitlab.ltvL";
    private static final String PROVIDER = HEARTH_PACKAGE + ".profile";
    private static final String AUTHORITY = "content://" + PROVIDER;
    /**
     * Hearth's signing certificates (SHA-256): its release key, and the developer key its debug builds share with
     * HearthTube. Anything else answering as Hearth could otherwise say "not a kids profile" or pass any PIN.
     */
    private static final String[] HEARTH_CERTS = {
            "0438047b1a5eefe8693cad8f2b57189a418337bbcbd3c7dbdb79d20884beaf6e",
            "6748528ff4d17fd57c30b6c5d522c467920d9951ea5d208597f91b66df9a2bfe"};
    /** The Hearth install (its last update time) whose certificate checked out, so it's checked once per install */
    private static long sGenuineInstall = -1;
    private static final Uri ACTIVE_URI = Uri.parse(AUTHORITY + "/active");
    /** Hearth's current wallpaper picture (no picture = a gradient, see {@link #gradientUuid}) */
    public static final Uri WALLPAPER_URI = Uri.parse(AUTHORITY + "/wallpaper");

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
    /** Hearth's provider contract version (docs/provider-contract.md in Hearth); 1 before it was reported. */
    public final int contractVersion;
    /** The newest contract this code was written against: a newer Hearth may mean columns changed meaning. */
    private static final int KNOWN_CONTRACT = 2;
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
        this.contractVersion = cursor.getColumnIndex("contract_version") != -1 ? (int) getLong(cursor, "contract_version") : 1;

        if (contractVersion > KNOWN_CONTRACT && !sWarnedNewerContract) {
            sWarnedNewerContract = true;
            Log.e(TAG, "Hearth's provider contract is version %s; HearthTube knows up to %s", contractVersion, KNOWN_CONTRACT);
        }
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

        HearthProfile hearth = queryHearth(context);
        return hearth != null && hearth.key() != null ? hearth : null;
    }

    /** Hearth is installed but suspended by Google TV (a kids profile that hasn't approved it) */
    public static boolean isHearthSuspended(Context context) {
        if (context == null || android.os.Build.VERSION.SDK_INT < 24) {
            return false;
        }

        try {
            android.content.pm.ApplicationInfo info = context.getPackageManager().getApplicationInfo("com.leanbitlab.ltvL", 0);
            return (info.flags & android.content.pm.ApplicationInfo.FLAG_SUSPENDED) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Hearth's settings and look, even when it can't tell the profile. Null when Hearth is missing.
     */
    @Nullable
    public static HearthProfile queryHearth(Context context) {
        if (context == null || !isTrusted(context)) {
            return null;
        }

        try (Cursor cursor = context.getContentResolver().query(ACTIVE_URI, null, null, null, null)) {
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
        if (!isTrusted(context)) {
            return PIN_UNAVAILABLE;
        }

        try {
            Bundle result = context.getContentResolver().call(ACTIVE_URI, "verify_parent_pin", pin, null);

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
        return isTrusted(context);
    }

    /** The provider HearthTube talks to belongs to Hearth, signed with Hearth's key */
    private static boolean isTrusted(Context context) {
        if (context == null) {
            return false;
        }

        try {
            android.content.pm.ProviderInfo provider = context.getPackageManager().resolveContentProvider(PROVIDER, 0);
            return provider != null && HEARTH_PACKAGE.equals(provider.packageName) && isGenuine(context) == Boolean.TRUE;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Whether the installed com.leanbitlab.ltvL is signed with Hearth's key: null when it isn't installed (or can't
     * be read), false for an app that only took Hearth's name.
     */
    @Nullable
    public static Boolean isGenuine(Context context) {
        android.content.pm.PackageManager packageManager = context.getPackageManager();

        try {
            long installed = packageManager.getPackageInfo(HEARTH_PACKAGE, 0).lastUpdateTime;

            if (installed == sGenuineInstall) {
                return true;
            }

            android.content.pm.Signature[] signers;
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                android.content.pm.SigningInfo signing = packageManager.getPackageInfo(
                        HEARTH_PACKAGE, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).signingInfo;
                if (signing == null) {
                    return false;
                }
                signers = signing.hasMultipleSigners() ? signing.getApkContentsSigners() : signing.getSigningCertificateHistory();
                if (!signing.hasMultipleSigners() && signers != null && signers.length > 0) {
                    signers = new android.content.pm.Signature[] {signers[signers.length - 1]}; // the current key
                }
            } else {
                signers = packageManager.getPackageInfo(HEARTH_PACKAGE, android.content.pm.PackageManager.GET_SIGNATURES).signatures;
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
