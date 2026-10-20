package com.thesiegs.hearthtube.mobile.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;

/**
 * A screen with HearthTube's wallpaper behind it ({@link HearthLook}). The player doesn't use this: video is on black.
 * Everything a subclass draws over the wallpaper must leave its own background out (or be see-through).
 */
public abstract class HearthActivity extends MotherActivity {
    private HearthLook.Backdrop mBackdrop;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBackdrop = new HearthLook.Backdrop(this);
    }

    /** The pill bars scroll sideways: a drag that starts near the left edge must not go back (the system's back does) */
    @Override
    protected void initEdgeSlide() {
    }

    @Override
    protected void onStart() {
        super.onStart();
        mBackdrop.start();
    }

    @Override
    protected void onStop() {
        mBackdrop.stop();
        super.onStop();
    }
}
