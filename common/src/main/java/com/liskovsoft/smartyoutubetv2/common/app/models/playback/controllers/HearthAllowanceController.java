package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.utils.GlintChannel;
import com.liskovsoft.smartyoutubetv2.common.utils.HearthAllowance;
import com.liskovsoft.smartyoutubetv2.common.utils.HearthChanges;
import com.liskovsoft.smartyoutubetv2.common.utils.HearthProfile;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

/**
 * HearthTube: Hearth's daily YouTube allowance in the player (Hearth's provider contract 6, {@link HearthAllowance}).
 * Tells Hearth how long videos actually played: every half minute while playing, and when playback pauses or stops,
 * the player closes or HearthTube goes to the background. Stops playback when the allowance is used up or Home
 * Assistant's schedule is locked: checked as a video starts playing, every half minute while it plays, and when Hearth
 * announces a change (it does after each report). A video that hasn't started yet is checked as it opens
 * (PlaybackPresenter, VideoLoaderController). The same events go to Glint's HearthTube tile on a phone
 * ({@link GlintChannel}), its "playing" updates paced by the same tick; Glint's own limit is checked here like Hearth's,
 * and again when Glint announces a change.
 */
public class HearthAllowanceController extends BasePlayerController {
    private static final long TICK_MS = 30_000;
    private final Runnable mTick = this::tick;
    private boolean mTicking;
    private boolean mObserving;
    private final ContentObserver mHearthObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            GlintChannel.onGlintChanged();
            HearthAllowance.stopIfRefused(getContext(), getPlayer());
        }
    };
    private final Runnable mHearthChanged = () -> mHearthObserver.onChange(false);

    @Override
    public void onEngineInitialized() {
        observe(true);
    }

    @Override
    public void onPlay() {
        GlintChannel.onPlaying(getContext(), videoId(), positionMs());

        if (HearthAllowance.onPlaying()) {
            startTicking();
            HearthAllowance.stopIfRefused(getContext(), getPlayer());
        }
    }

    @Override
    public void onBuffering() {
        HearthAllowance.onWaiting(getContext());
        GlintChannel.onWaiting(getContext());
    }

    @Override
    public void onPause() {
        stopped();
        GlintChannel.onPaused(getContext(), videoId(), positionMs());
    }

    @Override
    public void onPlayEnd() {
        closed();
    }

    @Override
    public void onEngineError(int type, int rendererIndex, Throwable error) {
        closed();
    }

    @Override
    public void onFinish() {
        closed();
    }

    @Override
    public void onEngineReleased() {
        closed();
        observe(false);
    }

    @Override
    public void onViewDestroyed() {
        closed();
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

    /** Ended, failed or closed: stopped for Hearth, and for Glint too */
    private void closed() {
        stopped();
        GlintChannel.onStopped(getContext(), videoId(), positionMs());
    }

    private String videoId() {
        Video video = getVideo();
        return video != null ? video.videoId : null;
    }

    private long positionMs() {
        PlaybackView player = getPlayer();
        return player != null ? player.getPositionMs() : -1;
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
        if (player == null) {
            closed();
            return;
        }

        if (!player.isPlaying()) {
            onPause();
            return;
        }

        HearthAllowance.report(getContext());
        GlintChannel.onPlaying(getContext(), videoId(), positionMs());

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
            HearthChanges.remove(mHearthChanged);
            return;
        }

        // Hearth's changes come read once for every part that reacts to them (no Hearth: the half-minute check stays)
        HearthChanges.add(getContext(), mHearthChanged);

        // Glint announces a change of the phone's limit, lock or kids mode there
        for (Uri glint : GlintChannel.observeUris()) {
            try {
                resolver.registerContentObserver(glint, false, mHearthObserver);
            } catch (Exception e) {
                // No Glint: the half-minute check stays
            }
        }
    }
}
