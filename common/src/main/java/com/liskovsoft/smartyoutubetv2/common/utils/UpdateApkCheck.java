package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;

import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

/**
 * HearthTube: a downloaded update is installed only when it is this app, newer, and signed with this app's key (or a
 * key rotated from it, as {@code release.ps1} signs). The update feed and the APK come from the same place, so a
 * checksum there wouldn't tell more; the signature is what only a real HearthTube release has. Android checks the same
 * at install, but this refuses before the installer comes up, with a clear message, and never starts it for a stranger.
 */
public final class UpdateApkCheck {
    private static final String TAG = UpdateApkCheck.class.getSimpleName();

    private UpdateApkCheck() {
    }

    public static boolean isOurs(Context context, @Nullable String apkPath) {
        if (context == null || apkPath == null) {
            return false;
        }

        if (android.os.Build.VERSION.SDK_INT < 28) {
            return true; // no key history to read before Android 9: Android's own same-key check at install stands
        }

        return isOursApi28(context, apkPath);
    }

    @androidx.annotation.RequiresApi(28)
    private static boolean isOursApi28(Context context, String apkPath) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo update = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNING_CERTIFICATES);
            PackageInfo installed = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);

            if (update == null || update.signingInfo == null || installed.signingInfo == null) {
                Log.e(TAG, "Update unreadable: %s", apkPath);
                return false;
            }

            if (!context.getPackageName().equals(update.packageName)) {
                Log.e(TAG, "Update is another app: %s", update.packageName);
                return false;
            }

            if (update.getLongVersionCode() <= installed.getLongVersionCode()) {
                Log.e(TAG, "Update isn't newer: %s", update.getLongVersionCode());
                return false;
            }

            // The update's key history must hold the key this install is signed with now
            Set<String> updateKeys = digests(update.signingInfo);
            Set<String> ourKey = current(installed.signingInfo);

            if (ourKey.isEmpty() || !updateKeys.containsAll(ourKey)) {
                Log.e(TAG, "Update isn't signed with HearthTube's key");
                return false;
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Update check failed: %s", e.getMessage());
            return false;
        }
    }

    @androidx.annotation.RequiresApi(28)
    private static Set<String> digests(SigningInfo signing) throws Exception {
        Set<String> result = new HashSet<>();
        Signature[] signers = signing.hasMultipleSigners() ? signing.getApkContentsSigners() : signing.getSigningCertificateHistory();

        for (Signature signer : signers) {
            result.add(sha256(signer));
        }

        return result;
    }

    @androidx.annotation.RequiresApi(28)
    private static Set<String> current(SigningInfo signing) throws Exception {
        if (signing.hasMultipleSigners()) {
            return digests(signing);
        }

        Signature[] history = signing.getSigningCertificateHistory();
        Set<String> result = new HashSet<>();

        if (history != null && history.length > 0) {
            result.add(sha256(history[history.length - 1]));
        }

        return result;
    }

    private static String sha256(Signature signature) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
        StringBuilder sb = new StringBuilder();

        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }
}
