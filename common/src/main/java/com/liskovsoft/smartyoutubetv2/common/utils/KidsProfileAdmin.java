package com.liskovsoft.smartyoutubetv2.common.utils;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;

/**
 * HearthTube's device admin (HearthTubeDeviceAdmin, hearthtube builds only). It asks for no policies: it's there
 * because Android won't suspend an active device admin, so Google TV can't hide HearthTube from kids profiles,
 * which can't approve it (Google TV only offers Play Store apps). Google TV's bedtime and limits can't pause us
 * either then, so HearthTube follows Hearth's screen time itself.
 */
public final class KidsProfileAdmin {
    private static final String RECEIVER = "com.liskovsoft.smartyoutubetv2.tv.HearthTubeDeviceAdmin";
    private static final String HEARTH_PACKAGE = "com.leanbitlab.ltvL";

    private KidsProfileAdmin() {
    }

    /** Whether this build has the admin (only hearthtube builds do). */
    public static boolean isAvailable(Context context) {
        try {
            context.getPackageManager().getReceiverInfo(component(context), 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isActive(Context context) {
        DevicePolicyManager dpm = dpm(context);
        return dpm != null && dpm.isAdminActive(component(context));
    }

    /**
     * Whether Hearth can stand in for Google TV's screen time: it must be the home app (Google TV can't suspend
     * that, so it keeps seeing profile switches and time-up screens) and new enough to report kids_profile and
     * screen_time_up. Without it, nothing would stop HearthTube at bedtime once Google TV can't pause it.
     */
    public static boolean canGuardScreenTime(Context context) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        android.content.pm.ResolveInfo info = context.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        boolean hearthIsHome = info != null && info.activityInfo != null
                && HEARTH_PACKAGE.equals(info.activityInfo.packageName);
        HearthProfile hearth = HearthProfile.queryHearth(context);
        return hearthIsHome && hearth != null && hearth.kidsProfile != null;
    }

    /** Opens Android's own "Activate device admin" confirmation, if Hearth can guard screen time. */
    public static void requestActive(Context context) {
        if (!canGuardScreenTime(context)) {
            MessageHelpers.showLongMessage(context, R.string.hearth_kids_profiles_needs_hearth);
            return;
        }

        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.hearth_kids_profiles_explanation));
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            MessageHelpers.showLongMessage(context, R.string.hearth_kids_profiles_unavailable);
        }
    }

    /** Gives up the admin: Google TV hides HearthTube from kids profiles again, and HearthTube can be uninstalled. */
    public static void removeActive(Context context) {
        DevicePolicyManager dpm = dpm(context);
        if (dpm != null && dpm.isAdminActive(component(context))) {
            dpm.removeActiveAdmin(component(context));
        }
    }

    private static ComponentName component(Context context) {
        return new ComponentName(context.getPackageName(), RECEIVER);
    }

    private static DevicePolicyManager dpm(Context context) {
        return (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }
}
