package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu;

import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.HearthSections;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.HearthSections.TitledGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.BlockedChannelData;

import java.util.List;

/**
 * HearthTube: the long-press menu of a channel's circle in Subscriptions. It's SmartTube's own long-press menu
 * ({@link VideoMenuPresenter}) with the channel as its card, the way a channel found by search has it: Open channel,
 * Unsubscribe, Block channel, Pin channel to the menu, each as switched on under Settings › General › Context menu,
 * with Hearth's icons. In a kids profile, unsubscribing and blocking take the parent PIN (VideoMenuPresenter asks).
 */
public final class HearthChannelMenu {
    /** The channel left Subscriptions (unsubscribed, or blocked): its circle goes */
    public interface OnChannelGone {
        void onChannelGone(Video channel);
    }

    private HearthChannelMenu() {
    }

    /**
     * @return false: not a channel's row (All, a topic chip), nothing to show
     */
    public static boolean show(Context context, @Nullable TitledGroup row, OnChannelGone onGone) {
        MediaItem channel = row != null ? row.getChannel() : null;

        if (context == null || channel == null) {
            return false;
        }

        // A channel card: no video, no playlist, subscribed (it's in Subscriptions)
        Video video = new Video();
        video.title = channel.getTitle();
        video.author = channel.getTitle();
        video.cardImageUrl = channel.getCardImageUrl();
        video.bgImageUrl = channel.getBackgroundImageUrl();
        video.isSubscribed = true;
        video.channelId = row.getUploadsChannelId();

        MediaItem upload = video.channelId == null ? findOwnUpload(row.getMediaItems(), channel.getTitle()) : null;

        if (upload != null && upload.getChannelId() != null) {
            video.channelId = upload.getChannelId();
        }

        if (video.channelId != null) {
            showMenu(context, video, onGone);
        } else if (upload != null) {
            // The list of channels carries no ids: one of the channel's own videos says whose it is
            MessageHelpers.showMessage(context, R.string.wait_data_loading);
            MediaServiceManager.instance().loadMetadata(upload, metadata -> {
                video.channelId = metadata.getChannelId();

                if (video.channelId != null) {
                    showMenu(context, video, onGone);
                }
            });
        }

        return true;
    }

    private static void showMenu(Context context, Video channel, OnChannelGone onGone) {
        VideoMenuPresenter.instance(context).showMenu(channel, (item, action) -> {
            // Block and Unblock both report a removal: only a block takes the channel away
            boolean blocked = action == VideoMenuCallback.ACTION_REMOVE
                    && BlockedChannelData.instance(context).containsChannel(item.channelId, item.getAuthor());

            if (action == VideoMenuCallback.ACTION_UNSUBSCRIBE || blocked) {
                AppDialogPresenter.instance(context).closeDialog();
                onGone.onChannelGone(item);
            }
        });
    }

    /** The channel's own video in its uploads (not a collaboration another channel posted), else the first one */
    @Nullable
    private static MediaItem findOwnUpload(@Nullable List<MediaItem> uploads, String channelTitle) {
        if (uploads == null || uploads.isEmpty()) {
            return null;
        }

        for (MediaItem upload : uploads) {
            if (upload != null && HearthSections.isSameChannel(null, channelTitle, null, upload.getAuthor())) {
                return upload;
            }
        }

        return uploads.get(0);
    }
}
