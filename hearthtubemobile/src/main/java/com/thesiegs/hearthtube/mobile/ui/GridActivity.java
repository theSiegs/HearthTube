package com.thesiegs.hearthtube.mobile.ui;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.thesiegs.hearthtube.mobile.R;

/**
 * A screen that is a list of videos and nothing else (a channel, its uploads): the grid fills it, and the kid gate
 * applies (nothing YouTube lists signed out is shown to a kid). Subclasses connect it to their presenter.
 */
public abstract class GridActivity extends HearthActivity {
    private VideoGrid mGrid;

    /** The screen is up: attach to the presenter (setView, onViewInitialized) */
    protected abstract void onGridReady();

    protected abstract void onVideoClicked(Video video);

    protected abstract void onVideoLongClicked(Video video);

    protected abstract void onScrollEnd(Video last);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        mGrid = new VideoGrid(this, new VideoGrid.Listener() {
            @Override
            public void onClick(Video video) {
                onVideoClicked(video);
            }

            @Override
            public void onLongClick(Video video) {
                KidMenu.longPress(GridActivity.this, () -> onVideoLongClicked(video));
            }

            @Override
            public void onScrollEnd(Video last) {
                GridActivity.this.onScrollEnd(last);
            }
        });
        // A channel has several shelves (Videos, Playlists, Live): pills to pick one, as on the home screen
        root.addView(new SubPillStrip(this, mGrid, null), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(mGrid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        onGridReady();
    }

    @Override
    protected void initDpi() {
    }

    @Override
    protected void initTheme() {
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mGrid.onConfigurationChanged();
    }

    protected void showGroup(VideoGroup group) {
        if (KidGate.isClosed(this)) {
            mGrid.clear();
            mGrid.showMessage(getString(R.string.gated_title));
            return;
        }

        mGrid.update(group);
    }

    protected void clearGrid() {
        mGrid.clear();
    }

    protected void showProgress(boolean show) {
        mGrid.showProgress(show);
    }

    protected void scrollToPosition(int position) {
        mGrid.scrollToPosition(position);
    }
}
