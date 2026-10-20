package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

import androidx.tvprovider.media.tv.TvContractCompat;

import com.liskovsoft.sharedutils.mylogger.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes the Android TV home channels (Subscriptions, History, Recommended) that earlier builds published. The app
 * doesn't publish channels any more, so the rows would otherwise stay on the launcher's home screen, stale, for good.
 * Watch Next (Continue Watching) is a different list and stays (WatchNextPublisher). Runs once.
 */
public final class HomeChannelsCleanup {
    private static final String TAG = HomeChannelsCleanup.class.getSimpleName();
    private static final String PREFS_NAME = "home_channels_cleanup";
    private static final String DONE = "done";

    private HomeChannelsCleanup() {
    }

    /** Call off the main thread: it reads and deletes through the TV provider */
    public static void runOnce(Context context) {
        if (context == null) {
            return;
        }

        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (prefs.getBoolean(DONE, false)) {
            return;
        }

        try {
            ContentResolver resolver = appContext.getContentResolver();
            List<Long> ids = new ArrayList<>();

            // A query only returns this app's own channels
            try (Cursor cursor = resolver.query(TvContractCompat.Channels.CONTENT_URI,
                    new String[] {TvContractCompat.Channels._ID}, null, null, null)) {
                while (cursor != null && cursor.moveToNext()) {
                    ids.add(cursor.getLong(0));
                }
            }

            for (long id : ids) {
                resolver.delete(TvContractCompat.buildChannelUri(id), null, null);
            }

            Log.d(TAG, "Removed %s home channels", ids.size());
            prefs.edit().putBoolean(DONE, true).apply();
        } catch (Exception e) { // no TV provider (a phone), or access refused: nothing to clean
            Log.e(TAG, "Can't remove the home channels: %s", e.getMessage());
            prefs.edit().putBoolean(DONE, true).apply();
        }
    }
}
