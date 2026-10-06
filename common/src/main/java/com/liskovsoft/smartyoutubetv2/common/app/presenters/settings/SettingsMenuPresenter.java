package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager;

/**
 * Settings as a side panel menu, like the Hearth launcher's, instead of a page of tiles.
 * Each entry opens that category's panel on top; Back comes back to this menu.
 */
public class SettingsMenuPresenter {
    private SettingsMenuPresenter() {
    }

    public static void show(Context context) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);

        for (SettingsItem item : AppDataSourceManager.instance().getSettingItems(context)) {
            dialog.appendSingleButton(UiOptionItem.from(item.title, option -> item.onClick.run()));
        }

        dialog.showDialog(context.getString(R.string.header_settings));
    }
}
