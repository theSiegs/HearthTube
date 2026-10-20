package com.thesiegs.hearthtube.mobile.playback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
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
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer.CustomOverridesRenderersFactory;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.selector.RestoreTrackSelector;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;

import java.io.InputStream;
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
    private final Map<Integer, Integer> mButtonStates = new HashMap<>();

    private PlaybackPresenter mPresenter;
    private ExoPlayerController mEngine;
    private ExoPlayerInitializer mInitializer;
    private SimpleExoPlayer mPlayer;
    private com.google.android.exoplayer2.ui.PlayerView mPlayerView;
    private TextView mTitle;
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
            mTitle.setVisibility(visibility);
            if (mPresenter != null) {
                mPresenter.onControlsShown(mControlsShown);
            }
        });
        root.addView(mPlayerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mTitle = new TextView(this);
        mTitle.setTextColor(Color.WHITE);
        mTitle.setTextSize(16);
        mTitle.setMaxLines(2);
        mTitle.setBackgroundColor(0x66000000);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        mTitle.setPadding(pad, pad, pad, pad);
        root.addView(mTitle, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

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
        mPlayerView.setPlayer(null);
        mInitializer.release();
        mEngine.release();
        mPlayer = null;
    }

    // PlayerManager

    @Override
    public void setVideo(Video video) {
        mEngine.setVideo(video);

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
    }

    @Override
    public void updateEndingTime() {
    }

    @Override
    public void setChatReceiver(ChatReceiver chatReceiver) {
    }
}
