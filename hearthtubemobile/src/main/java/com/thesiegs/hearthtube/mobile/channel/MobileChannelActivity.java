package com.thesiegs.hearthtube.mobile.channel;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.thesiegs.hearthtube.mobile.ui.GridActivity;
import com.thesiegs.hearthtube.mobile.ui.ViewTeardown;

/** A channel's page: its videos, playlists and so on, as the shared {@link ChannelPresenter} loads them */
public class MobileChannelActivity extends GridActivity implements ChannelView {
    private ChannelPresenter mPresenter;

    @Override
    protected void onGridReady() {
        mPresenter = ChannelPresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    @Override
    protected void onVideoClicked(Video video) {
        mPresenter.onVideoItemClicked(video);
    }

    @Override
    protected void onVideoLongClicked(Video video) {
        mPresenter.onVideoItemLongClicked(video);
    }

    @Override
    protected void onScrollEnd(Video last) {
        mPresenter.onScrollEnd(last);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        ViewTeardown.destroyed(mPresenter, this);
    }

    /** Leaving by Back also forgets the channel, so the next one starts empty */
    @Override
    public void finish() {
        if (mPresenter != null) {
            mPresenter.onFinish();
        }
        super.finish();
    }

    @Override
    public void update(VideoGroup videoGroup) {
        showGroup(videoGroup);
    }

    @Override
    public void setPosition(int index) {
        scrollToPosition(index);
    }

    @Override
    public void showProgressBar(boolean show) {
        showProgress(show);
    }

    @Override
    public void clear() {
        clearGrid();
    }
}
