package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hearth announces every change on its provider, about once a minute even when nothing a viewer sees changed (it
 * stamps the allowance check time). Several parts of HearthTube react to it (the allowance in the player, the
 * time's-up check, the rows' shade, the background, the parent's lists), and each used to ask Hearth again on the main
 * thread, with a few package lookups each time. Here one observer hears Hearth, reads it once off the main thread
 * ({@link HearthProfile.Snapshot}), then runs every listener on the main thread with that answer in place: their
 * {@link HearthProfile#queryHearth} and {@link HearthProfile#isHearthSuspended} take it instead of asking again.
 * Announcements that come while a read is under way make one more read, not one each.
 */
public final class HearthChanges {
    private static final CopyOnWriteArrayList<Runnable> sListeners = new CopyOnWriteArrayList<>();
    private static final ExecutorService sReader = Executors.newSingleThreadExecutor();
    private static final Handler sMain = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean sReading = new AtomicBoolean();
    private static final AtomicBoolean sAgain = new AtomicBoolean();
    private static Context sApp;
    private static ContentObserver sObserver;

    private HearthChanges() {
    }

    /** Runs {@code listener} on the main thread after each change Hearth announces, until {@link #remove} */
    public static synchronized void add(Context context, Runnable listener) {
        if (context == null || listener == null || sListeners.contains(listener)) {
            return;
        }

        sListeners.add(listener);

        if (sObserver != null) {
            return;
        }

        sApp = context.getApplicationContext();
        sObserver = new ContentObserver(sMain) {
            @Override
            public void onChange(boolean selfChange) {
                read();
            }
        };

        for (Uri hearth : HearthProfile.ACTIVE_URIS) {
            try {
                sApp.getContentResolver().registerContentObserver(hearth, false, sObserver);
            } catch (Exception e) {
                // No Hearth (or too old): each part's own timer still checks
            }
        }
    }

    public static synchronized void remove(Runnable listener) {
        sListeners.remove(listener);

        if (sListeners.isEmpty() && sObserver != null) {
            sApp.getContentResolver().unregisterContentObserver(sObserver);
            sObserver = null;
        }
    }

    private static void read() {
        if (!sReading.compareAndSet(false, true)) {
            sAgain.set(true); // a change after the read began: read once more when it ends
            return;
        }

        Context app = sApp;

        sReader.execute(() -> {
            HearthProfile.Snapshot snapshot = null;

            try {
                snapshot = app != null ? HearthProfile.Snapshot.read(app) : null;
            } finally {
                HearthProfile.Snapshot answer = snapshot;
                sMain.post(() -> {
                    dispatch(answer);
                    sReading.set(false);

                    if (sAgain.getAndSet(false)) {
                        read();
                    }
                });
            }
        });
    }

    private static void dispatch(HearthProfile.Snapshot snapshot) {
        HearthProfile.Snapshot.sCurrent = snapshot;

        try {
            for (Runnable listener : sListeners) {
                try {
                    listener.run();
                } catch (RuntimeException e) {
                    // One part failing mustn't keep the change from the others
                    com.liskovsoft.sharedutils.mylogger.Log.e("HearthChanges", "Listener failed: %s", e.getMessage());
                }
            }
        } finally {
            HearthProfile.Snapshot.sCurrent = null;
        }
    }
}
