package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.BackgroundManager;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.utils.DailyBackground;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;
import io.reactivex.disposables.Disposable;

public class UriBackgroundManager {
    private static final String TAG = UriBackgroundManager.class.getSimpleName();
    private static final int BACKGROUND_UPDATE_DELAY_MS = 300;
    private Uri mBackgroundURI;
    private Drawable mDefaultBackground;
    private DisplayMetrics mMetrics;
    private Runnable mBackgroundTask;
    private BackgroundManager mBackgroundManager;
    private final Activity mActivity;
    private final Handler mHandler;
    private int mBackgroundColor = -1;
    private Disposable mDailyBackgroundAction;

    public UriBackgroundManager(Activity activity) {
        mActivity = activity;
        mHandler = new Handler();
        prepareBackgroundManager();
        setDefaultBackground();
    }

    private void prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(mActivity);
        mBackgroundManager.attach(mActivity.getWindow());
        mDefaultBackground = ContextCompat.getDrawable(mActivity, Helpers.getThemeAttr(mActivity, R.attr.shelfBackground));
        mBackgroundTask = new UpdateBackgroundTask();
        mMetrics = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    private void startBackgroundTimer(Uri backgroundURI) {
        mBackgroundURI = backgroundURI;
        mHandler.removeCallbacks(mBackgroundTask);
        mHandler.postDelayed(mBackgroundTask, BACKGROUND_UPDATE_DELAY_MS);
    }

    public void startBackgroundTimer(String bgImageUrl) {
        if (bgImageUrl != null) {
            startBackgroundTimer(Uri.parse(bgImageUrl));
        }
    }

    public void setBackgroundFrom(Video item) {
        // Selectively change background picture
    }

    public void onStart() {
        if (mBackgroundURI != null) {
            showBackground(mBackgroundURI.toString());
        } else if (mBackgroundColor != -1) {
            setBackgroundColor(mBackgroundColor);
        } else {
            setDefaultBackground();
        }
    }

    public void onStop() {
        mBackgroundManager.release();
    }

    public void onDestroy() {
        mHandler.removeCallbacks(mBackgroundTask);
        RxHelper.disposeActions(mDailyBackgroundAction);
        mBackgroundManager = null;
    }

    public void removeBackground() {
        mBackgroundManager.setDrawable(null);
    }

    public void setDefaultBackground() {
        mBackgroundManager.setDrawable(mDefaultBackground);
        showDailyBackground();
    }

    /**
     * Today's image replaces the plain theme color. Screens that set their own background (the player) are left alone.
     */
    private void showDailyBackground() {
        RxHelper.disposeActions(mDailyBackgroundAction);
        mDailyBackgroundAction = DailyBackground.load(mActivity, result -> {
            // No image: plain dark (the theme's color)
            if (mBackgroundManager == null || mBackgroundURI != null || mBackgroundColor != -1 || result.image == null) {
                return;
            }

            // HearthTube: under Hearth's shade (darker at the top and bottom), so text reads on any picture
            mBackgroundManager.setDrawable(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[] {
                    new android.graphics.drawable.BitmapDrawable(mActivity.getResources(), result.image),
                    androidx.core.content.ContextCompat.getDrawable(mActivity, R.drawable.hearth_wallpaper_shade)}));
        });
    }

    public void setBackgroundColor(int color) {
        mBackgroundColor = color;
        mBackgroundManager.setColor(color);
    }

    private class UpdateBackgroundTask implements Runnable {
        @Override
        public void run() {
            if (mBackgroundURI != null) {
                showBackground(mBackgroundURI.toString());
            }
        }
    }

    public BackgroundManager getBackgroundManager() {
        return mBackgroundManager;
    }

    public void showBackground(String uri) {
        View videoView = mActivity.findViewById(R.id.video_surface);

        if (videoView != null) {
            videoView.setVisibility(uri == null ? View.VISIBLE : View.INVISIBLE);
        }

        if (uri == null) {
            removeBackground();
            return;
        }

        int width = mMetrics.widthPixels;
        int height = mMetrics.heightPixels;

        RequestOptions options = ViewUtil.glideOptions()
                .centerCrop()
                .error(mDefaultBackground);

        Glide.with(mActivity)
                .asBitmap()
                .load(uri)
                .apply(options)
                .into(new SimpleTarget<Bitmap>(width, height) {
                    @Override
                    public void onResourceReady(
                            @NonNull Bitmap resource,
                            Transition<? super Bitmap> transition) {
                        mBackgroundManager.setBitmap(resource);
                    }
                });
    }

    public void showBackgroundColor(int colorResId) {
        View videoView = mActivity.findViewById(R.id.video_surface);

        if (videoView != null) {
            videoView.setVisibility(colorResId == -1 ? View.VISIBLE : View.INVISIBLE);
        }

        if (colorResId == -1) {
            removeBackground();
            return;
        }

        if (mBackgroundManager != null) {
            mBackgroundManager.setColor(ContextCompat.getColor(mActivity, colorResId));
        }
    }
}
