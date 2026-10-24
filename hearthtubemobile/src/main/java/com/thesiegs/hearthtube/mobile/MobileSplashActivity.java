package com.thesiegs.hearthtube.mobile;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.SplashPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.thesiegs.hearthtube.mobile.ui.ViewTeardown;

/**
 * The way into the app, as on the TV: the shared {@link SplashPresenter} starts the services, sets up the kids filters
 * (no Shorts, the AI channel list), reads the link or search the app was opened with, and then hands over to the
 * browse screen or the player. Nothing else may be the launcher, or those kids protections would never start.
 */
public class MobileSplashActivity extends MotherActivity implements SplashView {
    private Intent mNewIntent;
    private SplashPresenter mPresenter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mNewIntent = getIntent();

        mPresenter = SplashPresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    @Override
    protected void initDpi() {
    }

    @Override
    protected void initTheme() {
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        mNewIntent = intent;
        mPresenter.onViewInitialized();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ViewTeardown.destroyed(mPresenter, this);
    }

    @Override
    public Intent getNewIntent() {
        return mNewIntent;
    }

    @Override
    public void finishView() {
        finish();
    }
}
