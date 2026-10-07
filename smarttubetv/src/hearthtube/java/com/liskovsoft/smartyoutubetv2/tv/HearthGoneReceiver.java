package com.liskovsoft.smartyoutubetv2.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.liskovsoft.smartyoutubetv2.common.utils.KidsProfileAdmin;

/**
 * An app was uninstalled (PACKAGE_FULLY_REMOVED still reaches manifest receivers on Android 8+): when it was Hearth,
 * HearthTube gives up its device admin, so it can be uninstalled too and kids profiles don't show "no Hearth".
 */
public class HearthGoneReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getData() != null && "com.leanbitlab.ltvL".equals(intent.getData().getSchemeSpecificPart())) {
            KidsProfileAdmin.dropIfHearthGone(context);
        }
    }
}
