package com.thesiegs.hearthtube.mobile.parent;

import android.content.Context;
import android.content.Intent;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AboutSimpleSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.BlockedWordsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.MainUISettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.HearthAllowance;
import com.liskovsoft.smartyoutubetv2.common.utils.KidsMode;
import com.liskovsoft.smartyoutubetv2.common.utils.ParentGate;
import com.thesiegs.hearthtube.mobile.MobileSplashActivity;
import com.thesiegs.hearthtube.mobile.R;

import java.util.ArrayList;
import java.util.List;

/**
 * The parent's way in on a kid's phone: behind the parent PIN (the first parent to open it chooses one), a short menu
 * of what a parent needs here. Everything else a kid could change stays out of reach.
 */
public final class MobileParentMenu {
    private MobileParentMenu() {
    }

    public static void show(Context context) {
        ParentGate.run(context, () -> showMenu(context));
    }

    /** Minutes of YouTube a day on this device; 0 is no limit. Hearth's limit, when Hearth is there, takes its place. */
    private static final int[] DAILY_MINUTES = {0, 15, 30, 45, 60, 90, 120};

    private static void appendDailyTime(Context context, AppDialogPresenter dialog) {
        int current = HearthAllowance.getLocalLimitMinutes(context);
        List<OptionItem> options = new ArrayList<>();

        for (int minutes : DAILY_MINUTES) {
            String title = minutes == 0 ? context.getString(R.string.daily_time_none)
                    : context.getString(R.string.daily_time_minutes, minutes);
            options.add(UiOptionItem.from(title, option -> HearthAllowance.setLocalLimitMinutes(context, minutes), minutes == current));
        }

        dialog.appendRadioCategory(context.getString(R.string.parent_daily_time), options);
    }

    /** A new accent color or wallpaper: open the screens again from the start, as they read their look when they open */
    private static void reopenScreens(Context context) {
        Intent intent = new Intent(context, MobileSplashActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }

    private static void showMenu(Context context) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);

        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.parent_accounts), option -> {
            dialog.closeDialog();
            AccountSettingsPresenter.instance(context).show();
        }));
        dialog.appendSingleSwitch(UiOptionItem.from(context.getString(R.string.parent_kids_mode),
                option -> KidsMode.setExplicit(context, option.isSelected()),
                ParentGate.isKidsProfile(context)));
        // A limit is for a kid's device: it does nothing on anyone else's
        if (ParentGate.isKidsProfile(context)) {
            appendDailyTime(context, dialog);
        }
        BlockedWordsPresenter.appendSettingsButton(context, dialog);
        // Accent color and wallpaper, as in the TV app's Look and layout; About has the wallpaper's credit
        dialog.appendSingleButton(UiOptionItem.from(context.getString(com.liskovsoft.smartyoutubetv2.common.R.string.settings_look_and_layout), option -> {
            dialog.closeDialog();
            MainUISettingsPresenter.instance(context).showLook(() -> reopenScreens(context));
        }));
        dialog.appendSingleButton(UiOptionItem.from(context.getString(com.liskovsoft.smartyoutubetv2.common.R.string.settings_about), option -> {
            dialog.closeDialog();
            AboutSimpleSettingsPresenter.instance(context).showCredits();
        }));

        dialog.showDialog(context.getString(ParentGate.isKidsProfile(context) ? R.string.parent_menu_title : R.string.settings_menu_title));
    }
}
