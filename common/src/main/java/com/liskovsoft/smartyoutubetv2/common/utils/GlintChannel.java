package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HearthTube: tells Glint (the phone launcher) what plays here, for its live HearthTube tile (Glint contract 1).
 * Phone and tablet build only, and only when Glint is installed and signed with Glint's key; otherwise nothing is sent.
 * Two calls, on events only (no timer of its own: the player's half-minute tick paces the "playing" updates):
 * report_started {videoId, title, channel, channelId?, kids} as a video begins, and report_state {state: playing,
 * paused or stopped, videoId, positionMs?, playedMs, kids}, where playedMs is the time actually played since the last
 * report_state. Each carries contract=1 and ts (epoch ms of the event). Fire and forget: Glint only shows it, so
 * nothing is kept or sent again when it doesn't take a report.
 */
public final class GlintChannel {
    private static final String TAG = GlintChannel.class.getSimpleName();
    private static final int CONTRACT = 1;
    private static final String RELEASE_PACKAGE = "com.thesiegs.glint";
    private static final String DEBUG_PACKAGE = "com.thesiegs.glint.debug";
    /** Glint's release key, and the debug key Glint, Hearth and HearthTube's debug builds share */
    private static final String RELEASE_CERT = "e2a4d2ecbdf91e437f3c72e310ebac5b21ee7dfbf163fd1b42ff0972189ea0eb";
    private static final String DEBUG_CERT = "6748528ff4d17fd57c30b6c5d522c467920d9951ea5d208597f91b66df9a2bfe";
    /** While a video plays on, a "playing" update at most this often (Glint asked for one at least every 10 min) */
    private static final long PLAYING_UPDATE_MS = 5 * 60_000;
    public static final String STATE_PLAYING = "playing";
    public static final String STATE_PAUSED = "paused";
    public static final String STATE_STOPPED = "stopped";
    /** Reports go out one at a time, off the main thread */
    private static final ExecutorService sReporter = Executors.newSingleThreadExecutor();
    /** One stretch counts at most the update interval twice over (a missed stop mustn't count for hours) */
    private static final PlayTimer sTimer = new PlayTimer(2 * PLAYING_UPDATE_MS, Long.MAX_VALUE);
    private static String sState = STATE_STOPPED;
    private static String sStartedVideoId;
    private static long sLastStateAt;
    private static String sTrustedPackage;
    private static long sTrustedInstall;

    private GlintChannel() {
    }

    /** A video begins (not a resume of the one already going) */
    public static synchronized void onStarted(Context context, String videoId, String title, String channel,
                                              @Nullable String channelId) {
        if (!isMobileBuild(context) || videoId == null || (videoId.equals(sStartedVideoId) && !STATE_STOPPED.equals(sState))) {
            return;
        }

        sStartedVideoId = videoId;
        Bundle extras = extras(context);
        extras.putString("videoId", videoId);
        extras.putString("title", title);
        extras.putString("channel", channel);
        if (channelId != null) {
            extras.putString("channelId", channelId);
        }
        send(context, "report_started", extras);
    }

    /** Playing (again), or still playing on the player's tick: an update when it starts, then every few minutes */
    public static synchronized void onPlaying(Context context, @Nullable String videoId, long positionMs) {
        if (!isMobileBuild(context)) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        boolean started = sTimer.start(now);

        if (started || !STATE_PLAYING.equals(sState) || now - sLastStateAt >= PLAYING_UPDATE_MS) {
            sTimer.take(now, true);
            state(context, STATE_PLAYING, videoId, positionMs);
        }
    }

    /** Waiting (buffering): the time so far counts, and goes with the next update */
    public static synchronized void onWaiting(Context context) {
        if (isMobileBuild(context)) {
            sTimer.take(SystemClock.elapsedRealtime(), false);
        }
    }

    public static synchronized void onPaused(Context context, @Nullable String videoId, long positionMs) {
        if (isMobileBuild(context) && !STATE_PAUSED.equals(sState) && !STATE_STOPPED.equals(sState)) {
            sTimer.take(SystemClock.elapsedRealtime(), false);
            state(context, STATE_PAUSED, videoId, positionMs);
        }
    }

    /** Ended, failed, or the player closed */
    public static synchronized void onStopped(Context context, @Nullable String videoId, long positionMs) {
        if (isMobileBuild(context) && !STATE_STOPPED.equals(sState)) {
            sTimer.take(SystemClock.elapsedRealtime(), false);
            state(context, STATE_STOPPED, videoId, positionMs);
        }
    }

    /** Sends the state with all the playing counted since the last one (the caller has just taken it into the timer) */
    private static void state(Context context, String state, @Nullable String videoId, long positionMs) {
        sState = state;
        sLastStateAt = SystemClock.elapsedRealtime();
        long playedMs = sTimer.nextReport(1);

        Bundle extras = extras(context);
        extras.putString("state", state);
        extras.putString("videoId", videoId != null ? videoId : sStartedVideoId);
        if (positionMs >= 0) {
            extras.putLong("positionMs", positionMs);
        }
        extras.putLong("playedMs", playedMs);
        send(context, "report_state", extras);
    }

    private static Bundle extras(Context context) {
        Bundle extras = new Bundle();
        extras.putInt("contract", CONTRACT);
        extras.putLong("ts", System.currentTimeMillis());
        extras.putBoolean("kids", ParentGate.isKidsProfile(context));
        return extras;
    }

    private static void send(Context context, String method, Bundle extras) {
        Context app = context.getApplicationContext();

        sReporter.execute(() -> {
            String glint = trustedPackage(app);

            if (glint == null) {
                return;
            }

            try {
                Bundle result = app.getContentResolver().call(uri(glint), method, null, extras);
                Log.d(TAG, "Glint %s %s: %s", method, extras.getString("state", ""), result != null && result.getBoolean("ok"));
            } catch (Exception e) {
                Log.d(TAG, "Glint didn't take %s: %s", method, e.getMessage());
            }
        });
    }

    private static Uri uri(String glintPackage) {
        return Uri.parse("content://" + glintPackage + ".hearthtube/");
    }

    private static boolean isMobileBuild(Context context) {
        return context != null && KidsMode.isMobileBuild(context);
    }

    /** The installed Glint whose provider this is, signed with Glint's key (checked once per install); null when none */
    @Nullable
    private static String trustedPackage(Context context) {
        String[] candidates = com.liskovsoft.smartyoutubetv2.common.BuildConfig.DEBUG
                ? new String[] {RELEASE_PACKAGE, DEBUG_PACKAGE} : new String[] {RELEASE_PACKAGE};
        PackageManager packageManager = context.getPackageManager();

        for (String glint : candidates) {
            try {
                ProviderInfo provider = packageManager.resolveContentProvider(glint + ".hearthtube", 0);
                if (provider == null || !glint.equals(provider.packageName)) {
                    continue;
                }

                long installed = packageManager.getPackageInfo(glint, 0).lastUpdateTime;
                if (glint.equals(sTrustedPackage) && installed == sTrustedInstall) {
                    return glint;
                }

                String[] digests = HearthProfile.signerDigests(packageManager, glint);
                String expected = RELEASE_PACKAGE.equals(glint) ? RELEASE_CERT : DEBUG_CERT;
                if (digests != null && digests.length == 1 && expected.equals(digests[0])) {
                    sTrustedPackage = glint;
                    sTrustedInstall = installed;
                    return glint;
                }

                Log.e(TAG, "Not Glint's signing key: %s", glint);
            } catch (Exception e) {
                // Not installed, or unreadable
            }
        }

        return null;
    }
}
