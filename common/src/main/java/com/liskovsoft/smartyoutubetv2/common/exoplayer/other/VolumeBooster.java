package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.media.audiofx.LoudnessEnhancer;
import android.os.Build.VERSION;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioListener;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager.TickleListener;
import com.liskovsoft.smartyoutubetv2.common.utils.LoudnessNormalizer;

public class VolumeBooster implements AudioListener, TickleListener {
    private static final String TAG = VolumeBooster.class.getSimpleName();
    private static final int DRIFT_FIX_INTERVAL_MINUTES = 5;
    private boolean mIsEnabled;
    private float mVolume;
    private final SimpleExoPlayer mPlayer;
    private LoudnessEnhancer mBooster;
    private boolean mIsSupported;
    private int mCurrentSessionId = -1;
    private int mGainMb;
    private int mTickleCount;
    private boolean mIsPerVideo;

    public VolumeBooster(boolean enabled, float volume, @Nullable SimpleExoPlayer player) {
        mIsEnabled = enabled;
        mVolume = volume;
        mPlayer = player;
    }

    /**
     * HearthTube: the booster for "Even out volume", which lifts quiet videos (LoudnessNormalizer). Each video sets its
     * lift with {@link #setVolume}; it's off, and so is its compressor, unless a video needs one.
     */
    public static VolumeBooster forEvenVolume(@Nullable SimpleExoPlayer player) {
        VolumeBooster booster = new VolumeBooster(true, 1f, player);
        booster.mIsPerVideo = true;
        return booster;
    }

    @Override
    public void onAudioSessionId(int audioSessionId) {
        if (VERSION.SDK_INT < 19 || (mVolume <= 1 && !mIsPerVideo)) {
            return;
        }

        // NOTE: 5.1 audio cannot be boosted (format isn't supported error)
        if (mPlayer != null && mPlayer.getAudioFormat() != null && mPlayer.getAudioFormat().channelCount > 2) {
            return;
        }

        Log.d(TAG, "Audio session id is %s, supported gain %s", audioSessionId, LoudnessEnhancer.PARAM_TARGET_GAIN_MB);

        if (mBooster != null && audioSessionId == mCurrentSessionId) {
            return; // Already initialized for this session
        }

        mCurrentSessionId = audioSessionId;

        release();

        try {
            mBooster = new LoudnessEnhancer(audioSessionId);
            mBooster.setEnabled(mIsEnabled);

            //double log2 = Math.log(mVolume) / Math.log(2);
            //double gainMb = 10 * log2 * 100;
            //mBooster.setTargetGain((int) gainMb);

            double gainMb = 20 * Math.log10(mVolume * 3) * 100;
            mGainMb = mIsPerVideo ? getLiftMb() : (int) gainMb; // HearthTube: an exact lift
            mBooster.setTargetGain(mGainMb);
            if (mIsPerVideo) {
                mBooster.setEnabled(mIsEnabled && mGainMb > 0);
            }

            //mBooster.setTargetGain((int) (1000 * mVolume));

            mIsSupported = true;

            // DRIFT FIX: periodically reset the native compressor state.
            TickleManager.instance().addListener(this);
            mTickleCount = 0;
        } catch (RuntimeException | UnsatisfiedLinkError | NoClassDefFoundError | NoSuchFieldError e) { // Cannot initialize effect engine
            e.printStackTrace();
            mIsSupported = false;
        }
    }

    /**
     * DRIFT FIX: AOSP's le_fx::AdaptiveDynamicRangeCompression accumulates compressor_gain_ <br/>
     * multiplicatively using a Taylor-approximated exp(), so it drifts downward over long <br/>
     * sessions and never recovers. Re-applying the target gain triggers <br/>
     * EFFECT_CMD_SET_PARAM -> LE_reset() -> Initialize(), which resets compressor_gain_ to 1.0f.
     */
    @RequiresApi(19)
    @Override
    public void onTickle() {
        if (++mTickleCount > DRIFT_FIX_INTERVAL_MINUTES && mBooster != null && mIsSupported) {
            mTickleCount = 0;
            try {
                mBooster.setTargetGain(mGainMb);
                Log.d(TAG, "Drift fix: re-applied target gain %s mB", mGainMb);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isEnabled() {
        return mIsEnabled;
    }

    public void setEnabled(boolean enabled) {
        mIsEnabled = enabled;
        if (mBooster != null) {
            mBooster.setEnabled(enabled);
        }
    }

    public boolean isSupported() {
        return mIsSupported;
    }

    public boolean isPerVideo() {
        return mIsPerVideo;
    }

    /**
     * HearthTube: this video's volume, see LoudnessNormalizer. Above 1 is the lift, which is up to 6 dB and stereo only
     * (LoudnessEnhancer can't take more channels). Only a booster {@link #forEvenVolume} lifts.
     */
    public void setVolume(float volume) {
        if (!mIsPerVideo) {
            return; // SmartTube's boost is set once, when the player starts
        }

        mVolume = volume;

        if (mBooster == null || !mIsSupported) {
            return; // onAudioSessionId() applies it
        }

        try {
            int gainMb = getLiftMb();
            if (gainMb != mGainMb) {
                mGainMb = gainMb;
                mBooster.setTargetGain(gainMb);
            }
            mBooster.setEnabled(mIsEnabled && gainMb > 0);
            Log.d(TAG, "Lift %s mB for volume %s, enabled %s", gainMb, volume, mBooster.getEnabled());
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    /** HearthTube: what the booster adds now, 1 when it's off */
    public float getGain() {
        return mBooster != null && mIsSupported && mIsEnabled && mGainMb > 0 ? (float) Math.pow(10, mGainMb / 2000.0) : 1f;
    }

    private int getLiftMb() {
        boolean isSurround = mPlayer != null && mPlayer.getAudioFormat() != null && mPlayer.getAudioFormat().channelCount > 2;
        return isSurround ? 0 : LoudnessNormalizer.getLiftMb(mVolume);
    }

    public void release() {
        TickleManager.instance().removeListener(this);
        if (mBooster != null) {
            mBooster.release();
            mBooster = null;
        }
    }
}
