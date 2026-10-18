package com.thesiegs.hearthtube.mobile.channel;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.thesiegs.hearthtube.mobile.ui.GridActivity;

/** All of one channel's uploads, as the shared {@link ChannelUploadsPresenter} loads them */
public class MobileChannelUploadsActivity extends GridActivity implements ChannelUploadsView {
    private ChannelUploadsPresenter mPresenter;

    @Override
    protected void onGridReady() {
        mPresenter = ChannelUploadsPresenter.instance(this);
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

        if (mPresenter.getView() == this) {
            mPresenter.onViewDestroyed();
        }
    }

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
    public void showProgressBar(boolean show) {
        showProgress(show);
    }

    @Override
    public void clear() {
        clearGrid();
    }
}
