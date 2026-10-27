package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.prefs.ParentBlockData;
import com.liskovsoft.smartyoutubetv2.common.prefs.WatchLogData;
import com.liskovsoft.smartyoutubetv2.common.prefs.WatchLogData.Entry;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HearthTube's side of the parent channel (docs/parent-channel.md): in a kids profile, the videos it started go to Hearth
 * (provider contract 8, {@code report_started}) for the parent to read, and the parent's blocked videos, channels and
 * words come back ({@code parent_blocks}) as {@link ParentBlockData}. Hearth passes both on to Home Assistant when that
 * is set up; HearthTube only talks to Hearth, and works the same without either. The kid's own log stays the record:
 * what Hearth didn't take (not there, not connected) is sent again the next time.
 */
public final class ParentChannel {
    private static final String TAG = ParentChannel.class.getSimpleName();
    /** Entries sent in one go; the rest follow the next time */
    private static final int BATCH = 50;
    private static final ExecutorService sWorker = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean sRunning = new AtomicBoolean();
    private static ContentObserver sObserver;

    private ParentChannel() {
    }

    /** HearthTube is opened: watch Hearth's row for changes, and catch up now */
    public static void onLaunch(Context context) {
        if (context == null || !ParentGate.isKidsProfile(context)) {
            return;
        }

        observe(context.getApplicationContext());
        sync(context);
    }

    /** Takes the parent's lists from Hearth and sends what it hasn't got; off the main thread */
    public static void sync(Context context) {
        if (context == null || !ParentGate.isKidsProfile(context) || !sRunning.compareAndSet(false, true)) {
            return;
        }

        Context app = context.getApplicationContext();

        sWorker.execute(() -> {
            try {
                HearthProfile hearth = HearthProfile.live(app, HearthProfile.queryHearth(app));

                // An older Hearth, or none reachable now: what was last known stands
                if (hearth != null && hearth.contractVersion >= 8) {
                    ParentBlockData.instance(app).setFromHearth(hearth.parentBlocks);
                }

                flush(app);
            } catch (RuntimeException e) {
                Log.e(TAG, "Parent channel sync failed: %s", e.getMessage());
            } finally {
                sRunning.set(false);
            }
        });
    }

    /** Sends the entries Hearth hasn't got, oldest first, until one isn't taken; on the caller's thread */
    static void flush(Context context) {
        WatchLogData log = WatchLogData.instance(context);
        List<Entry> unsent = log.getUnsent(BATCH);

        for (Entry entry : unsent) {
            if (!HearthProfile.reportStarted(context, entry.timeMs, entry.videoId, entry.title, entry.channel, entry.channelId)) {
                return;
            }

            log.markSent(entry.timeMs);
        }
    }

    private static synchronized void observe(Context app) {
        if (sObserver != null) {
            return;
        }

        sObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                sync(app);
            }
        };

        ContentResolver resolver = app.getContentResolver();

        for (Uri hearth : HearthProfile.ACTIVE_URIS) {
            try {
                resolver.registerContentObserver(hearth, false, sObserver);
            } catch (Exception e) {
                // No Hearth (or too old): each start and each video still syncs
            }
        }
    }
}
