package com.thesiegs.hearthtube.mobile.playback;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.thesiegs.hearthtube.mobile.ui.HearthLook;
import com.thesiegs.hearthtube.mobile.ui.VideoGrid;

/**
 * What YouTube's app shows under the video: its title and views, the channel (its picture and name open its page), the
 * player's buttons, and, for adults, Up next (the TV's suggestion rows, as the phone's cards). Two parts the player
 * places by the screen: one under the other upright, side by side on a tablet turned sideways, neither when a phone
 * shows the video full screen. A kids profile has no Up next: what a kid watches next is chosen, not offered.
 */
final class WatchPanel {
    interface Listener {
        void onSuggestionClick(Video video);
        void onSuggestionLongClick(Video video);
        void onSuggestionScrollEnd(Video last);
    }

    private final Context mContext;
    private final LinearLayout mDetails;
    private final TextView mTitle;
    private final TextView mMeta;
    private final LinearLayout mChannel;
    private final ImageView mAvatar;
    private final TextView mAuthor;
    private final LinearLayout mActionsSlot;
    private final VideoGrid mUpNext;
    private final boolean mHasUpNext;
    private Video mVideo;
    private String mAvatarUrl;

    WatchPanel(Context context, boolean hasUpNext, Listener listener) {
        mContext = context;
        mHasUpNext = hasUpNext;

        mDetails = new LinearLayout(context);
        mDetails.setOrientation(LinearLayout.VERTICAL);
        mDetails.setPadding(dp(12), dp(10), dp(12), dp(4));
        mDetails.setBackgroundColor(Color.BLACK);

        mTitle = new TextView(context);
        mTitle.setTextColor(Color.WHITE);
        mTitle.setTextSize(18);
        mTitle.setTypeface(HearthLook.medium());
        mTitle.setMaxLines(3);
        mTitle.setEllipsize(TextUtils.TruncateAt.END);
        mDetails.addView(mTitle);

        mMeta = new TextView(context);
        mMeta.setTextColor(0xFFAAAAAA);
        mMeta.setTextSize(13);
        mMeta.setMaxLines(2);
        mMeta.setEllipsize(TextUtils.TruncateAt.END);
        mMeta.setPadding(0, dp(4), 0, 0);
        mDetails.addView(mMeta);

        // The channel: its picture (once the player has it) and name, a tap opens its page
        mChannel = new LinearLayout(context);
        mChannel.setOrientation(LinearLayout.HORIZONTAL);
        mChannel.setGravity(Gravity.CENTER_VERTICAL);
        mChannel.setPadding(0, dp(10), 0, dp(6));
        mAvatar = new ImageView(context);
        mAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mAvatar.setBackgroundColor(0xFF303030);
        mAvatar.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        mAvatar.setClipToOutline(true);
        mChannel.addView(mAvatar, new LinearLayout.LayoutParams(dp(36), dp(36)));
        mAuthor = new TextView(context);
        mAuthor.setTextColor(Color.WHITE);
        mAuthor.setTextSize(15);
        mAuthor.setTypeface(HearthLook.medium());
        mAuthor.setSingleLine(true);
        mAuthor.setEllipsize(TextUtils.TruncateAt.END);
        mAuthor.setPadding(dp(10), 0, 0, 0);
        mChannel.addView(mAuthor, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        mChannel.setOnClickListener(v -> {
            if (mVideo != null && ChannelPresenter.canOpenChannel(mVideo)) {
                MediaServiceManager.chooseChannelPresenter(mContext, mVideo);
            }
        });
        mDetails.addView(mChannel);

        // The player's buttons sit here while the panel shows (else over the video)
        mActionsSlot = new LinearLayout(context);
        mActionsSlot.setOrientation(LinearLayout.VERTICAL);
        mDetails.addView(mActionsSlot);

        mUpNext = new VideoGrid(context, new VideoGrid.Listener() {
            @Override
            public void onClick(Video video) {
                listener.onSuggestionClick(video);
            }

            @Override
            public void onLongClick(Video video) {
                listener.onSuggestionLongClick(video);
            }

            @Override
            public void onScrollEnd(Video last) {
                listener.onSuggestionScrollEnd(last);
            }
        });
        mUpNext.setFixedColumns(1);
        mUpNext.setVisibility(hasUpNext ? View.VISIBLE : View.GONE);
    }

    View details() {
        return mDetails;
    }

    /** Up next; GONE in a kids profile */
    View upNext() {
        return mUpNext;
    }

    boolean hasUpNext() {
        return mHasUpNext;
    }

    LinearLayout actionsSlot() {
        return mActionsSlot;
    }

    void setVideo(@Nullable Video video) {
        if (video == null) {
            return;
        }

        boolean sameChannel = mVideo != null && TextUtils.equals(mVideo.channelId, video.channelId);
        mVideo = video;
        setTitle(video.getTitleFull() != null ? video.getTitleFull() : video.getTitle());
        CharSequence meta = video.getSecondTitleFull();
        mMeta.setText(meta);
        mMeta.setVisibility(TextUtils.isEmpty(meta) ? View.GONE : View.VISIBLE);
        String author = video.getAuthor();
        mAuthor.setText(author);
        mChannel.setVisibility(TextUtils.isEmpty(author) ? View.GONE : View.VISIBLE);

        if (!sameChannel) {
            setChannelIcon(null); // the next channel's picture comes with its details
        }
    }

    void setTitle(@Nullable String title) {
        mTitle.setText(title != null ? title : "");
    }

    void setChannelIcon(@Nullable String url) {
        if (TextUtils.equals(url, mAvatarUrl)) {
            return;
        }

        mAvatarUrl = url;

        if (url == null) {
            Glide.with(mAvatar).clear(mAvatar);
        } else {
            Glide.with(mAvatar).load(url).into(mAvatar);
        }
    }

    // Up next: the suggestion groups the presenter sends, as the TV's rows

    void updateSuggestions(VideoGroup group) {
        if (mHasUpNext) {
            mUpNext.update(group);
        }
    }

    void clearSuggestions() {
        mUpNext.clear();
    }

    boolean isSuggestionsEmpty() {
        return mUpNext.isEmpty();
    }

    void scrollUpNextToTop() {
        mUpNext.scrollToPosition(0);
    }

    private int dp(int dp) {
        return HearthLook.dp(mContext, dp);
    }
}
