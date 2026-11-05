package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HearthTube and Glint (the phone launcher), Glint contract 3. Phone and tablet build only, and only with a Glint
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
    /** What HearthTube speaks: Glint answers a failure as {contract: 3, error} instead of a null */
    private static final int CONTRACT = 3;
    /** The oldest answer HearthTube reads (v2 has every call; only an older one is "too old") */
    private static final int MIN_ANSWER = 2;
    private static final String RELEASE_PACKAGE = "com.thesiegs.glint";
    private static final String DEBUG_PACKAGE = "com.thesiegs.glint.debug";
    /** Glint's nightly build, signed with the release key for now */
    private static final String NIGHTLY_PACKAGE = "com.thesiegs.glint.nightly";
    private static final String PREFS = "glint_channel";
    /** Glint's last kids-mode answer, kept for when it goes silent */
    private static final String PREF_LAST_KIDS = "last_kids";
    /** Glint's release key, and the debug key Glint, Hearth and HearthTube's debug builds share */
    private static final String RELEASE_CERT = "e2a4d2ecbdf91e437f3c72e310ebac5b21ee7dfbf163fd1b42ff0972189ea0eb";
    private static final String DEBUG_CERT = "6748528ff4d17fd57c30b6c5d522c467920d9951ea5d208597f91b66df9a2bfe";
    /** While a video plays on, a "playing" update at most this often (Glint asked for one at least every 10 min) */
    private static final long PLAYING_UPDATE_MS = 5 * 60_000;
    /** Glint's kids mode is asked again after this (sooner when Glint announces a change) */
    private static final long KIDS_CACHE_MS = 60_000;
    /** A question asked while the screen waits (limit, kids mode, whether there's a PIN) */
    private static final long QUICK_TIMEOUT_MS = 1_500;
    /** A PIN check, which Glint may pass on to Hearth's Home Assistant integration over the network */
    private static final long PIN_TIMEOUT_MS = 10_000;
    public static final int PIN_NO_GLINT = 0;
    public static final int PIN_SET = 1;
    public static final int PIN_NOT_SET = 2;
    public static final int PIN_SILENT = 3;
    public static final String STATE_PLAYING = "playing";
    public static final String STATE_PAUSED = "paused";
    public static final String STATE_STOPPED = "stopped";
    /** Reports go out one at a time, off the main thread */
    private static final ExecutorService sReporter = Executors.newSingleThreadExecutor();
    /** Questions to Glint, each on a thread of its own so that a stuck one doesn't hold up the next */
    private static final ExecutorService sCaller = Executors.newCachedThreadPool();
    private static final AtomicBoolean sKidsRefreshing = new AtomicBoolean();
    /** One stretch counts at most the update interval twice over (a missed stop mustn't count for hours) */
    private static final PlayTimer sTimer = new PlayTimer(2 * PLAYING_UPDATE_MS, Long.MAX_VALUE);
    private static final GlintPlayLedger sLedger = new GlintPlayLedger();
    private static String sState = STATE_STOPPED;
    private static String sStartedVideoId;
    private static long sLastStateAt;
    private static String sTrustedPackage;
    private static long sTrustedInstall;
    private static volatile Boolean sKids;
    private static volatile long sKidsCheckedAt = -1;

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
        /** What made it a kid phone in Glint: "setting", "supervision", "hearth" or "none" (read, not acted on yet) */
        @Nullable public String kidsBy;
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
     * Asks Glint for the phone's daily limit (blocking, at most {@link #QUICK_TIMEOUT_MS}). Null without a Glint, or
     * when it doesn't answer (too old, busy, failing).
     */
    @Nullable
    public static Limit limit(Context context) {
        Reply reply = isMobileBuild(context) ? call(context, "minutes_left", new Bundle(), QUICK_TIMEOUT_MS) : null;
        Bundle result = reply != null ? reply.answer : null;

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
        limit.kidsBy = result.getString("kidsBy");

        sKids = limit.kids;
        sKidsCheckedAt = SystemClock.elapsedRealtime();
        prefs(context).edit().putBoolean(PREF_LAST_KIDS, limit.kids).apply();
        return limit;
    }

    /**
     * Glint's kids mode: null without a Glint, or when it doesn't answer. Kept a minute, or until Glint announces a
     * change; an old answer is used while a fresh one is asked off the main thread, so only the first one waits.
     */
    @Nullable
    public static Boolean kids(Context context) {
        if (!isMobileBuild(context)) {
            return null;
        }

        long checkedAt = sKidsCheckedAt;

        if (checkedAt == -1) {
            refreshKids(context);
            return sKids;
        }

        if (SystemClock.elapsedRealtime() - checkedAt >= KIDS_CACHE_MS && sKidsRefreshing.compareAndSet(false, true)) {
            Context app = context.getApplicationContext();
            sCaller.execute(() -> {
                try {
                    refreshKids(app);
                } finally {
                    sKidsRefreshing.set(false);
                }
            });
        }

        return sKids;
    }

    /**
     * Asks Glint afresh. A Glint that's installed but silent keeps its last "kids" (kept across restarts), so a kid
     * phone doesn't loosen while Glint is failing; without a Glint there's no word.
     */
    private static void refreshKids(Context context) {
        if (limit(context) == null) {
            sKids = trustedPackage(context) != null && prefs(context).getBoolean(PREF_LAST_KIDS, false) ? Boolean.TRUE : null;
            sKidsCheckedAt = SystemClock.elapsedRealtime();
        }
    }

    private static android.content.SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Glint announced a change (limit, lock or kids mode): the next question asks it afresh */
    public static void onGlintChanged() {
        sKidsCheckedAt = -1;
    }

    /** Where Glint announces a change, for a ContentObserver */
    public static Uri[] observeUris() {
        return new Uri[] {Uri.parse("content://" + RELEASE_PACKAGE + ".hearthtube/minutes_left"),
                Uri.parse("content://" + NIGHTLY_PACKAGE + ".hearthtube/minutes_left"),
                Uri.parse("content://" + DEBUG_PACKAGE + ".hearthtube/minutes_left")};
    }

    /**
     * Glint's parent PIN (blocking, at most {@link #QUICK_TIMEOUT_MS}): {@link #PIN_NO_GLINT} without a Glint, or one
     * too old for PINs (then HearthTube's own PIN goes on as before), {@link #PIN_SET}, {@link #PIN_NOT_SET}, or
     * {@link #PIN_SILENT} when it didn't answer in time
     */
    public static int pinStatus(Context context) {
        Reply reply = isMobileBuild(context) ? call(context, "parent_pin_status", new Bundle(), QUICK_TIMEOUT_MS) : null;

        if (reply == null || reply.old) {
            return PIN_NO_GLINT;
        }

        if (reply.answer == null) {
            return PIN_SILENT;
        }

        return reply.answer.getBoolean("hasPin") ? PIN_SET : PIN_NOT_SET;
    }

    /** What a PIN check gave: like {@link HearthProfile#verifyParentPin} */
    public interface PinResult {
        void done(int result);
    }

    /**
     * Checks a parent PIN with Glint off the main thread (Glint may ask Hearth's Home Assistant integration, over the
     * network), and gives {@link HearthProfile#PIN_OK}, {@link HearthProfile#PIN_WRONG},
     * {@link HearthProfile#PIN_UNAVAILABLE} or seconds to wait, on a background thread. Glint throttles wrong PINs.
     */
    public static void verifyParentPin(Context context, String pin, PinResult result) {
        Context app = context.getApplicationContext();

        sCaller.execute(() -> {
            Bundle extras = new Bundle();
            extras.putString("pin", pin);
            Reply reply = isMobileBuild(app) && pin != null ? call(app, "verify_parent_pin", extras, PIN_TIMEOUT_MS) : null;
            Bundle answer = reply != null ? reply.answer : null;

            if (answer == null) {
                result.done(HearthProfile.PIN_UNAVAILABLE);
            } else if (answer.getBoolean("ok")) {
                result.done(HearthProfile.PIN_OK);
            } else {
                Long lockedUntil = number(answer, "lockedUntil");
                long waitMs = (lockedUntil != null ? lockedUntil : 0) - System.currentTimeMillis();
                result.done(waitMs > 0 ? (int) Math.max(1, (waitMs + 999) / 1000) : HearthProfile.PIN_WRONG);
            }
        });
    }

    /** Glint's reply: its answer (contract 2 or newer), or it's too old for the call, or (neither) it didn't or couldn't answer */
    private static final class Reply {
        @Nullable final Bundle answer;
        final boolean old;

        Reply(@Nullable Bundle answer, boolean old) {
            this.answer = answer;
            this.old = old;
        }
    }

    /**
     * One call to Glint's provider, waiting at most {@code timeoutMs} (on a thread of its own, so a stuck Glint only
     * costs the wait). Null without a Glint signed with its key.
     */
    @Nullable
    private static Reply call(Context context, String method, Bundle extras, long timeoutMs) {
        String glint = trustedPackage(context);

        if (glint == null) {
            return null;
        }

        extras.putInt("contract", CONTRACT);
        Context app = context.getApplicationContext();
        Future<Bundle> future = sCaller.submit(() -> app.getContentResolver().call(uri(glint), method, null, extras));

        try {
            Bundle result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            // Only an answer that says it's from an older contract is "too old"; no answer at all from a Glint that's
            // installed is silence (it may be failing), never a reason to loosen anything
            if (result == null) {
                return new Reply(null, false);
            }
            // Glint couldn't answer (busy, unavailable, failing): silence, so nothing loosens
            if (result.containsKey("error")) {
                Log.d(TAG, "Glint couldn't answer %s: %s", method, result.getString("error"));
                return new Reply(null, false);
            }
            return result.getInt("contract", 0) >= MIN_ANSWER ? new Reply(result, false) : new Reply(null, true);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Log.d(TAG, "Glint didn't answer %s: %s", method, cause != null ? cause.getMessage() : null);
            return new Reply(null, false);
        } catch (TimeoutException e) {
            future.cancel(true);
            Log.d(TAG, "Glint didn't answer %s in %s ms", method, timeoutMs);
            return new Reply(null, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Reply(null, false);
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
                ? new String[] {RELEASE_PACKAGE, NIGHTLY_PACKAGE, DEBUG_PACKAGE} : new String[] {RELEASE_PACKAGE, NIGHTLY_PACKAGE};
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
                String expected = DEBUG_PACKAGE.equals(glint) ? DEBUG_CERT : RELEASE_CERT;
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
