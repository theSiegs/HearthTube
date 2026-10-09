package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.HearthLinkData;
import com.liskovsoft.smartyoutubetv2.common.prefs.ProfileLinkData;
import com.liskovsoft.smartyoutubetv2.common.utils.HearthLook;
import com.liskovsoft.smartyoutubetv2.common.utils.HearthProfile;
import com.liskovsoft.smartyoutubetv2.common.utils.WatchNextPublisher;

/**
 * Settings > Works with Hearth: one switch per way HearthTube fits in with the Hearth launcher.
 */
public class WorksWithHearthPresenter {
    private WorksWithHearthPresenter() {
    }

    public static void show(Context context) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        HearthLinkData data = HearthLinkData.instance(context);
        HearthProfile hearth = HearthProfile.queryHearth(context);

        ProfileLinkData links = ProfileLinkData.instance(context);
        dialog.appendSingleSwitch(UiOptionItem.from(context.getString(R.string.hearth_follow_profile),
                option -> links.setFollowEnabled(option.isSelected()), links.isFollowEnabled()));

        dialog.appendSingleSwitch(UiOptionItem.from(context.getString(R.string.hearth_continue_watching), option -> {
            data.setContinueWatchingEnabled(option.isSelected());
            if (!option.isSelected()) {
                WatchNextPublisher.clearAll(context);
            }
        }, data.isContinueWatchingEnabled()));

        dialog.appendSingleSwitch(UiOptionItem.from(context.getString(R.string.hearth_match_clock_language), option -> {
            data.setMatchClockAndLanguageEnabled(option.isSelected());
            HearthLook.refresh(context);
        }, data.isMatchClockAndLanguageEnabled()));

        dialog.appendSingleSwitch(UiOptionItem.from(context.getString(R.string.hearth_screensaver_paused),
                option -> data.setScreensaverWhenPausedEnabled(option.isSelected()), data.isScreensaverWhenPausedEnabled()));

        dialog.appendSingleSwitch(UiOptionItem.from(context.getString(R.string.hearth_back_to_hearth),
                option -> data.setBackToHearthEnabled(option.isSelected()), data.isBackToHearthEnabled()));

        dialog.appendSingleSwitch(UiOptionItem.from(context.getString(R.string.hearth_home_assistant),
                option -> data.setHomeAssistantEnabled(option.isSelected()), data.isHomeAssistantEnabled()));

        // Last, so the switches get the first focus; it says why they do nothing yet
        if (hearth == null) {
            dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.works_with_hearth_missing)));
        }

        dialog.showDialog(context.getString(R.string.works_with_hearth));
    }
}
