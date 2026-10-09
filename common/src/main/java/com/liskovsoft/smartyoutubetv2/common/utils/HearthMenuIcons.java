package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;

/**
 * HearthTube: an outlined icon before each label in the long-press menu, like Hearth's panel actions (icon, gap,
 * label). Matched by the label, so the menu's many builders stay as SmartTube has them. A label without an icon gets
 * an empty space of the same width, keeping the labels in one column.
 */
public final class HearthMenuIcons {
    private static final int ICON_SIZE_DP = 24;
    /** {label string, icon}; a label with %s matches by its text before the %s */
    private static final int[][] ICONS = {
            {R.string.dialog_add_to_playlist, R.drawable.ic_hearth_action_playlist_add},
            {R.string.add_video_to_new_playlist, R.drawable.ic_hearth_action_playlist_add},
            {R.string.create_playlist, R.drawable.ic_hearth_action_playlist_add},
            {R.string.save_playlist, R.drawable.ic_hearth_action_playlist_add},
            {R.string.add_video_to_watch_later, R.drawable.ic_hearth_menu_watch_later},
            {R.string.play_next, R.drawable.ic_hearth_action_queue},
            {R.string.add_to_playback_queue, R.drawable.ic_hearth_action_queue},
            {R.string.action_playback_queue, R.drawable.ic_hearth_action_queue},
            {R.string.playlist_order, R.drawable.ic_hearth_action_queue},
            {R.string.remove_from_playback_queue, R.drawable.ic_hearth_action_delete},
            {R.string.remove_from_history, R.drawable.ic_hearth_action_delete},
            {R.string.remove_from_subscriptions, R.drawable.ic_hearth_action_delete},
            {R.string.remove_playlist, R.drawable.ic_hearth_action_delete},
            {R.string.clear_history, R.drawable.ic_hearth_action_delete},
            {R.string.open_channel, R.drawable.ic_hearth_settings_account},
            {R.string.open_channel_uploads, R.drawable.ic_hearth_menu_library},
            {R.string.open_playlist, R.drawable.ic_hearth_menu_library},
            {R.string.open_comments, R.drawable.ic_hearth_action_comment},
            {R.string.action_video_info, R.drawable.ic_hearth_settings_about},
            {R.string.play_video, R.drawable.ic_hearth_settings_player},
            {R.string.play_from_start, R.drawable.ic_hearth_settings_player},
            {R.string.play_video_incognito, R.drawable.ic_hearth_settings_player},
            {R.string.return_to_background_video, R.drawable.ic_hearth_settings_player},
            {R.string.not_interested, R.drawable.ic_hearth_settings_block},
            {R.string.not_recommend_channel, R.drawable.ic_hearth_settings_block},
            {R.string.dialog_block_channel, R.drawable.ic_hearth_settings_block},
            {R.string.dialog_unblock_channel, R.drawable.ic_hearth_settings_block},
            {R.string.mark_as_watched, R.drawable.ic_hearth_action_done},
            {R.string.subscribe_to_channel, R.drawable.ic_hearth_action_bell},
            {R.string.unsubscribe_from_channel, R.drawable.ic_hearth_action_bell},
            {R.string.subscribe_unsubscribe_from_channel, R.drawable.ic_hearth_action_bell},
            {R.string.set_stream_reminder, R.drawable.ic_hearth_action_bell},
            {R.string.unset_stream_reminder, R.drawable.ic_hearth_action_bell},
            {R.string.pin_channel, R.drawable.ic_hearth_menu_pin},
            {R.string.pin_playlist, R.drawable.ic_hearth_menu_pin},
            {R.string.unpin_from_sidebar, R.drawable.ic_hearth_menu_pin},
            {R.string.rename_playlist, R.drawable.ic_hearth_menu_edit},
            {R.string.pause_history, R.drawable.ic_hearth_menu_watch_later},
            {R.string.resume_history, R.drawable.ic_hearth_menu_watch_later},
            // Broad "Add to %s" / "Remove from %s" last, after the labels they'd also match
            {R.string.dialog_add_to, R.drawable.ic_hearth_action_playlist_add},
            {R.string.dialog_remove_from, R.drawable.ic_hearth_action_delete},
    };

    private HearthMenuIcons() {
    }

    /** Puts the icons before the labels of the menu's buttons, just before it's shown */
    public static void apply(Context context, AppDialogPresenter dialog) {
        if (context == null || dialog == null) {
            return;
        }

        int size = Math.round(ICON_SIZE_DP * context.getResources().getDisplayMetrics().density);

        for (OptionItem item : dialog.getSingleButtons()) {
            if (!(item instanceof UiOptionItem) || item.getTitle() == null) {
                continue;
            }

            Drawable icon = find(context, item.getTitle().toString());
            if (icon == null) {
                icon = new ColorDrawable(0); // same width, so the labels line up
            }
            icon.setBounds(0, 0, size, size);
            ((UiOptionItem) item).setTitle(TextUtils.concat(Utils.icon(icon), "  ", item.getTitle()));
        }
    }

    private static Drawable find(Context context, String label) {
        for (int[] entry : ICONS) {
            String text = context.getString(entry[0]);
            int arg = text.indexOf('%');
            boolean matches = arg > 0 ? label.startsWith(text.substring(0, arg)) : label.equals(text);

            if (matches) {
                return ContextCompat.getDrawable(context, entry[1]);
            }
        }

        return null;
    }
}
