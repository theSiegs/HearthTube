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
import com.liskovsoft.smartyoutubetv2.common.prefs.ParentBlockData;
import com.liskovsoft.smartyoutubetv2.common.prefs.ParentBlockData.Item;
import com.liskovsoft.smartyoutubetv2.common.utils.AiSlopList;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.KeywordFilter;
import com.liskovsoft.smartyoutubetv2.common.utils.ParentGate;
import com.liskovsoft.smartyoutubetv2.common.utils.KeywordMatcher;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * HearthTube: the blocked words list (Settings › General › Blocked words), and the long-press menu's "Hide videos with
 * words from this title…", which picks words off a title so nobody has to type them with a remote. In a kids profile
 * both take the parent PIN. What a blocked word does is {@link KeywordFilter}. Also the switch for "Likely AI" labels,
 * which sits under Blocked words.
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
                option -> show(context)));
        settingsPresenter.appendSingleButton(UiOptionItem.from(context.getString(R.string.hidden_videos),
                option -> showHiddenVideos(context)));
    }

    /**
     * The long-press menu's last hiding item: everything hidden or blocked in this profile in one place (no PIN: a kid who
     * hid something can undo it). Videos, creators and words each open their list.
     */
    public static void appendManageButton(Context context, AppDialogPresenter dialog) {
        if (context == null || !MainUIData.instance(context).isMenuItemEnabled(MainUIData.MENU_ITEM_BLOCK_CHANNEL)) {
            return;
        }

        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.manage_hidden),
                option -> showManager(context)));
    }

    private static void showManager(Context context) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        ParentBlockData parent = ParentBlockData.instance(context);
        int videos = com.liskovsoft.smartyoutubetv2.common.prefs.HiddenVideoData.instance(context).getVideos().size()
                + parent.getItems(Item.VIDEO).size();
        int creators = com.liskovsoft.smartyoutubetv2.common.prefs.BlockedChannelData.instance(context).getChannelCount()
                + parent.getItems(Item.CHANNEL).size();
        int words = KeywordFilter.getWords(context).size() + parent.getItems(Item.WORD).size();

        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.hidden_videos) + " (" + videos + ")",
                option -> showHiddenVideos(context)));
        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.blocked_creators) + " (" + creators + ")",
                option -> showBlockedCreators(context)));
        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.blocked_words) + " (" + words + ")",
                option -> show(context)));

        sOnTop = dialog.isDialogShown();
        dialog.showDialog(context.getString(R.string.manage_hidden), () -> onClosed(context));
    }

    /** Creators not shown: each a button that shows it again */
    private static void showBlockedCreators(Context context) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        com.liskovsoft.smartyoutubetv2.common.prefs.BlockedChannelData blocked =
                com.liskovsoft.smartyoutubetv2.common.prefs.BlockedChannelData.instance(context);

        for (android.util.Pair<String, String> entry : blocked.getChannelIdsWithNames()) {
            String name = entry.second == null || entry.second.isEmpty() ? entry.first : entry.second;
            dialog.appendSingleButton(UiOptionItem.from(
                    SettingsMenuPresenter.withIcon(context, R.drawable.ic_hearth_action_delete, name),
                    // A parent may have blocked it: in a kids profile, showing a creator again takes the PIN (not one the kid blocked)
                    option -> AppDialogUtil.showConfirmationDialog(context, context.getString(R.string.hidden_video_show, name),
                            () -> ParentBlockData.unblockGate(context, Item.CHANNEL, entry.first != null ? entry.first : entry.second, () -> {
                                blocked.removeChannel(entry.first, entry.second);
                                sChanged = true;
                                reopenCreators(context);
                            }))));
        }

        appendParentRows(context, dialog, Item.CHANNEL);

        dialog.appendSingleButton(UiOptionItem.from(
                context.getString(blocked.isEmpty() && ParentBlockData.instance(context).getItems(Item.CHANNEL).isEmpty()
                        ? R.string.blocked_creators_empty : R.string.hidden_videos_hint)));

        sOnTop = dialog.isDialogShown();
        dialog.showDialog(context.getString(R.string.blocked_creators), () -> onClosed(context));
    }

    private static void reopenCreators(Context context) {
        // The panels stack (menu, manager, list, question): close them all rather than guess which one is on top
        AppDialogPresenter.instance(context).closeDialog();
        MessageHelpers.showMessage(context, R.string.shown_again);
    }

    /** Settings › General: the videos hidden one by one, each a button that shows it again */
    private static void showHiddenVideos(Context context) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        com.liskovsoft.smartyoutubetv2.common.prefs.HiddenVideoData hidden =
                com.liskovsoft.smartyoutubetv2.common.prefs.HiddenVideoData.instance(context);

        for (android.util.Pair<String, String> entry : hidden.getVideos()) {
            String title = entry.second == null || entry.second.isEmpty() ? entry.first : entry.second;
            dialog.appendSingleButton(UiOptionItem.from(
                    SettingsMenuPresenter.withIcon(context, R.drawable.ic_hearth_action_delete, title),
                    option -> AppDialogUtil.showConfirmationDialog(context, context.getString(R.string.hidden_video_show, title),
                            () -> ParentBlockData.unblockGate(context, Item.VIDEO, entry.first, () -> {
                                hidden.unhide(entry.first);
                                sChanged = true;
                                reopenHidden(context);
                            }))));
        }

        appendParentRows(context, dialog, Item.VIDEO);

        dialog.appendSingleButton(UiOptionItem.from(
                context.getString(hidden.isEmpty() && ParentBlockData.instance(context).getItems(Item.VIDEO).isEmpty()
                        ? R.string.hidden_videos_empty : R.string.hidden_videos_hint)));

        sOnTop = dialog.isDialogShown();
        dialog.showDialog(context.getString(R.string.hidden_videos), () -> onClosed(context));
    }

    private static void reopenHidden(Context context) {
        // The panels stack (menu, manager, list, question): close them all rather than guess which one is on top
        AppDialogPresenter.instance(context).closeDialog();
        MessageHelpers.showMessage(context, R.string.shown_again);
    }

    /**
     * Settings › General, under Blocked words: the "Likely AI" label on videos from channels on AiSList
     * ({@link com.liskovsoft.smartyoutubetv2.common.utils.AiSlopList}). On by default; kids profiles hide those videos
     * instead.
     */
    public static void appendAiLabelsSwitch(Context context, AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(context.getString(R.string.ai_labels),
                context.getString(R.string.ai_labels_desc),
                option -> {
                    AiSlopList.setLabelsEnabled(context, option.isSelected());
                    BrowsePresenter.instance(context).refresh(false);
                },
                AiSlopList.isLabelsEnabled(context)));
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
                    // A parent may have added it: in a kids profile, removing a word takes the PIN (not one the kid added)
                    option -> AppDialogUtil.showConfirmationDialog(context, context.getString(R.string.blocked_words_remove, word),
                            () -> ParentBlockData.unblockGate(context, Item.WORD, word, () -> {
                                KeywordFilter.remove(context, word);
                                sChanged = true;
                                reopen(context);
                            }))));
        }

        appendParentRows(context, dialog, Item.WORD);

        dialog.appendSingleButton(UiOptionItem.from(
                context.getString(words.isEmpty() && ParentBlockData.instance(context).getItems(Item.WORD).isEmpty()
                        ? R.string.blocked_words_empty : R.string.blocked_words_hint)));

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
                        ParentBlockData.kidMade(context, Item.WORD, word);
                        sChanged = true;
                    } else {
                        MessageHelpers.showMessage(context, R.string.blocked_words_already, word.trim());
                    }

                    return true;
                },
                () -> Utils.post(() -> show(context)));
    }

    /**
     * What a parent blocked, below the viewer's own, and locked: one set here (a parent's PIN) comes back after the PIN, one
     * from Hearth is taken back there.
     */
    private static void appendParentRows(Context context, AppDialogPresenter dialog, char kind) {
        for (Item item : ParentBlockData.instance(context).getItems(kind)) {
            dialog.appendSingleButton(UiOptionItem.from(
                    context.getString(R.string.parent_blocked_row, item.label),
                    option -> {
                        if (item.source != Item.LOCAL) {
                            MessageHelpers.showMessage(context, R.string.parent_blocked_in_hearth);
                            return;
                        }

                        AppDialogUtil.showConfirmationDialog(context, context.getString(R.string.hidden_video_show, item.label),
                                () -> ParentGate.run(context, () -> {
                                    ParentBlockData.instance(context).removeLocal(kind, item.id);
                                    sChanged = true;
                                    AppDialogPresenter.instance(context).closeDialog();
                                    MessageHelpers.showMessage(context, R.string.shown_again);
                                }));
                    }));
        }
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
                option -> showPicker(context, video, callback))); // no PIN: hiding takes none
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

        // In a kids profile a word already blocked isn't offered: unticking it would undo what a parent may have set
        boolean kids = ParentGate.isKidsProfile(context);

        for (String word : words) {
            if (!kids || !KeywordFilter.contains(context, word)) {
                options.add(pickOption(context, word, word, added));
            }
        }

        if (KeywordMatcher.isUsable(channel) && !isAmong(channel, words) && (!kids || !KeywordFilter.contains(context, channel))) {
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
                boolean put = KeywordFilter.add(context, word);
                added[0] |= put;
                if (put) ParentBlockData.kidMade(context, Item.WORD, word);
            } else {
                KeywordFilter.remove(context, word);
                ParentBlockData.instance(context).unmarkKidMade(Item.WORD, word);
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
