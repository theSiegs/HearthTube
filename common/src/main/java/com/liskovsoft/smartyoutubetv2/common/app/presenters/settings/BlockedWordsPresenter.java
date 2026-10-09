package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.KeywordFilter;
import com.liskovsoft.smartyoutubetv2.common.utils.KeywordMatcher;
import com.liskovsoft.smartyoutubetv2.common.utils.ParentGate;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * HearthTube: the blocked words list (Settings › General › Blocked words), and the long-press menu's "Hide videos with
 * words from this title…", which picks words off a title so nobody has to type them with a remote. In a kids profile
 * both take the parent PIN. What a blocked word does is {@link KeywordFilter}.
 */
public final class BlockedWordsPresenter {
    private static final long PANEL_CLOSE_MS = 300;
    /** The list opened on top of another panel (General), not on its own (after a PIN, or after typing a word) */
    private static boolean sOnTop;
    /** The list changed since the panels opened: the page underneath loads again when they close */
    private static boolean sChanged;

    private BlockedWordsPresenter() {
    }

    /** Settings › General, under Hide content */
    public static void appendSettingsButton(Context context, AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(context.getString(R.string.blocked_words),
                option -> ParentGate.run(context, () -> show(context))));
    }

    /** Add a word or phrase, then the words, each a button that removes it */
    public static void show(Context context) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        List<String> words = KeywordFilter.getWords(context);

        dialog.appendSingleButton(UiOptionItem.from(
                SettingsMenuPresenter.withIcon(context, R.drawable.ic_hearth_action_add, context.getString(R.string.blocked_words_add)),
                option -> showAdd(context)));

        for (String word : words) {
            dialog.appendSingleButton(UiOptionItem.from(
                    SettingsMenuPresenter.withIcon(context, R.drawable.ic_hearth_action_delete, word),
                    option -> AppDialogUtil.showConfirmationDialog(context, context.getString(R.string.blocked_words_remove, word), () -> {
                        KeywordFilter.remove(context, word);
                        sChanged = true;
                        reopen(context);
                    })));
        }

        dialog.appendSingleButton(UiOptionItem.from(
                context.getString(words.isEmpty() ? R.string.blocked_words_empty : R.string.blocked_words_hint)));

        sOnTop = dialog.isDialogShown();
        dialog.showDialog(context.getString(R.string.blocked_words), () -> onClosed(context));
    }

    /** The keyboard shows over the page, not the panel: the panel closes, and opens again once the word is in */
    private static void showAdd(Context context) {
        AppDialogPresenter.instance(context).closeDialog();

        SimpleEditDialog.show(
                context,
                context.getString(R.string.blocked_words_add),
                context.getString(R.string.blocked_words_add_hint),
                null,
                word -> {
                    if (!KeywordMatcher.isUsable(word)) {
                        MessageHelpers.showMessage(context, R.string.blocked_words_no_letters);
                        return false;
                    }

                    if (KeywordFilter.add(context, word)) {
                        sChanged = true;
                    } else {
                        MessageHelpers.showMessage(context, R.string.blocked_words_already, word.trim());
                    }

                    return true;
                },
                () -> Utils.post(() -> show(context)));
    }

    /** The list again, as it is now (a panel can't change once shown) */
    private static void reopen(Context context) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);

        if (sOnTop) {
            dialog.goBack();
            show(context);
        } else {
            dialog.closeDialog();
            Utils.postDelayed(() -> show(context), PANEL_CLOSE_MS);
        }
    }

    private static void onClosed(Context context) {
        if (sChanged) {
            sChanged = false;
            BrowsePresenter.instance(context).refresh(false);
        }
    }

    /**
     * The long-press menu, right after Block the channel, and shown with it (Settings › General › Context menu).
     */
    public static void appendMenuButton(Context context, AppDialogPresenter dialog, Video video, VideoMenuCallback callback) {
        if (context == null || video == null || video.isChapter || !video.hasVideo()
                || !MainUIData.instance(context).isMenuItemEnabled(MainUIData.MENU_ITEM_BLOCK_CHANNEL)
                || KeywordMatcher.splitWords(video.getTitle()).isEmpty()) {
            return;
        }

        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.blocked_words_from_title),
                option -> ParentGate.run(context, () -> showPicker(context, video, callback))));
    }

    /**
     * The title's words and the channel name, ticked if blocked already. Each tick or untick counts at once, like any
     * setting; the video (and others like it in its row) goes when the panel closes.
     */
    private static void showPicker(Context context, Video video, VideoMenuCallback callback) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        List<OptionItem> options = new ArrayList<>();
        List<String> words = KeywordMatcher.splitWords(video.getTitle());
        String channel = video.getAuthor();
        boolean[] added = {false};

        for (String word : words) {
            options.add(pickOption(context, word, word, added));
        }

        if (KeywordMatcher.isUsable(channel) && !isAmong(channel, words)) {
            options.add(pickOption(context, context.getString(R.string.blocked_words_channel, channel), channel, added));
        }

        dialog.appendCheckedCategory(context.getString(R.string.blocked_words_pick), options);
        dialog.showDialog(context.getString(R.string.blocked_words_pick), () -> {
            if (added[0]) {
                hideMatching(context, video, callback);
            }
        });
    }

    private static OptionItem pickOption(Context context, String label, String word, boolean[] added) {
        return UiOptionItem.from(label, option -> {
            if (option.isSelected()) {
                added[0] |= KeywordFilter.add(context, word);
            } else {
                KeywordFilter.remove(context, word);
            }
        }, KeywordFilter.contains(context, word));
    }

    /** Takes the video, and the ones like it in the same row, off the screen now; the rest go as pages load */
    private static void hideMatching(Context context, Video video, VideoMenuCallback callback) {
        List<Video> hidden = new ArrayList<>();
        VideoGroup group = video.getGroup();

        if (group != null && !group.isEmpty()) {
            for (Video item : group.getVideos()) {
                if (item != video && KeywordFilter.isHidden(item)) {
                    hidden.add(item);
                }
            }
        }

        if (KeywordFilter.isHidden(video)) {
            hidden.add(video);
        }

        if (callback != null) {
            for (Video item : hidden) {
                callback.onItemAction(item, VideoMenuCallback.ACTION_REMOVE);
            }
        }

        MessageHelpers.showMessage(context, R.string.blocked_words_hidden);
    }

    private static boolean isAmong(String word, List<String> words) {
        for (String other : words) {
            if (KeywordMatcher.isSame(word, other)) {
                return true;
            }
        }

        return false;
    }
}
