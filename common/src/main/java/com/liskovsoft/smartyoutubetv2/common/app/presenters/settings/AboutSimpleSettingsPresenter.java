package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.appupdatechecker2.AppUpdateChecker;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.ATVBridgePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AmazonBridgePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AppUpdatePresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class AboutSimpleSettingsPresenter extends BasePresenter<Void> {
    private final AppUpdateChecker mUpdateChecker;

    public AboutSimpleSettingsPresenter(Context context) {
        super(context);

        mUpdateChecker = new AppUpdateChecker(getContext(), null);
    }

    public static AboutSimpleSettingsPresenter instance(Context context) {
        return new AboutSimpleSettingsPresenter(context);
    }

    public void show() {
        // HearthTube: titled like its menu entry, About, with the version after it
        String mainTitle = String.format("%s %s %s", getContext().getString(R.string.settings_about),
                getContext().getString(R.string.app_name),
                AppInfoHelpers.getAppVersionName(getContext()));

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendAutoUpdateSwitch(settingsPresenter);

        appendUpdateCheckButton(settingsPresenter);

        // "Enable global search" is a setting, under General

        AboutSettingsPresenter.appendBackgroundCredit(getContext(), settingsPresenter);

        appendCredits(settingsPresenter);

        settingsPresenter.showDialog(mainTitle);
    }

    private void appendAutoUpdateSwitch(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.check_updates_auto), optionItem -> {
            mUpdateChecker.setUpdateCheckEnabled(optionItem.isSelected());
        }, mUpdateChecker.isUpdateCheckEnabled()));
    }

    private void appendUpdateCheckButton(AppDialogPresenter settingsPresenter) {
        OptionItem updateCheckOption = UiOptionItem.from(
                getContext().getString(R.string.check_for_updates),
                option -> AppUpdatePresenter.instance(getContext()).start(true));

        settingsPresenter.appendSingleButton(updateCheckOption);
    }

    private void appendInstallBridge(AppDialogPresenter settingsPresenter) {
        OptionItem installBridgeOption = UiOptionItem.from(
                getContext().getString(R.string.enable_voice_search),
                option -> startBridgePresenter());

        settingsPresenter.appendSingleButton(installBridgeOption);
    }

    private void startBridgePresenter() {
        MessageHelpers.showLongMessage(getContext(), R.string.enable_voice_search_desc);

        ATVBridgePresenter atvPresenter = ATVBridgePresenter.instance(getContext());
        atvPresenter.runBridgeInstaller(true);
        atvPresenter.unhold();

        AmazonBridgePresenter amazonPresenter = AmazonBridgePresenter.instance(getContext());
        amazonPresenter.runBridgeInstaller(true);
        amazonPresenter.unhold();
    }

    /**
     * Where HearthTube's code comes from. Each opens its project page (as a QR code on TVs without a browser).
     */
    private void appendCredits(AppDialogPresenter settingsPresenter) {
        String[][] credits = {
                {getContext().getString(R.string.credit_hearthtube), "https://github.com/theSiegs/HearthTube"},
                {getContext().getString(R.string.credit_smarttube), "https://github.com/yuliskov/SmartTube"},
                {getContext().getString(R.string.credit_mediaservicecore), "https://github.com/yuliskov/MediaServiceCore"},
                {getContext().getString(R.string.credit_exoplayer), "https://github.com/google/ExoPlayer"},
                {getContext().getString(R.string.credit_sponsorblock), "https://github.com/ajayyy/SponsorBlock"},
                {getContext().getString(R.string.credit_newpipe), "https://github.com/TeamNewPipe/NewPipe"},
                {getContext().getString(R.string.credit_libraries), "https://github.com/yuliskov/SmartTube/blob/master/smarttubetv/build.gradle"},
                {getContext().getString(R.string.credit_icons), "https://github.com/google/material-design-icons"},
                {getContext().getString(R.string.credit_hearth), "https://github.com/theSiegs/Hearth"},
        };

        List<OptionItem> items = new ArrayList<>();

        for (String[] credit : credits) {
            items.add(UiOptionItem.from(credit[0], credit[1].replace("https://", ""),
                    option -> Utils.openLink(getContext(), Utils.toQrCodeLink(credit[1])), false));
        }

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.credits), items);
    }
}
