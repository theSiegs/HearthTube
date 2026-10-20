package com.thesiegs.hearthtube.mobile.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;

/**
 * The phone's and tablet's list of videos, shared by every screen that shows some (home, search, a channel): cards in
 * as many columns as the width allows (one on a phone, more on a tablet or a phone turned sideways), row titles as
 * headers, a spinner, a message, and a nudge for the next page near the end. The presenters feed it {@link VideoGroup}s
 * exactly as they feed the TV's rows.
 */
public class VideoGrid extends FrameLayout {
    /** Columns by width: a 600dp tablet gets two, a 900dp one three */
    private static final int COLUMN_DP = 300;
    private static final int PAGE_AHEAD = 4;

    public interface Listener {
        void onClick(Video video);
        void onLongClick(Video video);
        /** Near the end of the list: {@code last} is its last video */
        void onScrollEnd(Video last);
    }

    /** The sub-pills of the section on show changed (or its picked row did) */
    public interface StripListener {
        void onStrip(java.util.List<VideoRows.Pill> pills, int selected);
    }

    /** The list scrolled: dy is the way it went (positive is down the list); atTop when it is back at its start */
    public interface ScrollWatcher {
        void onScrolled(int dy, boolean atTop);
    }

    private final VideoRows mRows = new VideoRows();
    private StripListener mStripListener;
    private ScrollWatcher mScrollWatcher;
    private final VideoAdapter mAdapter;
    private final GridLayoutManager mLayout;
    private final RecyclerView mList;
    private final ProgressBar mProgress;
    private final TextView mMessage;
    private final TextView mAction;

    public VideoGrid(Context context, Listener listener) {
        super(context);

        mAdapter = new VideoAdapter(new VideoAdapter.Listener() {
            @Override
            public void onClick(Video video) {
                listener.onClick(video);
            }

            @Override
            public void onLongClick(Video video) {
                listener.onLongClick(video);
            }

            @Override
            public void onAuthorClick(Video video) {
                MediaServiceManager.chooseChannelPresenter(context, video);
            }
        });

        mLayout = new GridLayoutManager(context, columns());
        mLayout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return mAdapter.isHeader(position) ? mLayout.getSpanCount() : 1;
            }
        });

        mList = new RecyclerView(context);
        mList.setLayoutManager(mLayout);
        mList.setAdapter(mAdapter);
        mList.setClipToPadding(false);
        mList.setPadding(dp(8), 0, dp(8), dp(16));
        mList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (mScrollWatcher != null) {
                    mScrollWatcher.onScrolled(dy, !recyclerView.canScrollVertically(-1));
                }

                if (dy > 0 && mLayout.findLastVisibleItemPosition() >= mAdapter.getItemCount() - PAGE_AHEAD) {
                    Video last = mAdapter.lastVideo();
                    if (last != null) {
                        listener.onScrollEnd(last);
                    }
                }
            }
        });
        addView(mList, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mProgress = new ProgressBar(context);
        mProgress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(HearthLook.accent(context)));
        mProgress.setVisibility(GONE);
        addView(mProgress, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        mMessage = new TextView(context);
        // The TV's empty-page card: the message on black at 55%
        mMessage.setTextColor(Color.WHITE);
        mMessage.setTextSize(16);
        mMessage.setGravity(Gravity.CENTER);
        mMessage.setPadding(dp(24), dp(24), dp(24), dp(24));
        HearthLook.emptyCard(mMessage);
        mMessage.setVisibility(GONE);
        LayoutParams messageParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        messageParams.setMargins(dp(24), 0, dp(24), 0);
        addView(mMessage, messageParams);

        mAction = new TextView(context);
        mAction.setTextSize(15);
        mAction.setPadding(dp(28), dp(12), dp(28), dp(12));
        HearthLook.glassButton(mAction);
        mAction.setVisibility(GONE);
        LayoutParams actionParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        actionParams.topMargin = dp(120);
        addView(mAction, actionParams);
    }

    /** Applies a group the way its action says (replace, append, remove...) */
    public void update(VideoGroup group) {
        showMessage(null);
        mRows.update(group);
        refresh();
    }

    public void clear() {
        mRows.clear();
        refresh();
    }

    /** What the list shows (the picked row with a strip, else everything), and the strip's pills */
    private void refresh() {
        mAdapter.setItems(mRows.shown(mStripListener != null));

        if (mStripListener != null) {
            mStripListener.onStrip(mRows.hasStrip() ? mRows.pills() : java.util.Collections.emptyList(), mRows.selectedIndex());
        }
    }

    public void setStripListener(StripListener listener) {
        mStripListener = listener;
    }

    public void setScrollWatcher(ScrollWatcher watcher) {
        mScrollWatcher = watcher;
    }

    /** A sub-pill was picked: its row, from its start */
    public void selectRow(int index) {
        mRows.select(index);
        refresh();
        mList.scrollToPosition(0);
    }

    /** A channel left Subscriptions: its pill goes, and its videos from the other rows */
    public void removeChannel(Object tag, VideoRows.VideoMatcher channelVideos) {
        mRows.removeChannel(tag, channelVideos);
        refresh();
    }

    /** The header floats over the list: the list starts below it */
    public void setTopInset(int px) {
        if (mList.getPaddingTop() == px) {
            return;
        }

        boolean atStart = !mList.canScrollVertically(-1);
        mList.setPadding(mList.getPaddingLeft(), px, mList.getPaddingRight(), mList.getPaddingBottom());

        // The first card stays where it was unless told: at the start of the list it moves down below the header
        if (atStart) {
            mList.post(() -> mList.scrollToPosition(0));
        }
    }

    public boolean isEmpty() {
        return mAdapter.getItemCount() == 0;
    }

    public void showProgress(boolean show) {
        mProgress.setVisibility(show ? VISIBLE : GONE);
    }

    public boolean isProgressShowing() {
        return mProgress.getVisibility() == VISIBLE;
    }

    /** Text over the list (an error, "nothing here"); null or empty hides it */
    public void showMessage(@Nullable CharSequence message) {
        showMessage(message, null, null);
    }

    /** Like {@link #showMessage(CharSequence)}, with a button under it (such as "Sign in") when there is something to do */
    public void showMessage(@Nullable CharSequence message, @Nullable CharSequence actionText, @Nullable Runnable action) {
        mMessage.setText(message);
        mMessage.setVisibility(message != null && message.length() > 0 ? VISIBLE : GONE);

        boolean hasAction = message != null && message.length() > 0 && actionText != null && actionText.length() > 0 && action != null;
        mAction.setText(hasAction ? actionText : null);
        mAction.setOnClickListener(hasAction ? v -> action.run() : null);
        mAction.setVisibility(hasAction ? VISIBLE : GONE);
    }

    public void scrollToPosition(int position) {
        mList.scrollToPosition(Math.max(0, position));
    }

    public void scrollTo(Video video) {
        int at = mAdapter.indexOf(video);

        if (at >= 0) {
            mList.scrollToPosition(at);
        }
    }

    /** Turned sideways, or unfolded: the number of columns follows the width */
    public void onConfigurationChanged() {
        mLayout.setSpanCount(columns());
    }

    private int columns() {
        return Math.max(1, (int) (getResources().getConfiguration().screenWidthDp / (float) COLUMN_DP));
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
