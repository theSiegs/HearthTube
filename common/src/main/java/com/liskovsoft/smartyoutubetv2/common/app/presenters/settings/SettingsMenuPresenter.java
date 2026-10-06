package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.SparseIntArray;

import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

/**
 * Settings as a side panel menu, like the Hearth launcher's, instead of a page of tiles.
 * Each entry has a line icon like Hearth's rows and opens that category's panel on top; Back comes back to this menu.
 */
public class SettingsMenuPresenter {
    /** Dialog id of this menu: the panel shows the HearthTube logo on top (AppDialogFragment) */
    public static final int DIALOG_ID = 0x48545553;
    private static final int ICON_SIZE_DP = 24;
    /** Tile artwork (settings grid) to the menu's line icon */
    private static final SparseIntArray ICONS = new SparseIntArray();

    static {
        ICONS.put(R.drawable.settings_account, R.drawable.ic_hearth_settings_account);
        ICONS.put(R.drawable.settings_cast, R.drawable.ic_hearth_settings_cast);
        ICONS.put(R.drawable.settings_language, R.drawable.ic_hearth_settings_language);
        ICONS.put(R.drawable.settings_app, R.drawable.ic_hearth_settings_general);
        ICONS.put(R.drawable.settings_main_ui, R.drawable.ic_hearth_settings_interface);
        ICONS.put(R.drawable.settings_player, R.drawable.ic_hearth_settings_player);
        ICONS.put(R.drawable.settings_afr, R.drawable.ic_hearth_settings_display);
        ICONS.put(R.drawable.settings_subtitles, R.drawable.ic_hearth_settings_subtitles);
        ICONS.put(R.drawable.settings_search, R.drawable.ic_hearth_settings_search);
        ICONS.put(R.drawable.settings_block, R.drawable.ic_hearth_settings_block);
        ICONS.put(R.drawable.settings_dearrow, R.drawable.ic_hearth_settings_label);
        ICONS.put(R.drawable.settings_backup, R.drawable.ic_hearth_settings_backup);
        ICONS.put(R.drawable.settings_about, R.drawable.ic_hearth_settings_about);
    }

    private SettingsMenuPresenter() {
    }

    public static void show(Context context) {
        // Kids profiles: every setting (Shorts, accounts, PINs) is a parent's call
        com.liskovsoft.smartyoutubetv2.common.utils.ParentGate.run(context, () -> showUnlocked(context));
    }

    private static void showUnlocked(Context context) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);

        for (SettingsItem item : AppDataSourceManager.instance().getSettingItems(context)) {
            dialog.appendSingleButton(UiOptionItem.from(withIcon(context, item), option -> item.onClick.run()));
        }

        dialog.setId(DIALOG_ID);
        dialog.showDialog(context.getString(R.string.header_settings));
    }

    private static CharSequence withIcon(Context context, SettingsItem item) {
        int iconResId = ICONS.get(item.imageResId, 0);
        Drawable icon = iconResId != 0 ? ContextCompat.getDrawable(context, iconResId) : null;

        if (icon == null) {
            return item.title;
        }

        int size = Math.round(ICON_SIZE_DP * context.getResources().getDisplayMetrics().density);
        icon.setBounds(0, 0, size, size);

        // Em spaces: about Hearth's 16dp gap between icon and label
        return TextUtils.concat(Utils.icon(icon), "  ", item.title);
    }
}
