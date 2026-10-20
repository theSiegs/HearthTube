package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Settings Look/General/Player no longer offer (the settings cleanup) but the app still obeys when an earlier build
 * had them on: with no switch left, nobody could turn them off. This puts them back to their defaults, once.
 * <p>
 * Reset: the settings password, the master password and Child mode (a password nobody can see or clear would lock
 * the family out), the web proxy, Conscrypt, the Google Drive backup schedule, "prefer Google/IPv4 DNS" (network behavior the privacy notes don't
 * describe), and the key remaps. Not reset: the device workarounds (an old TV may need one) and the player quirks.
 */
public final class RemovedSettingsReset {
    private static final String PREFS_NAME = "hearthtube_migrations";
    private static final String DONE = "removed_settings_reset";

    private RemovedSettingsReset() {
    }

    public static void runOnce(Context context) {
        if (context == null) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (prefs.getBoolean(DONE, false)) {
            return;
        }

        prefs.edit().putBoolean(DONE, true).apply();

        GeneralData general = GeneralData.instance(context);
        general.setSettingsPassword(null);
        general.setMasterPassword(null);
        general.setChildModeEnabled(false);
        general.setProxyEnabled(false);
        general.setOkButtonLongPressDisabled(false);
        general.setRemapNumbersToSpeedEnabled(false);
        general.setRemapDpadLeftToVolumeEnabled(false);
        general.setRemapPlayToOKEnabled(false);
        general.setRemapSToSpeedToggleEnabled(false);
        general.setDpadUpDownAction(GeneralData.ACTION_UNDEFINED);
        general.setNextPreviousAction(GeneralData.ACTION_UNDEFINED);
        general.setFastForwardRewindAction(GeneralData.ACTION_UNDEFINED);
        general.setPageUpDownAction(GeneralData.ACTION_UNDEFINED);
        general.setChannelUpDownAction(GeneralData.ACTION_UNDEFINED);

        // Google Drive backup is gone: stop a daily upload an earlier build had scheduled
        general.setGDriveBackupFreqDays(0);
        com.liskovsoft.smartyoutubetv2.common.utils.SafeWorkManager.getInstance(context).cancelUniqueWork("GDriveBackupWorker");

        NetworkData.instance(context).setConscryptEnabled(false);
        PlayerTweaksData.instance(context).setPreferredDnsType(PlayerTweaksData.DNS_TYPE_SYSTEM);
    }
}
