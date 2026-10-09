package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import android.util.SparseIntArray;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.HearthSections;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ProfilePickerPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.HearthTabsData;
import com.liskovsoft.smartyoutubetv2.common.utils.ParentGate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The menu that slides in from the left (Left at the left edge of the browse screen), like the Hearth launcher's:
 * HearthTube logo, who's watching, the sections that aren't tabs at the top, then Edit menu and tabs, and Settings.
 */
public class SideMenuPresenter {
    /** Dialog id of this menu: the panel shows the HearthTube logo on top (AppDialogFragment) */
    public static final int DIALOG_ID = 0x48545357;
    private static final SparseIntArray ICONS = new SparseIntArray();

    static {
        ICONS.put(MediaGroup.TYPE_SPORTS, R.drawable.ic_hearth_menu_sports);
        ICONS.put(MediaGroup.TYPE_LIVE, R.drawable.ic_hearth_menu_live);
        ICONS.put(MediaGroup.TYPE_NEWS, R.drawable.ic_hearth_menu_news);
        ICONS.put(HearthSections.TYPE_LIBRARY, R.drawable.ic_hearth_menu_library);
        ICONS.put(HearthSections.TYPE_WATCH_LATER, R.drawable.ic_hearth_menu_watch_later);
        ICONS.put(HearthSections.TYPE_PODCASTS, R.drawable.ic_hearth_menu_podcasts);
    }

    private SideMenuPresenter() {
    }

    public static void show(Context context) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        BrowsePresenter browse = BrowsePresenter.instance(context);
        HearthTabsData tabs = HearthTabsData.instance(context);

        dialog.appendSingleButton(UiOptionItem.from(
                SettingsMenuPresenter.withIcon(context, R.drawable.ic_hearth_settings_account,
                        context.getString(R.string.side_menu_who, firstName(context))),
                option -> {
                    dialog.closeDialog();
                    ProfilePickerPresenter.instance(context).start();
                }));

        for (BrowseSection section : browse.getSections()) {
            int id = section.getId();

            if (tabs.isTab(id) || HearthTabsData.hasOwnButton(id)) {
                continue;
            }

            // Pinned channels and playlists carry their Video; the rest are known sections
            int icon = section.getData() != null ? R.drawable.ic_hearth_menu_pin : ICONS.get(id, R.drawable.ic_hearth_menu_pin);
            dialog.appendSingleButton(UiOptionItem.from(
                    SettingsMenuPresenter.withIcon(context, icon, section.getTitle()),
                    option -> {
                        dialog.closeDialog();
                        browse.selectSection(id);
                    }));
        }

        dialog.appendSingleButton(UiOptionItem.from(
                SettingsMenuPresenter.withIcon(context, R.drawable.ic_hearth_menu_edit, context.getString(R.string.edit_menu_and_tabs)),
                option -> ParentGate.run(context, () -> showEditor(context))));

        dialog.appendSingleButton(UiOptionItem.from(
                SettingsMenuPresenter.withIcon(context, R.drawable.ic_hearth_menu_settings, context.getString(R.string.header_settings)),
                option -> SettingsMenuPresenter.show(context)));

        dialog.setId(DIALOG_ID);
        // No title: the logo says it all
        dialog.showDialog("");
    }

    /**
     * Edit menu and tabs: switch each section on or off, and choose which are tabs at the top.
     */
    private static void showEditor(Context context) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        BrowsePresenter browse = BrowsePresenter.instance(context);
        SidebarService sidebar = SidebarService.instance(context);
        HearthTabsData tabs = HearthTabsData.instance(context);

        List<OptionItem> shown = new ArrayList<>();
        List<OptionItem> atTop = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : sidebar.getDefaultSections().entrySet()) {
            int id = entry.getValue();

            if (id == MediaGroup.TYPE_SETTINGS) {
                continue;
            }

            String title = context.getString(entry.getKey());

            shown.add(UiOptionItem.from(title,
                    option -> browse.enableSection(id, option.isSelected()), sidebar.isSectionPinned(id)));

            if (!HearthTabsData.hasOwnButton(id)) {
                atTop.add(UiOptionItem.from(title, option -> {
                    tabs.setTab(id, option.isSelected());
                    browse.updateSections();
                }, tabs.isTab(id)));
            }
        }

        dialog.appendCheckedCategory(context.getString(R.string.edit_tabs_at_top), atTop);
        dialog.appendCheckedCategory(context.getString(R.string.edit_shown_sections), shown);
        dialog.showDialog(context.getString(R.string.edit_menu_and_tabs));
    }

    /** The Google TV profile's name (through Hearth), else the account's first name, else "Sign in" */
    private static String firstName(Context context) {
        com.liskovsoft.smartyoutubetv2.common.utils.HearthProfile profile =
                com.liskovsoft.smartyoutubetv2.common.utils.HearthProfile.query(context);

        if (profile != null && profile.name != null) {
            return profile.name;
        }

        Account account = MediaServiceManager.instance().getSelectedAccount();
        String name = account != null ? (account.getName() != null ? account.getName() : account.getEmail()) : null;

        if (name == null || name.trim().isEmpty()) {
            return context.getString(R.string.dialog_add_account);
        }

        return name.trim().split("\\s+")[0];
    }
}
