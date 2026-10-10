package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.utils.HearthAllowance;
import com.liskovsoft.smartyoutubetv2.common.utils.HearthProfile;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

/**
 * HearthTube: Hearth's daily YouTube allowance in the player (Hearth's provider contract 6, {@link HearthAllowance}).
 * Tells Hearth how long videos actually played: every half minute while playing, and when playback pauses or stops,
 * the player closes or HearthTube goes to the background. Stops playback when the allowance is used up or Home
 * Assistant's schedule is locked: checked as a video starts playing, every half minute while it plays, and when Hearth
 * announces a change (it does after each report). A video that hasn't started yet is checked as it opens
 * (PlaybackPresenter, VideoLoaderController).
 */
public class HearthAllowanceController extends BasePlayerController {
    private static final long TICK_MS = 30_000;
    private final Runnable mTick = this::tick;
    private boolean mTicking;
    private boolean mObserving;
    private final ContentObserver mHearthObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            HearthAllowance.stopIfRefused(getContext(), getPlayer());
        }
    };

    @Override
    public void onEngineInitialized() {
        observe(true);
    }

    @Override
    public void onPlay() {
        if (HearthAllowance.onPlaying()) {
            startTicking();
            HearthAllowance.stopIfRefused(getContext(), getPlayer());
        }
    }

    @Override
    public void onBuffering() {
        HearthAllowance.onWaiting(getContext());
    }

    @Override
    public void onPause() {
        stopped();
    }

    @Override
    public void onPlayEnd() {
        stopped();
    }

    @Override
    public void onEngineError(int type, int rendererIndex, Throwable error) {
        stopped();
    }

    @Override
    public void onFinish() {
        stopped();
    }

    @Override
    public void onEngineReleased() {
        stopped();
        observe(false);
    }

    @Override
    public void onViewDestroyed() {
        stopped();
        observe(false);
    }

    /** HearthTube went to the background (it may play on there) */
    @Override
    public void onViewPaused() {
        HearthAllowance.report(getContext());
    }

    private void stopped() {
        HearthAllowance.onStopped(getContext());
        Utils.removeCallbacks(mTick);
        mTicking = false;
    }

    private void startTicking() {
        if (!mTicking) {
            mTicking = true;
            Utils.postDelayed(mTick, TICK_MS);
        }
    }

    private void tick() {
        mTicking = false;
        PlaybackView player = getPlayer();

        // A stop that went unnoticed
        if (player == null || !player.isPlaying()) {
            stopped();
            return;
        }

        HearthAllowance.report(getContext());

        if (!HearthAllowance.stopIfRefused(getContext(), player)) {
            startTicking();
        }
    }

    private void observe(boolean observe) {
        if (observe == mObserving || getContext() == null) {
            return;
        }

        mObserving = observe;
        ContentResolver resolver = getContext().getApplicationContext().getContentResolver();

        if (!observe) {
            resolver.unregisterContentObserver(mHearthObserver);
            return;
        }

        for (Uri hearth : HearthProfile.ACTIVE_URIS) {
            try {
                resolver.registerContentObserver(hearth, false, mHearthObserver);
            } catch (Exception e) {
                // No Hearth (or too old): the half-minute check stays
            }
        }
    }
}
