package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.content.SharedPreferences;

/** HearthTube: keeps {@link CastApprovals} for this TV (per Android user, so per Google TV profile) */
public final class CastApprovalsStore {
    private static final String PREFS = "hearthtube_cast_approvals";
    private static final String KEY = "approved";
    private static CastApprovals sApprovals;

    private CastApprovalsStore() {
    }

    public static synchronized CastApprovals get(Context context) {
        if (sApprovals == null) {
            sApprovals = new CastApprovals();
            sApprovals.restore(prefs(context).getString(KEY, null));
        }

        return sApprovals;
    }

    public static synchronized void save(Context context) {
        prefs(context).edit().putString(KEY, get(context).save()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
