package com.thesiegs.hearthtube.mobile.parent;

import android.content.Context;
import android.content.Intent;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.HearthSettings;
import com.liskovsoft.smartyoutubetv2.common.utils.HearthAllowance;
import com.liskovsoft.smartyoutubetv2.common.utils.KidsMode;
import com.liskovsoft.smartyoutubetv2.common.utils.ParentGate;
import com.thesiegs.hearthtube.mobile.MobileSplashActivity;
import com.thesiegs.hearthtube.mobile.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings on the phone, and on a kid's phone the parent's way in: behind the parent PIN (the first parent to open it
 * chooses one). The pages are the TV app's list (HearthSettings), with the same names in the same order, less the ones
 * about a remote control or a TV; what is only a phone's, kids mode and the daily time, is at the top.
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

    /** A new accent color, wallpaper or Shorts setting: open the screens again from the start, as they read it when they open */
    private static void reopenScreens(Context context) {
        Intent intent = new Intent(context, MobileSplashActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }

    private static void showMenu(Context context) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);

        dialog.appendSingleSwitch(UiOptionItem.from(context.getString(R.string.parent_kids_mode),
                option -> KidsMode.setExplicit(context, option.isSelected()),
                ParentGate.isKidsProfile(context)));
        // A limit is for a kid's device: it does nothing on anyone else's
        if (ParentGate.isKidsProfile(context)) {
            appendDailyTime(context, dialog);
        }

        for (HearthSettings.Entry entry : HearthSettings.entries(context, true, () -> reopenScreens(context))) {
            // The page opens over the menu, so Back from it returns here, as on the TV
            dialog.appendSingleButton(UiOptionItem.from(entry.title, option -> entry.open.run()));
        }

        dialog.showDialog(context.getString(ParentGate.isKidsProfile(context) ? R.string.parent_menu_title : R.string.settings_menu_title));
    }
}
