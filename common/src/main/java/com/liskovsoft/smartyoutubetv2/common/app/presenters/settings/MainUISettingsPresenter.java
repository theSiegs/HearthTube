package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import android.os.Build;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.DeArrowData;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData.ColorScheme;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;

import java.util.ArrayList;
import java.util.List;

public class MainUISettingsPresenter extends BasePresenter<Void> {
    private final MainUIData mMainUIData;
    private final GeneralData mGeneralData;
    private final PlayerData mPlayerData;
    private final DeArrowData mDeArrowData;
    private boolean mRestartApp;
    private final Runnable mOnFinish = () -> {
        if (mRestartApp) {
            mRestartApp = false;
            MessageHelpers.showLongMessage(getContext(), R.string.msg_restart_app);
        }
    };

    private MainUISettingsPresenter(Context context) {
        super(context);
        mMainUIData = MainUIData.instance(context);
        mGeneralData = GeneralData.instance(context);
        mPlayerData = PlayerData.instance(context);
        mDeArrowData = DeArrowData.instance(context);
    }

    public static MainUISettingsPresenter instance(Context context) {
        return new MainUISettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendAccentColor(settingsPresenter);
        appendWallpaper(settingsPresenter);
        appendTopButtonsCategory(settingsPresenter);
        appendColorScheme(settingsPresenter);
        if (Build.VERSION.SDK_INT > 19) {
            appendCardTextScrollSpeed(settingsPresenter);
        }
        appendCardPreviews(settingsPresenter);
        appendCardStyle(settingsPresenter);
        appendThumbSource(settingsPresenter);
        //appendCardTitleLines(settingsPresenter);
        appendChannelSortingCategory(settingsPresenter);
        //appendPlaylistsCategoryStyle(settingsPresenter);
        appendScaleUI(settingsPresenter);
        if (Build.VERSION.SDK_INT > 19) {
            appendVideoGridScale(settingsPresenter);
        }
        //appendTimeFormatCategory(settingsPresenter);
        appendMiscCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.dialog_main_ui), mOnFinish);
    }

    /**
     * HearthTube: Hearth's accent (Match Hearth) or one of its 15. Shows after a restart (themes are set as screens open).
     */
    private void appendAccentColor(AppDialogPresenter settingsPresenter) {
        com.liskovsoft.smartyoutubetv2.common.prefs.HearthLinkData data =
                com.liskovsoft.smartyoutubetv2.common.prefs.HearthLinkData.instance(getContext());
        Integer chosen = data.getAccentColor();
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.match_hearth), option -> {
            data.setAccentColor(null);
            mRestartApp = true;
        }, chosen == null));

        for (int[] accent : com.liskovsoft.smartyoutubetv2.common.utils.HearthAccent.ACCENTS) {
            int color = accent[1];
            options.add(UiOptionItem.from(getContext().getString(accent[0]), option -> {
                data.setAccentColor(color);
                mRestartApp = true;
            }, chosen != null && chosen == color));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.accent_color), options);
    }

    /**
     * HearthTube: Hearth's wallpaper (Match Hearth), Bing's picture of the day, plain dark, or one of Hearth's gradients.
     */
    private void appendWallpaper(AppDialogPresenter settingsPresenter) {
        com.liskovsoft.smartyoutubetv2.common.prefs.HearthLinkData data =
                com.liskovsoft.smartyoutubetv2.common.prefs.HearthLinkData.instance(getContext());
        String chosen = data.getWallpaper();
        List<OptionItem> options = new ArrayList<>();

        String[][] basics = {
                {getContext().getString(R.string.match_hearth), com.liskovsoft.smartyoutubetv2.common.utils.HearthWallpaper.MATCH_HEARTH},
                {getContext().getString(R.string.wallpaper_bing), com.liskovsoft.smartyoutubetv2.common.utils.HearthWallpaper.BING},
                {getContext().getString(R.string.wallpaper_dark), com.liskovsoft.smartyoutubetv2.common.utils.HearthWallpaper.DARK},
        };

        for (String[] basic : basics) {
            options.add(UiOptionItem.from(basic[0], option -> {
                data.setWallpaper(basic[1]);
                mRestartApp = true;
            }, basic[1].equals(chosen)));
        }

        for (Object[] gradient : com.liskovsoft.smartyoutubetv2.common.utils.HearthWallpaper.GRADIENTS) {
            String value = com.liskovsoft.smartyoutubetv2.common.utils.HearthWallpaper.GRADIENT_PREFIX + gradient[0];
            options.add(UiOptionItem.from(getContext().getString((int) gradient[1]), option -> {
                data.setWallpaper(value);
                mRestartApp = true;
            }, value.equals(chosen)));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.wallpaper), options);
    }

    private void appendTopButtonsCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.settings_search, MainUIData.TOP_BUTTON_SEARCH},
                {R.string.settings_language_country, MainUIData.TOP_BUTTON_CHANGE_LANGUAGE},
                {R.string.settings_accounts, MainUIData.TOP_BUTTON_BROWSE_ACCOUNTS}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                if (optionItem.isSelected()) {
                    mMainUIData.setTopButtonEnabled(pair[1]);
                } else {
                    mMainUIData.setTopButtonDisabled(pair[1]);
                }
            }, mMainUIData.isTopButtonEnabled(pair[1])));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.various_buttons), options);
    }

    private void appendColorScheme(AppDialogPresenter settingsPresenter) {
        List<ColorScheme> colorSchemes = mMainUIData.getColorSchemes();

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.color_scheme), fromColorSchemes(colorSchemes));
    }

    private List<OptionItem> fromColorSchemes(List<ColorScheme> colorSchemes) {
        List<OptionItem> styleOptions = new ArrayList<>();

        for (ColorScheme colorScheme : colorSchemes) {
            styleOptions.add(UiOptionItem.from(
                    getContext().getString(colorScheme.nameResId),
                    option -> {
                        mMainUIData.setColorScheme(colorScheme);
                        mRestartApp = true;
                    },
                    colorScheme.equals(mMainUIData.getColorScheme())));
        }

        return styleOptions;
    }

    private void appendCardPreviews(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.option_disabled, MainUIData.CARD_PREVIEW_DISABLED},
                {R.string.card_preview_full, MainUIData.CARD_PREVIEW_FULL},
                {R.string.card_preview_muted, MainUIData.CARD_PREVIEW_MUTED}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                mMainUIData.setCardPreviewType(pair[1]);
            }, mMainUIData.getCardPreviewType() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.card_preview), options);
    }

    private void appendCardStyle(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        OptionItem multilineTitle = UiOptionItem.from(getContext().getString(R.string.card_multiline_title),
                option -> mMainUIData.setCardMultilineTitleEnabled(option.isSelected()), mMainUIData.isCardMultilineTitleEnabled());

        OptionItem multilineSubtitle = UiOptionItem.from(getContext().getString(R.string.card_multiline_subtitle),
                option -> mMainUIData.setCardMultilineSubtitleEnabled(option.isSelected()), mMainUIData.isCardMultilineSubtitleEnabled());

        OptionItem autoScrolledTitle = UiOptionItem.from(getContext().getString(R.string.card_auto_scrolled_title),
                option -> mMainUIData.setCardTextAutoScrollEnabled(option.isSelected()), mMainUIData.isCardTextAutoScrollEnabled());

        OptionItem unlocalizedTitle = UiOptionItem.from(getContext().getString(R.string.card_unlocalized_titles),
                option -> mMainUIData.setUnlocalizedTitlesEnabled(option.isSelected()), mMainUIData.isUnlocalizedTitlesEnabled());

        OptionItem roundedCardCorners = UiOptionItem.from(getContext().getString(R.string.rounded_card_corners),
                option -> {
                    if (option.isSelected()) {
                        mMainUIData.setUiTweakEnabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS);
                    } else {
                        mMainUIData.setUiTweakDisabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS);
                    }
                    mRestartApp = true;
                },
                mMainUIData.isUiTweakEnabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS));
        
        options.add(multilineTitle);
        options.add(multilineSubtitle);
        if (Build.VERSION.SDK_INT > 19) {
            options.add(autoScrolledTitle);
        }
        options.add(unlocalizedTitle);
        options.add(roundedCardCorners);

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.cards_style), options);
    }

    private void appendCardTitleLines(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int linesNum : new int[] {1, 2, 3, 4}) {
            options.add(UiOptionItem.from(String.format("%s", linesNum),
                    optionItem -> mMainUIData.setCartTitleLinesNum(linesNum),
                    linesNum == mMainUIData.getCardTitleLinesNum()));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.card_title_lines_num), options);
    }

    private void appendThumbSource(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.thumb_quality_default, ClickbaitRemover.THUMB_QUALITY_DEFAULT},
                {R.string.thumb_quality_start, ClickbaitRemover.THUMB_QUALITY_START},
                {R.string.thumb_quality_middle, ClickbaitRemover.THUMB_QUALITY_MIDDLE},
                {R.string.thumb_quality_end, ClickbaitRemover.THUMB_QUALITY_END}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> mMainUIData.setThumbQuality(pair[1]),
                    mMainUIData.getThumbQuality() == pair[1]
                    ));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.card_content), options);
    }

    private void appendChannelSortingCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.sorting_last_viewed, MainUIData.CHANNEL_SORTING_LAST_VIEWED},
                {R.string.sorting_alphabetically, MainUIData.CHANNEL_SORTING_NAME},
                {R.string.sorting_by_new_content, MainUIData.CHANNEL_SORTING_NEW_CONTENT}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                mMainUIData.setChannelCategorySorting(pair[1]);
                BrowsePresenter.instance(getContext()).updateChannelSorting();
            }, mMainUIData.getChannelCategorySorting() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.channels_section_sorting), options);
    }

    private void appendPlaylistsCategoryStyle(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.playlists_style_grid, MainUIData.PLAYLISTS_STYLE_GRID},
                {R.string.playlists_style_rows, MainUIData.PLAYLISTS_STYLE_ROWS}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                mMainUIData.setPlaylistsStyle(pair[1]);
                BrowsePresenter.instance(getContext()).updatePlaylistsStyle();
            }, mMainUIData.getPlaylistsStyle() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.playlists_style), options);
    }

    private void appendScaleUI(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (float scale : new float[] {0.4f, 0.5f, 0.6f, 0.65f, 0.7f, 0.75f, 0.8f, 0.85f, 0.9f, 0.95f, 1.0f, 1.05f, 1.1f, 1.15f, 1.2f, 1.25f, 1.3f, 1.35f, 1.4f}) {
            options.add(UiOptionItem.from(String.format("%sx", scale),
                    optionItem -> {
                        mMainUIData.setUIScale(scale);
                        mRestartApp = true;
                    },
                    Helpers.floatEquals(scale, mMainUIData.getUIScale())));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.scale_ui), options);
    }

    private void appendCardTextScrollSpeed(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (float factor : new float[] {1, 1.5f, 2, 2.5f, 3, 3.5f, 4, 4.5f, 5, 5.5f, 6, 6.5f, 7, 7.5f, 8}) {
            options.add(UiOptionItem.from(String.format("%sx", Helpers.formatFloat(factor)),
                    optionItem -> mMainUIData.setCardTextScrollSpeed(factor),
                    Helpers.floatEquals(factor, mMainUIData.getCardTextScrollSpeed())));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.card_text_scroll_factor), options);
    }

    private void appendVideoGridScale(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (float scale : new float[] {0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.25f, 1.3f, 1.35f, 1.4f, 1.5f}) {
            options.add(UiOptionItem.from(String.format("%sx", scale),
                    optionItem -> {
                        mMainUIData.setVideoGridScale(scale);
                        mRestartApp = true;
                    },
                    Helpers.floatEquals(scale, mMainUIData.getVideoGridScale())));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.video_grid_scale), options);
    }

    private void appendTimeFormatCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(
                getContext().getString(R.string.time_format_24),
                option -> {
                    mGeneralData.set24HourLocaleEnabled(true);
                    mRestartApp = true;
                },
                mGeneralData.is24HourLocaleEnabled()));

        options.add(UiOptionItem.from(
                getContext().getString(R.string.time_format_12),
                option -> {
                    mGeneralData.set24HourLocaleEnabled(false);
                    mRestartApp = true;
                },
                !mGeneralData.is24HourLocaleEnabled()));

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.time_format), options);
    }

    private void appendMiscCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();
        
        options.add(UiOptionItem.from(getContext().getString(R.string.card_unlocalized_titles),
                option -> {
                    mMainUIData.setUnlocalizedTitlesEnabled(option.isSelected());
                    mDeArrowData.setReplaceTitlesEnabled(false);
                },
                mMainUIData.isUnlocalizedTitlesEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.time_format_24) + " " + getContext().getString(R.string.time_format),
                option -> {
                    mGeneralData.set24HourLocaleEnabled(option.isSelected());
                    mRestartApp = true;
                },
                mGeneralData.is24HourLocaleEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.app_corner_clock),
                option -> {
                    mGeneralData.setGlobalClockEnabled(option.isSelected());
                    mRestartApp = true;
                },
                mGeneralData.isGlobalClockEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_corner_clock),
                option -> mPlayerData.setGlobalClockEnabled(option.isSelected()),
                mPlayerData.isGlobalClockEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_corner_ending_time),
                option -> mPlayerData.setGlobalEndingTimeEnabled(option.isSelected()),
                mPlayerData.isGlobalEndingTimeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.channels_old_look),
                optionItem -> {
                    mMainUIData.setUploadsOldLookEnabled(optionItem.isSelected());
                    mRestartApp = true;
                },
                mMainUIData.isUploadsOldLookEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.fullscreen_mode),
                option -> {
                    mGeneralData.setFullscreenModeEnabled(option.isSelected());
                    mRestartApp = true;
                },
                mGeneralData.isFullscreenModeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.pinned_channel_rows),
                optionItem -> {
                    mMainUIData.setPinnedChannelRowsEnabled(optionItem.isSelected());
                    mRestartApp = true;
                },
                mMainUIData.isPinnedChannelRowsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.playlists_rows),
                optionItem -> {
                    mMainUIData.setPlaylistsStyle(optionItem.isSelected() ? MainUIData.PLAYLISTS_STYLE_ROWS : MainUIData.PLAYLISTS_STYLE_GRID);
                    BrowsePresenter.instance(getContext()).updatePlaylistsStyle();
                },
                mMainUIData.getPlaylistsStyle() == MainUIData.PLAYLISTS_STYLE_ROWS));

        options.add(UiOptionItem.from(getContext().getString(R.string.channels_filter),
                optionItem -> mMainUIData.setChannelsFilterEnabled(optionItem.isSelected()),
                mMainUIData.isChannelsFilterEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.channel_search_bar),
                optionItem -> mMainUIData.setChannelSearchBarEnabled(optionItem.isSelected()),
                mMainUIData.isChannelSearchBarEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.channels_auto_load),
                optionItem -> mMainUIData.setUploadsAutoLoadEnabled(optionItem.isSelected()),
                mMainUIData.isUploadsAutoLoadEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_other), options);
    }
}
