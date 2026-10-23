package com.thesiegs.hearthtube.mobile.playback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.ExoPlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.ExoPlayerInitializer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer.CustomOverridesRenderersFactory;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.selector.RestoreTrackSelector;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;

import java.io.InputStream;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.ReportPresenter;
import java.util.ArrayList;
import com.thesiegs.hearthtube.mobile.R;
import com.thesiegs.hearthtube.mobile.ui.HearthLook;
import com.thesiegs.hearthtube.mobile.ui.ViewTeardown;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HearthTube Mobile's player: ExoPlayer's own touch controls over the same engine ({@link ExoPlayerController}) and
 * the same {@link PlaybackPresenter} the TV uses, so every kids rule (blocked words, no Shorts, signed-in playback
 * only, the allowance) applies before a frame plays. What the TV puts around the player (suggestion rows, chat,
 * storyboards, extra buttons) is left out for now; those {@link PlaybackView} methods do nothing here.
 */
public class MobilePlaybackActivity extends MotherActivity implements PlaybackView {
    private static final int ID_QUALITY = com.liskovsoft.smartyoutubetv2.common.R.id.lb_control_high_quality;
    private static final int ID_CAPTIONS = com.liskovsoft.smartyoutubetv2.common.R.id.lb_control_closed_captioning;
    private static final int ID_SPEED = com.liskovsoft.smartyoutubetv2.common.R.id.action_video_speed;
    private static final int ID_REPEAT = com.liskovsoft.smartyoutubetv2.common.R.id.action_repeat;
    private static final int ID_SUBSCRIBE = com.liskovsoft.smartyoutubetv2.common.R.id.action_subscribe;
    private static final int ID_LIKE = com.liskovsoft.smartyoutubetv2.common.R.id.action_thumbs_up;
    private static final int ID_DISLIKE = com.liskovsoft.smartyoutubetv2.common.R.id.action_thumbs_down;
    private static final int ID_SAVE = com.liskovsoft.smartyoutubetv2.common.R.id.action_playlist_add;
    private static final int ID_SHARE = com.liskovsoft.smartyoutubetv2.common.R.id.action_share;
    private static final int ID_QUEUE = com.liskovsoft.smartyoutubetv2.common.R.id.action_playback_queue;
    private static final int ID_FIT = 1;
    private static final int ID_CHAPTERS = 2;
    /** Two taps this close are a double tap; after one, more taps keep seeking while they come this fast */
    private static final long DOUBLE_TAP_MS = 300;
    private static final long KEEP_SEEKING_MS = 700;
    private static final long SEEK_STEP_MS = 10_000;

    private final Map<Integer, Integer> mButtonStates = new HashMap<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mSingleTap = this::toggleControls;
    private final Runnable mHideSeekText = this::hideSeekText;
    private TextView mSeekText;
    private com.google.android.exoplayer2.ui.SubtitleView mSubtitleView;
    private SubtitleManager mSubtitleManager;
    private float mDownX;
    private float mDownY;
    private long mDownAt;
    private long mLastTapAt;
    private long mSeekingUntil;
    private long mSeekedMs;
    private List<SeekBarSegment> mSegments;
    private int mSegmentTries;
    private List<Video> mChapters;

    private PlaybackPresenter mPresenter;
    private ExoPlayerController mEngine;
    private ExoPlayerInitializer mInitializer;
    private SimpleExoPlayer mPlayer;
    private com.google.android.exoplayer2.ui.PlayerView mPlayerView;
    private TextView mTitle;
    private PlayerActions mActions;
    private LinearLayout mTop;
    private ProgressBar mProgress;
    private boolean mEngineBlocked;
    private boolean mControlsShown;
    private boolean mFinishing;
    private boolean mNoisyRegistered;
    /** Headphones unplugged (or a Bluetooth speaker gone): pause instead of playing on through the phone's speaker */
    private final BroadcastReceiver mBecomingNoisy = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mPlayer != null && mEngine != null) {
                mEngine.setPlayWhenReady(false);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(createContent());

        mInitializer = new ExoPlayerInitializer(this);
        mPresenter = PlaybackPresenter.instance(this);
        mEngine = new ExoPlayerController(this, mPresenter); // before setView: the presenter asks for the video at once
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    /** A phone's and tablet's own density: the TV's fixed-width scaling would make everything too big or small */
    @Override
    protected void initDpi() {
    }

    @Override
    protected void initTheme() {
    }

    /** A swipe near the left edge is the player's own (seeking, the system's back gesture), not the TV app's slide-to-close */
    @Override
    protected void initEdgeSlide() {
    }

    private View createContent() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        mPlayerView = new com.google.android.exoplayer2.ui.PlayerView(this);
        mPlayerView.setControllerAutoShow(true);
        mPlayerView.setShowBuffering(com.google.android.exoplayer2.ui.PlayerView.SHOW_BUFFERING_NEVER);
        mPlayerView.setControllerVisibilityListener((PlayerControlView.VisibilityListener) visibility -> {
            mControlsShown = visibility == View.VISIBLE;
            mTop.setVisibility(visibility);
            if (mPresenter != null) {
                mPresenter.onControlsShown(mControlsShown);
            }
        });
        root.addView(mPlayerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mPlayerView.setOnTouchListener(this::onPlayerTouch);

        mSubtitleView = new com.google.android.exoplayer2.ui.SubtitleView(this);
        mSubtitleView.setClickable(false);
        mSubtitleView.setFocusable(false);
        root.addView(mSubtitleView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mSeekText = new TextView(this);
        mSeekText.setTextColor(Color.WHITE);
        mSeekText.setTextSize(22);
        mSeekText.setVisibility(View.GONE);
        HearthLook.textShadow(mSeekText);
        root.addView(mSeekText, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        mTitle = new TextView(this);
        mTitle.setTextColor(Color.WHITE);
        mTitle.setTextSize(16);
        mTitle.setMaxLines(2);
        mTitle.setBackgroundColor(0x66000000);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        mTitle.setPadding(pad, pad, pad, pad);
        mActions = new PlayerActions(this);
        addActions();
        mTop = new LinearLayout(this);
        mTop.setOrientation(LinearLayout.VERTICAL);
        mTop.addView(mTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mTop.addView(mActions.view(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(mTop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        // The accent (Hearth's) on the seek bar and the spinner; the video stays on black
        int accent = HearthLook.accent(this);
        android.view.View timeBar = mPlayerView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_progress);
        if (timeBar instanceof com.google.android.exoplayer2.ui.DefaultTimeBar) {
            ((com.google.android.exoplayer2.ui.DefaultTimeBar) timeBar).setPlayedColor(accent);
            ((com.google.android.exoplayer2.ui.DefaultTimeBar) timeBar).setScrubberColor(accent);
        }
        HearthLook.textShadow(mTitle);

        mProgress = new ProgressBar(this);
        mProgress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(accent));
        root.addView(mProgress, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        return root;
    }

    /**
     * The buttons under the title. Each runs the TV's own code through the presenter (its dialogs are the shared
     * ones), so the rules are the same: what is for signed-in adults only on the TV is not offered here to a kid.
     */
    private void addActions() {
        mActions.add(0, getString(R.string.player_settings), () -> click(ID_QUALITY));
        mActions.add(ID_CAPTIONS, getString(R.string.player_captions), () -> click(ID_CAPTIONS));
        mActions.add(0, getString(R.string.player_speed), () -> longClick(ID_SPEED));
        mActions.add(0, getString(R.string.player_loop), () -> longClick(ID_REPEAT));
        mActions.add(ID_FIT, getString(R.string.player_fill), () -> {
            boolean fill = mPlayerView.getResizeMode() != com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
            mPlayerView.setResizeMode(fill ? com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    : com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
            mActions.setOn(ID_FIT, fill);
        });
        mActions.add(ID_CHAPTERS, getString(R.string.player_chapters), this::showChapters);
        mActions.setVisible(ID_CHAPTERS, false);
        // Acting as the signed-in account: never on a kid's phone, and only when someone is signed in
        if (!com.liskovsoft.smartyoutubetv2.common.utils.ParentGate.isKidsProfile(this)
                && com.liskovsoft.youtubeapi.service.YouTubeSignInService.instance().isSigned()) {
            mActions.add(ID_SUBSCRIBE, getString(R.string.player_subscribe), () -> click(ID_SUBSCRIBE));
            mActions.add(0, getString(R.string.player_bell), () -> longClick(ID_SUBSCRIBE));
            mActions.add(ID_LIKE, getString(R.string.player_like), () -> click(ID_LIKE));
            mActions.add(ID_DISLIKE, getString(R.string.player_dislike), () -> click(ID_DISLIKE));
            mActions.add(ID_SAVE, getString(R.string.player_save), () -> click(ID_SAVE));
            mActions.add(0, getString(R.string.player_share), () -> click(ID_SHARE));
            mActions.add(0, getString(R.string.player_queue), () -> click(ID_QUEUE));
        }

        mActions.add(0, getString(R.string.player_report), () -> {
            Video video = mEngine != null ? mEngine.getVideo() : null;

            if (video != null) {
                ReportPresenter.showReport(this, video);
            }
        });
    }

    private void click(int id) {
        if (mPresenter != null) {
            mPresenter.onButtonClicked(id, getButtonState(id));
        }
    }

    private void longClick(int id) {
        if (mPresenter != null) {
            mPresenter.onButtonLongClicked(id, getButtonState(id));
        }
    }

    private void hideSeekText() {
        mSeekText.setVisibility(View.GONE);
    }

    private void toggleControls() {
        showControls(!mControlsShown);
    }

    /**
     * A tap shows or hides the controls (after waiting to see if a second one follows). Two taps seek, ten seconds
     * back on the left, ten forward on the right, and more taps in a row keep going; two in the middle pause or play.
     */
    private boolean onPlayerTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = event.getX();
                mDownY = event.getY();
                mDownAt = event.getEventTime();
                return true;
            case MotionEvent.ACTION_UP:
                int slop = ViewConfiguration.get(this).getScaledTouchSlop();
                boolean tap = Math.abs(event.getX() - mDownX) < slop && Math.abs(event.getY() - mDownY) < slop
                        && event.getEventTime() - mDownAt < 300;

                if (tap) {
                    onTap(event.getX(), view.getWidth(), event.getEventTime());
                }

                return true;
            default:
                return true;
        }
    }

    private void onTap(float x, int width, long now) {
        if (now < mSeekingUntil || now - mLastTapAt < DOUBLE_TAP_MS) {
            mHandler.removeCallbacks(mSingleTap);
            mLastTapAt = 0;
            doubleTap(x, width, now);
        } else {
            mLastTapAt = now;
            mHandler.postDelayed(mSingleTap, DOUBLE_TAP_MS);
        }
    }

    private void doubleTap(float x, int width, long now) {
        if (mEngine == null) {
            return;
        }

        if (x > width * 0.4f && x < width * 0.6f) {
            mEngine.setPlayWhenReady(!mEngine.getPlayWhenReady());
            return;
        }

        boolean forward = x >= width * 0.5f;
        long step = forward ? SEEK_STEP_MS : -SEEK_STEP_MS;

        if (now >= mSeekingUntil) {
            mSeekedMs = 0;
        }

        long duration = mEngine.getDurationMs();
        long target = Math.max(0, mEngine.getPositionMs() + step);

        if (duration > 0) {
            target = Math.min(target, duration);
        }

        mEngine.setPositionMs(target);
        mSeekedMs += step;
        mSeekingUntil = now + KEEP_SEEKING_MS;
        showSeekText(forward, mSeekedMs);
    }

    private void showSeekText(boolean forward, long seekedMs) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mSeekText.getLayoutParams();
        int margin = (int) (getResources().getDisplayMetrics().widthPixels * 0.2f);
        params.gravity = Gravity.CENTER_VERTICAL | (forward ? Gravity.END : Gravity.START);
        params.setMarginStart(forward ? 0 : margin);
        params.setMarginEnd(forward ? margin : 0);
        mSeekText.setLayoutParams(params);
        mSeekText.setText((seekedMs > 0 ? "+" : "−") + Math.abs(seekedMs) / 1000 + " s");
        mSeekText.setVisibility(View.VISIBLE);
        mHandler.removeCallbacks(mHideSeekText);
        mHandler.postDelayed(mHideSeekText, KEEP_SEEKING_MS);
    }

    /** Ticks on the seek bar where the chapters (or SponsorBlock's segments) start */
    private void applySegments() {
        View bar = mPlayerView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_progress);

        if (!(bar instanceof com.google.android.exoplayer2.ui.DefaultTimeBar) || mEngine == null) {
            return;
        }

        com.google.android.exoplayer2.ui.DefaultTimeBar timeBar = (com.google.android.exoplayer2.ui.DefaultTimeBar) bar;

        if (mSegments == null || mSegments.isEmpty()) {
            timeBar.setAdGroupTimesMs(null, null, 0);
            return;
        }

        long duration = mEngine.getDurationMs();

        if (duration <= 0) { // the length isn't known yet: try again shortly
            if (mSegmentTries++ < 10) {
                mHandler.postDelayed(this::applySegments, 1000);
            }

            return;
        }

        long[] times = new long[mSegments.size()];
        boolean[] played = new boolean[times.length];

        for (int i = 0; i < times.length; i++) {
            times[i] = (long) (mSegments.get(i).startProgress * duration);
        }

        timeBar.setAdGroupTimesMs(times, played, times.length);
    }

    private void showChapters() {
        if (mChapters == null || mChapters.isEmpty()) {
            return;
        }

        AppDialogPresenter dialog = AppDialogPresenter.instance(this);

        for (Video chapter : mChapters) {
            long seconds = chapter.startTimeMs / 1000;
            String time = seconds >= 3600
                    ? String.format(java.util.Locale.US, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                    : String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60);
            dialog.appendSingleButton(UiOptionItem.from(time + "  " + chapter.getTitle(), option -> {
                setPositionMs(chapter.startTimeMs);
                dialog.closeDialog();
            }));
        }

        dialog.showDialog(getString(R.string.player_chapters));
    }

    // Lifecycle: the engine lives while the screen is visible, as on the TV

    @Override
    protected void onStart() {
        super.onStart();
        initializePlayer();
    }

    @Override
    protected void onStop() {
        super.onStop();
        maybeReleasePlayer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        initializePlayer();
        mPresenter.onViewResumed();
        blockEngine(false);
        hideSystemBarsInLandscape();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPresenter.onViewPaused();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();

        ViewTeardown.destroyed(mPresenter, this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hideSystemBarsInLandscape();
    }

    /** Sideways, the picture takes the whole screen; upright, the system bars stay */
    private void hideSystemBarsInLandscape() {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int flags = landscape
                ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                : View.SYSTEM_UI_FLAG_VISIBLE;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void initializePlayer() {
        if (mPlayer != null) {
            return;
        }

        DefaultTrackSelector trackSelector = new RestoreTrackSelector(new AdaptiveTrackSelection.Factory());
        mEngine.setTrackSelector(trackSelector);
        mPlayer = mInitializer.createPlayer(this, new CustomOverridesRenderersFactory(this), trackSelector);
        mEngine.setPlayer(mPlayer);
        mPlayerView.setPlayer(mPlayer);

        // Captions drawn by the TV's own manager, so they follow the caption style and size chosen in Settings
        if (mPlayerView.getSubtitleView() != null) {
            mPlayerView.getSubtitleView().setVisibility(View.GONE);
        }

        mSubtitleManager = new SubtitleManager(mSubtitleView);

        if (mPlayer.getTextComponent() != null) {
            mPlayer.getTextComponent().addTextOutput(mSubtitleManager);
        }
        mEngine.setPlayerView(new com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.PlayerView() {
            @Override
            public void setQualityInfo(String info) {
            }

            @Override
            public void setVideo(Video video) {
            }
        });

        mPresenter.setView(this);
        mPresenter.onEngineInitialized();

        // This ExoPlayer is too old to have setHandleAudioBecomingNoisy
        if (!mNoisyRegistered) {
            registerReceiver(mBecomingNoisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
            mNoisyRegistered = true;
        }
    }

    private void maybeReleasePlayer() {
        if (!mEngineBlocked) {
            releasePlayer();
        }
    }

    private void releasePlayer() {
        if (mPlayer == null) {
            return;
        }

        if (mNoisyRegistered) {
            unregisterReceiver(mBecomingNoisy);
            mNoisyRegistered = false;
        }

        mPresenter.onEngineReleased();

        if (mSubtitleManager != null && mPlayer.getTextComponent() != null) {
            mPlayer.getTextComponent().removeTextOutput(mSubtitleManager);
        }

        mSubtitleManager = null;
        mPlayerView.setPlayer(null);
        mInitializer.release();
        mEngine.release();
        mPlayer = null;
    }

    // PlayerManager

    @Override
    public void setVideo(Video video) {
        mEngine.setVideo(video);

        mChapters = null;
        mSegments = null;
        mSegmentTries = 0;
        mActions.setVisible(ID_CHAPTERS, false);

        if (video != null) {
            mTitle.setText(video.getTitleFull() != null ? video.getTitleFull() : "");
        }
    }

    @Override
    public Video getVideo() {
        return mEngine.getVideo();
    }

    @Override
    public void finish() {
        super.finish();
    }

    @Override
    public void finishReally() {
        if (!mFinishing) {
            mFinishing = true;
            super.finish();
        }
    }

    @Override
    public void showBackground(String url) {
    }

    @Override
    public void showBackgroundColor(int colorResId) {
    }

    @Override
    public void resetPlayerState() {
        mEngine.resetPlayerState();
    }

    @Override
    public boolean isEmbed() {
        return false;
    }

    // PlayerEngine

    @Override
    public void openSabr(MediaItemFormatInfo formatInfo) {
        mEngine.openSabr(formatInfo);
    }

    @Override
    public void openDash(MediaItemFormatInfo formatInfo) {
        mEngine.openDash(formatInfo);
    }

    @Override
    public void openDash(InputStream dashManifest) {
        mEngine.openDash(dashManifest);
    }

    @Override
    public void openDashUrl(String dashManifestUrl) {
        mEngine.openDashUrl(dashManifestUrl);
    }

    @Override
    public void openHlsUrl(String hlsPlaylistUrl) {
        mEngine.openHlsUrl(hlsPlaylistUrl);
    }

    @Override
    public void openUrlList(List<String> urlList) {
        mEngine.openUrlList(urlList);
    }

    @Override
    public void openMerged(MediaItemFormatInfo formatInfo, String hlsPlaylistUrl) {
        mEngine.openMerged(formatInfo, hlsPlaylistUrl);
    }

    @Override
    public void openMerged(InputStream dashManifest, String hlsPlaylistUrl) {
        mEngine.openMerged(dashManifest, hlsPlaylistUrl);
    }

    @Override
    public long getPositionMs() {
        return mEngine.getPositionMs();
    }

    @Override
    public void setPositionMs(long positionMs) {
        mEngine.setPositionMs(positionMs);
    }

    @Override
    public long getDurationMs() {
        return mEngine.getDurationMs();
    }

    @Override
    public void setPlayWhenReady(boolean play) {
        mEngine.setPlayWhenReady(play);
    }

    @Override
    public boolean getPlayWhenReady() {
        return mEngine.getPlayWhenReady();
    }

    @Override
    public boolean isPlaying() {
        return mEngine.isPlaying();
    }

    @Override
    public boolean isLoading() {
        return mEngine.isLoading();
    }

    @Override
    public List<FormatItem> getVideoFormats() {
        return mEngine.getVideoFormats();
    }

    @Override
    public List<FormatItem> getAudioFormats() {
        return mEngine.getAudioFormats();
    }

    @Override
    public List<FormatItem> getSubtitleFormats() {
        return mEngine.getSubtitleFormats();
    }

    @Override
    public void setFormat(FormatItem option) {
        mEngine.selectFormat(option);
    }

    @Override
    public FormatItem getVideoFormat() {
        return mEngine.getVideoFormat();
    }

    @Override
    public FormatItem getAudioFormat() {
        return mEngine.getAudioFormat();
    }

    @Override
    public FormatItem getSubtitleFormat() {
        return mEngine.getSubtitleFormat();
    }

    @Override
    public boolean isEngineInitialized() {
        return mPlayer != null;
    }

    @Override
    public void restartEngine() {
        releasePlayer();
        initializePlayer();
    }

    @Override
    public void reloadPlayback() {
        if (mPlayer != null) {
            mPresenter.onEngineReleased();
            mPresenter.onEngineInitialized();
        }
    }

    @Override
    public void blockEngine(boolean block) {
        mEngineBlocked = block;
    }

    @Override
    public boolean isEngineBlocked() {
        return mEngineBlocked;
    }

    /** A kid's phone doesn't play in a corner of the screen over other apps */
    @Override
    public boolean isInPIPMode() {
        return false;
    }

    @Override
    public boolean containsMedia() {
        return mEngine.containsMedia();
    }

    @Override
    public void setSpeed(float speed) {
        mEngine.setSpeed(speed);
    }

    @Override
    public float getSpeed() {
        return mEngine.getSpeed();
    }

    @Override
    public void setPitch(float pitch) {
        mEngine.setPitch(pitch);
    }

    @Override
    public float getPitch() {
        return mEngine.getPitch();
    }

    @Override
    public void setVolume(float volume) {
        mEngine.setVolume(volume);
    }

    @Override
    public float getVolume() {
        return mEngine.getVolume();
    }

    @Override
    public void setResizeMode(int mode) {
        mPlayerView.setResizeMode(mode);
    }

    @Override
    public int getResizeMode() {
        return mPlayerView.getResizeMode();
    }

    @Override
    public void setZoomPercents(int percents) {
    }

    @Override
    public void setAspectRatio(float ratio) {
    }

    @Override
    public void setRotationAngle(int angle) {
    }

    @Override
    public void setVideoFlipEnabled(boolean enabled) {
    }

    @Override
    public void setVideoGravity(int gravity) {
    }

    // PlayerUI: the TV's suggestion rows, chat, storyboards and button rows have no phone version yet

    @Override
    public void updateSuggestions(VideoGroup group) {
        if (group != null && group.isChapters()) { // the chapters ride in as a row; here they are a button's list
            mChapters = new ArrayList<>(group.getVideos());
            mActions.setVisible(ID_CHAPTERS, true);
        }
    }

    @Override
    public void removeSuggestions(VideoGroup group) {
    }

    @Override
    public int getSuggestionsIndex(VideoGroup group) {
        return -1;
    }

    @Override
    public VideoGroup getSuggestionsByIndex(int index) {
        return null;
    }

    @Override
    public void focusSuggestedItem(int index) {
    }

    @Override
    public void focusSuggestedItem(Video video) {
    }

    @Override
    public void resetSuggestedPosition() {
    }

    @Override
    public boolean isSuggestionsEmpty() {
        return true;
    }

    @Override
    public void clearSuggestions() {
    }

    @Override
    public void showOverlay(boolean show) {
        showControls(show);
    }

    @Override
    public boolean isOverlayShown() {
        return mControlsShown;
    }

    @Override
    public void showSuggestions(boolean show) {
    }

    @Override
    public boolean isSuggestionsShown() {
        return false;
    }

    @Override
    public void showControls(boolean show) {
        if (show) {
            mPlayerView.showController();
        } else {
            mPlayerView.hideController();
        }
    }

    @Override
    public boolean isControlsShown() {
        return mControlsShown;
    }

    @Override
    public int getButtonState(int buttonId) {
        Integer state = mButtonStates.get(buttonId);
        return state != null ? state : BUTTON_OFF;
    }

    @Override
    public void setButtonState(int buttonId, int buttonState) {
        mButtonStates.put(buttonId, buttonState);
        mActions.setOn(buttonId, buttonState == BUTTON_ON);
    }

    @Override
    public void setChannelIcon(String iconUrl) {
    }

    @Override
    public void setSeekPreviewTitle(String title) {
    }

    @Override
    public void setNextTitle(Video nextVideo) {
    }

    @Override
    public void showDebugInfo(boolean show) {
    }

    @Override
    public void showSubtitles(boolean show) {
        if (mSubtitleManager != null) {
            mSubtitleManager.show(show);
        }
    }

    @Override
    public void loadStoryboard() {
    }

    @Override
    public void setTitle(String title) {
        mTitle.setText(title);
    }

    @Override
    public void showProgressBar(boolean show) {
        mProgress.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void setSeekBarSegments(List<SeekBarSegment> segments) {
        mSegments = segments;
        mSegmentTries = 0;
        applySegments();
    }

    @Override
    public void updateEndingTime() {
    }

    @Override
    public void setChatReceiver(ChatReceiver chatReceiver) {
    }
}
