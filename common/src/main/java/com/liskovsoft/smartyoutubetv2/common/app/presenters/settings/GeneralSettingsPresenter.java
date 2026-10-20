package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuManager;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuProvider;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.NetworkData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.common.proxy.ProxyManager;
import com.liskovsoft.smartyoutubetv2.common.proxy.WebProxyDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class GeneralSettingsPresenter extends BasePresenter<Void> {
    private final GeneralData mGeneralData;
    private final PlayerData mPlayerData;
    private final PlayerTweaksData mPlayerTweaksData;
    private final MainUIData mMainUIData;
    private final MediaServiceData mMediaServiceData;
    private final NetworkData mNetworkData;
    private final SidebarService mSidebarService;
    private boolean mRestartApp;
    private final Runnable mOnFinish = () -> {
        if (mRestartApp) {
            mRestartApp = false;
            MessageHelpers.showLongMessage(getContext(), R.string.msg_restart_app);
        }
    };

    private GeneralSettingsPresenter(Context context) {
        super(context);
        mGeneralData = GeneralData.instance(context);
        mPlayerData = PlayerData.instance(context);
        mPlayerTweaksData = PlayerTweaksData.instance(context);
        mMainUIData = MainUIData.instance(context);
        mMediaServiceData = MediaServiceData.instance();
        mNetworkData = NetworkData.instance(context);
        mSidebarService = SidebarService.instance(context);
    }

    public static GeneralSettingsPresenter instance(Context context) {
        return new GeneralSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendBootToSection(settingsPresenter);
        // HearthTube: which sections show is Edit menu and tabs, in the side menu
        //appendEnabledSections(settingsPresenter);
        appendContextMenuItemsCategory(settingsPresenter);
        appendHideContent(settingsPresenter);
        BlockedWordsPresenter.appendSettingsButton(getContext(), settingsPresenter); // HearthTube
        BlockedWordsPresenter.appendAiLabelsSwitch(getContext(), settingsPresenter); // HearthTube
        appendAppExitCategory(settingsPresenter);
        appendBackgroundPlaybackCategory(settingsPresenter);
        appendScreenDimmingCategory(settingsPresenter);
        appendHistoryCategory(settingsPresenter);
        appendMiscCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.settings_general), mOnFinish);
    }

    private void appendHideContent(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_mixes),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_MIXES, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_MIXES)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_watched_from_watch_later),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_WATCHED_WATCH_LATER, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_WATCHED_WATCH_LATER)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_watched_from_home),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_WATCHED_HOME, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_WATCHED_HOME)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_watched_from_subscriptions),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_WATCHED_SUBSCRIPTIONS, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_WATCHED_SUBSCRIPTIONS)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_streams),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_STREAMS_SUBSCRIPTIONS, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_STREAMS_SUBSCRIPTIONS)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_upcoming),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_UPCOMING_SUBSCRIPTIONS, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_UPCOMING_SUBSCRIPTIONS)));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.hide_unwanted_content), options);
    }

    private void appendContextMenuItemsCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Map<Long, Integer> menuNames = getMenuNames();

        for (Long menuItem : mMainUIData.getMenuItemsOrdered()) {
            Integer nameResId = menuNames.get(menuItem);

            if (nameResId == null) {
                continue;
            }

            options.add(UiOptionItem.from(getContext().getString(nameResId), optionItem -> {
                if (optionItem.isSelected()) {
                    mMainUIData.setMenuItemEnabled(menuItem);
                    showMenuItemOrderDialog(menuItem);
                } else {
                    mMainUIData.setMenuItemDisabled(menuItem);
                }
            }, mMainUIData.isMenuItemEnabled(menuItem)));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.context_menu), options);
    }

    private void appendContextMenuSortingCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Map<Long, Integer> menuNames = getMenuNames();

        for (Long menuItem : mMainUIData.getMenuItemsOrdered()) {
            Integer nameResId = menuNames.get(menuItem);

            if (nameResId == null || !mMainUIData.isMenuItemEnabled(menuItem)) {
                continue;
            }

            options.add(UiOptionItem.from(getContext().getString(nameResId), optionItem ->
                    showMenuItemOrderDialog(menuItem), false));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.context_menu_sorting), options);
    }

    private void showMenuItemOrderDialog(Long menuItem) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());

        List<OptionItem> options = new ArrayList<>();

        Map<Long, Integer> menuNames = getMenuNames();

        Integer currentNameResId = menuNames.get(menuItem);

        if (currentNameResId == null) {
            return;
        }

        List<Long> menuItemsOrdered = mMainUIData.getMenuItemsOrdered();
        int size = menuItemsOrdered.size();
        int currentIndex = mMainUIData.getMenuItemIndex(menuItem);
        int counter = 0;

        for (int i = 0; i < size; i++) {
            Long item = menuItemsOrdered.get(i);
            Integer nameResId = menuNames.get(item);

            if (nameResId == null || !mMainUIData.isMenuItemEnabled(item)) {
                continue;
            }

            final int index = i;
            options.add(UiOptionItem.from((counter + 1) + " " + getContext().getString(nameResId), optionItem -> {
                if (optionItem.isSelected()) {
                    mMainUIData.setMenuItemIndex(index, menuItem);
                    dialog.goBack();
                }
            }, currentIndex == i));
            counter++;
        }

        String itemName = getContext().getString(currentNameResId);
        dialog.appendRadioCategory(getContext().getString(R.string.item_postion) + " " + itemName, options);

        dialog.showDialog();
    }

    private void appendBootToSection(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Map<Integer, Integer> sections = mSidebarService.getDefaultSections();

        for (Entry<Integer, Integer> section : sections.entrySet()) {
            options.add(
                    UiOptionItem.from(
                            getContext().getString(section.getKey()),
                            optionItem -> mSidebarService.setBootSectionId(section.getValue()),
                            section.getValue().equals(mSidebarService.getBootSectionId())
                    )
            );
        }

        Collection<Video> pinnedItems = mSidebarService.getPinnedItems();

        for (Video item : pinnedItems) {
            if (item != null && item.getTitle() != null) {
                options.add(
                        UiOptionItem.from(
                                item.getTitle(),
                                optionItem -> mSidebarService.setBootSectionId(item.getId()),
                                item.hashCode() == mSidebarService.getBootSectionId()
                        )
                );
            }
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.boot_to_section), options);
    }

    private void appendAppExitCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.app_exit_none, GeneralData.EXIT_NONE},
                {R.string.app_double_back_exit, GeneralData.EXIT_DOUBLE_BACK},
                {R.string.app_single_back_exit, GeneralData.EXIT_SINGLE_BACK}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> mGeneralData.setAppExitShortcut(pair[1]),
                    mGeneralData.getAppExitShortcut() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.app_exit_shortcut), options);
    }

    private void appendBackgroundPlaybackCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = AppDialogUtil.createBackgroundPlaybackCategory(getContext(), mPlayerData, mGeneralData);
        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendScreenDimmingCategory(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.screen_dimming), optionItem -> {
            AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());
            appendScreenDimmingAmountCategory(presenter);
            appendScreenDimmingTimeoutCategory(presenter);
            presenter.showDialog(getContext().getString(R.string.screen_dimming));
        }));
    }

    private void appendScreenDimmingAmountCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        int activeMode = mGeneralData.getScreensaverDimmingPercents();

        for (int dimPercents : Helpers.range(10, 80, 10)) {
            options.add(UiOptionItem.from(
                    dimPercents + "%",
                    option -> mGeneralData.setScreensaverDimmingPercents(dimPercents),
                    activeMode == dimPercents));
        }

        for (int dimPercents : Helpers.range(85, 100, 5)) {
            options.add(UiOptionItem.from(
                    dimPercents + "%",
                    option -> mGeneralData.setScreensaverDimmingPercents(dimPercents),
                    activeMode == dimPercents));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.screen_dimming_amount), options);
    }

    @SuppressLint("StringFormatMatches")
    private void appendScreenDimmingTimeoutCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        int screensaverTimeoutMs = mGeneralData.getScreensaverTimeoutMs();

        options.add(UiOptionItem.from(
                getContext().getString(R.string.option_never),
                option -> mGeneralData.setScreensaverTimeoutMs(GeneralData.SCREENSAVER_TIMEOUT_NEVER),
                screensaverTimeoutMs == GeneralData.SCREENSAVER_TIMEOUT_NEVER));

        for (int timeoutSec : new int[] {5, 15, 30}) {
            int timeoutMs = timeoutSec * 1_000;
            options.add(UiOptionItem.from(
                    getContext().getString(R.string.ui_hide_timeout_sec, timeoutSec),
                    option -> mGeneralData.setScreensaverTimeoutMs(timeoutMs),
                    screensaverTimeoutMs == timeoutMs));
        }

        for (int i = 1; i <= 15; i++) {
            int timeoutMs = i * 60 * 1_000;
            options.add(UiOptionItem.from(
                    getContext().getString(R.string.screen_dimming_timeout_min, i),
                    option -> mGeneralData.setScreensaverTimeoutMs(timeoutMs),
                    screensaverTimeoutMs == timeoutMs));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.screen_dimming_timeout), options);
    }

    //private void appendTimeFormatCategory(AppDialogPresenter settingsPresenter) {
    //    List<OptionItem> options = new ArrayList<>();
    //
    //    options.add(UiOptionItem.from(
    //            getContext().getString(R.string.time_format_24),
    //            option -> {
    //                mGeneralData.setTimeFormat(GeneralData.TIME_FORMAT_24);
    //                mRestartApp = true;
    //            },
    //            mGeneralData.getTimeFormat() == GeneralData.TIME_FORMAT_24));
    //
    //    options.add(UiOptionItem.from(
    //            getContext().getString(R.string.time_format_12),
    //            option -> {
    //                mGeneralData.setTimeFormat(GeneralData.TIME_FORMAT_12);
    //                mRestartApp = true;
    //            },
    //            mGeneralData.getTimeFormat() == GeneralData.TIME_FORMAT_12));
    //
    //    settingsPresenter.appendRadioCategory(getContext().getString(R.string.time_format), options);
    //}

    private void appendHistoryCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.auto_history, GeneralData.HISTORY_AUTO},
                {R.string.enable_history, GeneralData.HISTORY_ENABLED},
                {R.string.disable_history, GeneralData.HISTORY_DISABLED}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                mGeneralData.setHistoryState(pair[1]);
                MediaServiceManager.instance().enableHistory(pair[1] == GeneralData.HISTORY_AUTO || pair[1] == GeneralData.HISTORY_ENABLED);
            }, mGeneralData.getHistoryState() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.header_history), options);
    }

    private void appendMiscCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.disable_screensaver),
                option -> mGeneralData.setScreensaverDisabled(option.isSelected()),
                mGeneralData.isScreensaverDisabled()));

        // Disable long press on buggy controllers.
        options.add(UiOptionItem.from( getContext().getString(R.string.player_exit_shortcut) + ": " + getContext().getString(R.string.app_double_back_exit),
                option -> mGeneralData.setPlayerExitShortcut(option.isSelected() ? GeneralData.EXIT_DOUBLE_BACK : GeneralData.EXIT_SINGLE_BACK),
                mGeneralData.getPlayerExitShortcut() == GeneralData.EXIT_DOUBLE_BACK));

        options.add(UiOptionItem.from(getContext().getString(R.string.search_exit_shortcut) + ": " + getContext().getString(R.string.app_double_back_exit),
                option -> mGeneralData.setSearchExitShortcut(option.isSelected() ? GeneralData.EXIT_DOUBLE_BACK : GeneralData.EXIT_SINGLE_BACK),
                mGeneralData.getSearchExitShortcut() == GeneralData.EXIT_DOUBLE_BACK));

        options.add(UiOptionItem.from(getContext().getString(R.string.remember_position_subscriptions),
                option -> mGeneralData.setRememberSubscriptionsPositionEnabled(option.isSelected()),
                mGeneralData.isRememberSubscriptionsPositionEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.remember_position_pinned),
                option -> mGeneralData.setRememberPinnedPositionEnabled(option.isSelected()),
                mGeneralData.isRememberPinnedPositionEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_other), options);
    }

    private Map<Long, Integer> getMenuNames() {
        Map<Long, Integer> menuNames = new HashMap<>();
        menuNames.put(MainUIData.MENU_ITEM_EXIT_FROM_PIP, R.string.return_to_background_video);
        menuNames.put(MainUIData.MENU_ITEM_EXCLUDE_FROM_CONTENT_BLOCK, R.string.content_block_exclude_channel);
        menuNames.put(MainUIData.MENU_ITEM_MARK_AS_WATCHED, R.string.mark_as_watched);
        menuNames.put(MainUIData.MENU_ITEM_OPEN_CHANNEL, R.string.open_channel);
        menuNames.put(MainUIData.MENU_ITEM_UPDATE_CHECK, R.string.check_for_updates);
        menuNames.put(MainUIData.MENU_ITEM_CLEAR_HISTORY, R.string.clear_history);
        menuNames.put(MainUIData.MENU_ITEM_TOGGLE_HISTORY, R.string.pause_history);
        menuNames.put(MainUIData.MENU_ITEM_PLAYLIST_ORDER, R.string.playlist_order);
        menuNames.put(MainUIData.MENU_ITEM_PLAY_NEXT, R.string.play_next);
        menuNames.put(MainUIData.MENU_ITEM_ADD_TO_QUEUE, R.string.add_remove_from_playback_queue);
        menuNames.put(MainUIData.MENU_ITEM_SHOW_QUEUE, R.string.action_playback_queue);
        menuNames.put(MainUIData.MENU_ITEM_STREAM_REMINDER, R.string.set_stream_reminder);
        menuNames.put(MainUIData.MENU_ITEM_SUBSCRIBE, R.string.subscribe_unsubscribe_from_channel);
        menuNames.put(MainUIData.MENU_ITEM_SAVE_REMOVE_PLAYLIST, R.string.save_remove_playlist);
        menuNames.put(MainUIData.MENU_ITEM_CREATE_PLAYLIST, R.string.create_playlist);
        menuNames.put(MainUIData.MENU_ITEM_RENAME_PLAYLIST, R.string.rename_playlist);
        menuNames.put(MainUIData.MENU_ITEM_ADD_TO_WATCH_LATER, R.string.add_video_to_watch_later);
        menuNames.put(MainUIData.MENU_ITEM_ADD_TO_NEW_PLAYLIST, R.string.add_video_to_new_playlist);
        menuNames.put(MainUIData.MENU_ITEM_ADD_TO_PLAYLIST, R.string.dialog_add_to_playlist);
        menuNames.put(MainUIData.MENU_ITEM_RECENT_PLAYLIST, R.string.add_remove_from_recent_playlist);
        menuNames.put(MainUIData.MENU_ITEM_PLAY_VIDEO, R.string.play_video);
        menuNames.put(MainUIData.MENU_ITEM_PLAY_VIDEO_INCOGNITO, R.string.play_video_incognito);
        menuNames.put(MainUIData.MENU_ITEM_PLAY_FROM_START, R.string.play_from_start);
        menuNames.put(MainUIData.MENU_ITEM_NOT_INTERESTED, R.string.not_interested);
        menuNames.put(MainUIData.MENU_ITEM_NOT_RECOMMEND_CHANNEL, R.string.not_recommend_channel);
        menuNames.put(MainUIData.MENU_ITEM_REMOVE_FROM_HISTORY, R.string.remove_from_history);
        menuNames.put(MainUIData.MENU_ITEM_REMOVE_FROM_SUBSCRIPTIONS, R.string.remove_from_subscriptions);
        menuNames.put(MainUIData.MENU_ITEM_PIN_TO_SIDEBAR, R.string.pin_unpin_from_sidebar);
        menuNames.put(MainUIData.MENU_ITEM_SHARE_LINK, R.string.share_link);
        menuNames.put(MainUIData.MENU_ITEM_SHARE_EMBED_LINK, R.string.share_embed_link);
        menuNames.put(MainUIData.MENU_ITEM_SHARE_QR_LINK, R.string.share_qr_link);
        menuNames.put(MainUIData.MENU_ITEM_SELECT_ACCOUNT, R.string.dialog_account_list);
        menuNames.put(MainUIData.MENU_ITEM_MOVE_SECTION_UP, R.string.move_section_up);
        menuNames.put(MainUIData.MENU_ITEM_MOVE_SECTION_DOWN, R.string.move_section_down);
        menuNames.put(MainUIData.MENU_ITEM_RENAME_SECTION, R.string.rename_section);
        menuNames.put(MainUIData.MENU_ITEM_OPEN_DESCRIPTION, R.string.action_video_info);
        menuNames.put(MainUIData.MENU_ITEM_OPEN_COMMENTS, R.string.open_comments);
        menuNames.put(MainUIData.MENU_ITEM_OPEN_PLAYLIST, R.string.open_playlist);
        menuNames.put(MainUIData.MENU_ITEM_BLOCK_CHANNEL, R.string.dialog_block_channel);

        for (ContextMenuProvider provider : new ContextMenuManager(getContext()).getProviders()) {
            menuNames.put(provider.getId(), provider.getTitleResId());
        }

        return menuNames;
    }
}
