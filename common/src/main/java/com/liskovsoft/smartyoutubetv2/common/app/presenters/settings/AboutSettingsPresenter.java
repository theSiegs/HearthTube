package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.appupdatechecker2.AppUpdateChecker;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.locale.LocaleUtility;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.ATVBridgePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AmazonBridgePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AppUpdatePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.DailyBackground;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class AboutSettingsPresenter extends BasePresenter<Void> {
    private final AppUpdateChecker mUpdateChecker;

    public AboutSettingsPresenter(Context context) {
        super(context);

        mUpdateChecker = new AppUpdateChecker(getContext(), null);
    }

    public static AboutSettingsPresenter instance(Context context) {
        return new AboutSettingsPresenter(context);
    }

    public void show() {
        // HearthTube: titled like its menu entry, About, with the version after it
        String mainTitle = String.format("%s %s %s", getContext().getString(R.string.settings_about),
                getContext().getString(R.string.app_name),
                getVersionLine(getContext()));

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        String country = LocaleUtility.getCurrentLocale(getContext()).getCountry();

        appendUpdateCheckButton(settingsPresenter);

        appendAutoUpdateSwitch(settingsPresenter);

        appendUpdateChangelogButton(settingsPresenter);

        appendBackgroundCredit(settingsPresenter);

        appendUpdateSource(settingsPresenter);

        // HearthTube: "Enable global search" is a setting, under General

        if (!Helpers.equalsAny(country, "RU", "UA")) {
            appendDonation(settingsPresenter);
            appendFeedback(settingsPresenter);
            appendLinks(settingsPresenter);
        }

        settingsPresenter.showDialog(mainTitle);
    }

    private void appendAutoUpdateSwitch(AppDialogPresenter settingsPresenter) {
        GeneralData generalData = GeneralData.instance(getContext());
        List<OptionItem> items = new ArrayList<>();

        items.add(UiOptionItem.from(getContext().getString(R.string.option_disabled),
                optionItem -> mUpdateChecker.setUpdateCheckEnabled(false),
                !mUpdateChecker.isUpdateCheckEnabled()));

        items.add(UiOptionItem.from(getContext().getString(R.string.sidebar_notification), optionItem -> {
            mUpdateChecker.setUpdateCheckEnabled(true);
            generalData.setOldUpdateNotificationsEnabled(false);
        }, mUpdateChecker.isUpdateCheckEnabled() && !generalData.isOldUpdateNotificationsEnabled()));
        
        items.add(UiOptionItem.from(getContext().getString(R.string.dialog_notification), optionItem -> {
            mUpdateChecker.setUpdateCheckEnabled(true);
            generalData.setOldUpdateNotificationsEnabled(true);
        }, mUpdateChecker.isUpdateCheckEnabled() && generalData.isOldUpdateNotificationsEnabled()));

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.check_updates_auto), items);
    }

    private void appendUpdateCheckButton(AppDialogPresenter settingsPresenter) {
        OptionItem updateCheckOption = UiOptionItem.from(
                getContext().getString(R.string.check_for_updates),
                option -> AppUpdatePresenter.instance(getContext()).start(true));

        settingsPresenter.appendSingleButton(updateCheckOption);
    }

    private void appendUpdateChangelogButton(AppDialogPresenter settingsPresenter) {
        List<String> changes = GeneralData.instance(getContext()).getChangelog();

        if (changes == null || changes.isEmpty()) {
            return;
        }

        List<OptionItem> changelog = new ArrayList<>();

        for (String change : changes) {
            changelog.add(UiOptionItem.from(change));
        }

        String title = String.format("%s %s",
                getContext().getString(R.string.update_changelog),
                AppInfoHelpers.getAppVersionName(getContext()));

        settingsPresenter.appendStringsCategory(title, changelog);
    }

    private void appendBackgroundCredit(AppDialogPresenter settingsPresenter) {
        appendBackgroundCredit(getContext(), settingsPresenter);
    }

    /**
     * The version for About's title, shared with AboutSimpleSettingsPresenter: HearthTube's, a release date, and the
     * SmartTube version it's built on (version_based_on_smarttube: 2026.10.10 and 32.63); just the version in builds
     * without a base (SmartTube's own flavors).
     */
    static String getVersionLine(Context context) {
        String version = AppInfoHelpers.getAppVersionName(context);
        String base = context.getString(R.string.smarttube_base_version);

        return base.isEmpty() ? version : context.getString(R.string.version_based_on_smarttube, version, base);
    }

    /**
     * Shared with AboutSimpleSettingsPresenter (builds with a non-official package name)
     */
    static void appendBackgroundCredit(Context context, AppDialogPresenter settingsPresenter) {
        // In Hearth: Hearth's wallpaper, so its Bing photo's title and credit, and none for a picture or gradient
        com.liskovsoft.smartyoutubetv2.common.utils.HearthProfile synced =
                com.liskovsoft.smartyoutubetv2.common.utils.HearthWallpaper.getSynced(context);
        if (synced != null) {
            if ("bing".equals(synced.wallpaperKind) && synced.wallpaperCredit != null) {
                List<OptionItem> items = new ArrayList<>();
                if (synced.wallpaperTitle != null) {
                    items.add(UiOptionItem.from(synced.wallpaperTitle));
                }
                items.add(UiOptionItem.from(synced.wallpaperCredit));
                items.add(UiOptionItem.from(context.getString(R.string.background_image_source)));
                settingsPresenter.appendStringsCategory(context.getString(R.string.background_image), items);
            }
            return;
        }

        String credit = DailyBackground.getCredit(context);

        if (credit == null) {
            return;
        }

        List<OptionItem> items = new ArrayList<>();
        items.add(UiOptionItem.from(credit));
        items.add(UiOptionItem.from(context.getString(R.string.background_image_source)));

        settingsPresenter.appendStringsCategory(context.getString(R.string.background_image), items);
    }

    private void appendLinks(AppDialogPresenter settingsPresenter) {
        OptionItem releasesOption = UiOptionItem.from(getContext().getString(R.string.releases),
                option -> Utils.openLink(getContext(), Utils.toQrCodeLink(getContext().getString(R.string.releases_url))));

        OptionItem sourcesOption = UiOptionItem.from(getContext().getString(R.string.sources),
                option -> Utils.openLink(getContext(), Utils.toQrCodeLink(getContext().getString(R.string.sources_url))));

        //OptionItem webSiteOption = UiOptionItem.from(getContext().getString(R.string.web_site),
        //        option -> Utils.openLink(getContext(), Utils.toQrCodeLink(getContext().getString(R.string.web_site_url))));

        settingsPresenter.appendSingleButton(releasesOption);
        settingsPresenter.appendSingleButton(sourcesOption);
        //settingsPresenter.appendSingleButton(webSiteOption);
    }

    private void appendDonation(AppDialogPresenter settingsPresenter) {
        List<OptionItem> donateOptions = new ArrayList<>();

        Map<String, String> donations = Helpers.getMap(getContext(), R.array.donations);

        for (Entry<String, String> entry : donations.entrySet()) {
            donateOptions.add(UiOptionItem.from(
                    entry.getKey(),
                    option -> Utils.openLink(getContext(), Utils.toQrCodeLink(entry.getValue()))));
        }

        if (!donateOptions.isEmpty()) {
            settingsPresenter.appendStringsCategory(getContext().getString(R.string.donation), donateOptions);
        }
    }

    private void appendUpdateSource(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        String[] updateUrls = getContext().getResources().getStringArray(R.array.update_urls);

        if (updateUrls.length <= 1) {
            return;
        }

        if (mUpdateChecker.getPreferredHost() == null) {
            mUpdateChecker.setPreferredHost(Helpers.getHost(updateUrls[0]));
        }

        for (String url : updateUrls) {
            String hostName = Helpers.getHost(url);
            options.add(UiOptionItem.from(hostName,
                    optionItem -> mUpdateChecker.setPreferredHost(hostName),
                    Helpers.equals(hostName, mUpdateChecker.getPreferredHost())));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.preferred_update_source), options);
    }

    private void appendFeedback(AppDialogPresenter settingsPresenter) {
        List<OptionItem> feedbackOptions = new ArrayList<>();

        Map<String, String> feedback = Helpers.getMap(getContext(), R.array.feedback);

        for (Entry<String, String> entry : feedback.entrySet()) {
            feedbackOptions.add(UiOptionItem.from(
                    entry.getKey(),
                    option -> Utils.openLink(getContext(), Utils.toQrCodeLink(entry.getValue()))));
        }

        if (!feedbackOptions.isEmpty()) {
            settingsPresenter.appendStringsCategory(getContext().getString(R.string.feedback), feedbackOptions);
        }
    }

    static void appendInstallBridge(Context context, AppDialogPresenter settingsPresenter) {
        OptionItem installBridgeOption = UiOptionItem.from(
                context.getString(R.string.enable_voice_search),
                option -> startBridgePresenter(context));

        settingsPresenter.appendSingleButton(installBridgeOption);
    }

    private static void startBridgePresenter(Context context) {
        MessageHelpers.showLongMessage(context, R.string.enable_voice_search_desc);

        ATVBridgePresenter atvPresenter = ATVBridgePresenter.instance(context);
        atvPresenter.runBridgeInstaller(true);
        atvPresenter.unhold();

        AmazonBridgePresenter amazonPresenter = AmazonBridgePresenter.instance(context);
        amazonPresenter.runBridgeInstaller(true);
        amazonPresenter.unhold();
    }
}
