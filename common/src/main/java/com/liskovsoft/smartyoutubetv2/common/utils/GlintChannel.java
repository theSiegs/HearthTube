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
 * HearthTube and Glint (the phone launcher), Glint contract 2. Phone and tablet build only, and only with a Glint
 * signed with Glint's key; without one nothing is sent or asked, and HearthTube goes its own way.
 * <ul>
 * <li>What plays here, for Glint's live HearthTube tile and the shared allowance, on events only (no timer of its own:
 * the player's half-minute tick paces the "playing" updates): report_started {videoId, title, channel, channelId?,
 * kids} as a video begins, and report_state {state: playing, paused or stopped, videoId, positionMs?, playedMs, kids},
 * where playedMs is the time actually played since the last report_state. Each carries contract and ts (epoch ms of
 * the event). Fire and forget: nothing is sent again when Glint doesn't take a report.</li>
 * <li>The phone's one daily limit, which Glint owns when it's there: minutes_left ({@link Limit}), asked by
 * {@link HearthAllowance} as playing starts and on the player's tick, and again when Glint announces a change on
 * {@link #observeUris}. Glint's kids mode is a kids signal too ({@link KidsDecision}).</li>
 * <li>The phone's one parent PIN: parent_pin_status and verify_parent_pin ({@link ParentGate}).</li>
 * </ul>
 */
public final class GlintChannel {
    private static final String TAG = GlintChannel.class.getSimpleName();
    private static final int CONTRACT = 2;
    private static final String RELEASE_PACKAGE = "com.thesiegs.glint";
    private static final String DEBUG_PACKAGE = "com.thesiegs.glint.debug";
    /** Glint's release key, and the debug key Glint, Hearth and HearthTube's debug builds share */
    private static final String RELEASE_CERT = "e2a4d2ecbdf91e437f3c72e310ebac5b21ee7dfbf163fd1b42ff0972189ea0eb";
    private static final String DEBUG_CERT = "6748528ff4d17fd57c30b6c5d522c467920d9951ea5d208597f91b66df9a2bfe";
    /** While a video plays on, a "playing" update at most this often (Glint asked for one at least every 10 min) */
    private static final long PLAYING_UPDATE_MS = 5 * 60_000;
    /** Glint's kids mode is asked again after this (sooner when Glint announces a change) */
    private static final long KIDS_CACHE_MS = 60_000;
    public static final String STATE_PLAYING = "playing";
    public static final String STATE_PAUSED = "paused";
    public static final String STATE_STOPPED = "stopped";
    /** Reports go out one at a time, off the main thread */
    private static final ExecutorService sReporter = Executors.newSingleThreadExecutor();
    /** One stretch counts at most the update interval twice over (a missed stop mustn't count for hours) */
    private static final PlayTimer sTimer = new PlayTimer(2 * PLAYING_UPDATE_MS, Long.MAX_VALUE);
    private static final GlintPlayLedger sLedger = new GlintPlayLedger();
    private static String sState = STATE_STOPPED;
    private static String sStartedVideoId;
    private static long sLastStateAt;
    private static String sTrustedPackage;
    private static long sTrustedInstall;
    private static Boolean sKids;
    private static long sKidsCheckedAt = -1;

    /** Glint's answer to minutes_left */
    public static final class Limit {
        /** Glint's kids mode */
        public boolean kids;
        /** Minutes left today; null is no limit */
        @Nullable public Integer minutes;
        /** "pool" (the shared allowance through Hearth's Home Assistant integration), "local" (Glint's own) or null */
        @Nullable public String source;
        /** Not now (a bedtime), whatever the minutes say */
        public boolean locked;
        /** When the lock ends (epoch ms) */
        @Nullable public Long until;
        /** Glint's own wording for the kid, when it refuses */
        @Nullable public String message;
        /** The ts of the last report_state Glint counted in {@link #minutes} */
        @Nullable public Long asOf;
    }

    private GlintChannel() {
    }

    /** A video begins (not a resume of the one already going) */
    public static synchronized void onStarted(Context context, String videoId, String title, String channel,
                                              @Nullable String channelId) {
        if (!isMobileBuild(context) || videoId == null || (videoId.equals(sStartedVideoId) && !STATE_STOPPED.equals(sState))) {
            return;
        }

        sStartedVideoId = videoId;
        Bundle extras = extras(context, System.currentTimeMillis());
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
        long ts = System.currentTimeMillis();
        sLedger.sent(ts, playedMs);

        Bundle extras = extras(context, ts);
        extras.putString("state", state);
        extras.putString("videoId", videoId != null ? videoId : sStartedVideoId);
        if (positionMs >= 0) {
            extras.putLong("positionMs", positionMs);
        }
        extras.putLong("playedMs", playedMs);
        send(context, "report_state", extras);
    }

    /**
     * What played here that Glint hadn't counted at {@code asOf} (see {@link Limit#asOf}), ms, including what is
     * playing right now
     */
    public static synchronized long playedSince(@Nullable Long asOf) {
        if (sTimer.isPlaying()) {
            sTimer.take(SystemClock.elapsedRealtime(), true);
        }

        return sLedger.playedSince(asOf, sTimer.unreportedMs());
    }

    /**
     * Asks Glint for the phone's daily limit (blocking: a quick call to Glint's provider). Null without a Glint, or
     * when it doesn't answer (too old, busy, failing).
     */
    @Nullable
    public static synchronized Limit limit(Context context) {
        Bundle result = isMobileBuild(context) ? call(context, "minutes_left", new Bundle()) : null;

        if (result == null) {
            return null;
        }

        Limit limit = new Limit();
        limit.kids = result.getBoolean("kids");
        Long minutes = number(result, "minutes");
        limit.minutes = minutes != null ? (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, minutes)) : null;
        limit.source = result.getString("source");
        limit.locked = result.getBoolean("locked");
        limit.until = number(result, "until");
        limit.message = result.getString("message");
        limit.asOf = number(result, "asOf");

        sKids = limit.kids;
        sKidsCheckedAt = SystemClock.elapsedRealtime();
        return limit;
    }

    /** Glint's kids mode: null without a Glint, or when it doesn't answer. Kept a minute, or until Glint announces a change. */
    @Nullable
    public static synchronized Boolean kids(Context context) {
        if (!isMobileBuild(context)) {
            return null;
        }

        if (sKidsCheckedAt != -1 && SystemClock.elapsedRealtime() - sKidsCheckedAt < KIDS_CACHE_MS) {
            return sKids;
        }

        Limit limit = limit(context);

        if (limit == null) {
            sKids = null;
            sKidsCheckedAt = SystemClock.elapsedRealtime();
        }

        return sKids;
    }

    /** Glint announced a change (limit, lock or kids mode): the next question asks it afresh */
    public static synchronized void onGlintChanged() {
        sKidsCheckedAt = -1;
    }

    /** Where Glint announces a change, for a ContentObserver */
    public static Uri[] observeUris() {
        return new Uri[] {Uri.parse("content://" + RELEASE_PACKAGE + ".hearthtube/minutes_left"),
                Uri.parse("content://" + DEBUG_PACKAGE + ".hearthtube/minutes_left")};
    }

    /** A Glint signed with Glint's key is installed (on the phone build) */
    public static boolean isInstalled(Context context) {
        return isMobileBuild(context) && trustedPackage(context) != null;
    }

    /** Whether Glint has a parent PIN: null without a Glint, or when it doesn't answer */
    @Nullable
    public static Boolean hasParentPin(Context context) {
        Bundle result = isMobileBuild(context) ? call(context, "parent_pin_status", new Bundle()) : null;
        return result != null ? result.getBoolean("hasPin") : null;
    }

    /**
     * Checks a parent PIN with Glint, which throttles wrong ones. Like {@link HearthProfile#verifyParentPin}:
     * {@link HearthProfile#PIN_OK}, {@link HearthProfile#PIN_WRONG}, {@link HearthProfile#PIN_UNAVAILABLE} or seconds
     * to wait.
     */
    public static int verifyParentPin(Context context, String pin) {
        Bundle extras = new Bundle();
        extras.putString("pin", pin);
        Bundle result = isMobileBuild(context) && pin != null ? call(context, "verify_parent_pin", extras) : null;

        if (result == null) {
            return HearthProfile.PIN_UNAVAILABLE;
        }

        if (result.getBoolean("ok")) {
            return HearthProfile.PIN_OK;
        }

        Long lockedUntil = number(result, "lockedUntil");
        long waitMs = (lockedUntil != null ? lockedUntil : 0) - System.currentTimeMillis();
        return waitMs > 0 ? (int) Math.max(1, (waitMs + 999) / 1000) : HearthProfile.PIN_WRONG;
    }

    /** One call to Glint's provider, blocking; null without a Glint, on failure, or from a Glint older than contract 2 */
    @Nullable
    private static Bundle call(Context context, String method, Bundle extras) {
        String glint = trustedPackage(context);

        if (glint == null) {
            return null;
        }

        try {
            extras.putInt("contract", CONTRACT);
            Bundle result = context.getContentResolver().call(uri(glint), method, null, extras);
            return result != null && result.getInt("contract", 0) >= CONTRACT ? result : null;
        } catch (Exception e) {
            Log.d(TAG, "Glint didn't answer %s: %s", method, e.getMessage());
            return null;
        }
    }

    /** A number Glint sent as an Int or a Long; null when it's absent */
    @Nullable
    private static Long number(Bundle bundle, String key) {
        Object value = bundle.get(key);
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private static Bundle extras(Context context, long ts) {
        Bundle extras = new Bundle();
        extras.putInt("contract", CONTRACT);
        extras.putLong("ts", ts);
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
    private static synchronized String trustedPackage(Context context) {
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
