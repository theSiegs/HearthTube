package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.utils.HearthProfile;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The list of settings pages, once, for the TV app and the phone app: the same names in the same order, the TV with a few
 * more (the pages that are about a remote control or a TV). Both menus are made from it, so a page added here shows up
 * where it belongs in both, and the phone can't drift from the TV.
 * <p>
 * What Hearth owns isn't asked for here: with Hearth, there is no Language page.
 */
public final class HearthSettings {
    /** The pages, in the order they are listed */
    public enum Page {
        ACCOUNTS, CAST, GENERAL, LOOK, PLAYER, CONTENT, SEARCH, LANGUAGE, BACKUP, ABOUT
    }

    private HearthSettings() {
    }

    /** One page of settings: its name, the line-art drawable the TV menu draws it with (also its identity), and how to open it */
    public static final class Entry {
        public final Page page;
        public final String title;
        public final int iconResId;
        public final Runnable open;

        private Entry(Page page, String title, int iconResId, Runnable open) {
            this.page = page;
            this.title = title;
            this.iconResId = iconResId;
            this.open = open;
        }
    }

    /**
     * Which pages there are. The phone has none about a remote control, TV frame rate or the TV's own options (General,
     * Search's voice, Backup); with Hearth there, Language is Hearth's. Plain Java, so it is tested apart from Android.
     */
    public static List<Page> pages(boolean mobile, boolean hearthThere) {
        List<Page> pages = new ArrayList<>(Arrays.asList(Page.values()));

        if (mobile) {
            pages.removeAll(Arrays.asList(Page.CAST, Page.GENERAL, Page.SEARCH, Page.BACKUP));
        }

        if (hearthThere) {
            pages.remove(Page.LANGUAGE);
        }

        return pages;
    }

    /**
     * @param mobile        the phone app's list
     * @param onLookChanged phone: what to do when a change of look or of Shorts needs the screens to open again; the TV
     *                      asks for a restart instead (null)
     */
    public static List<Entry> entries(Context context, boolean mobile, Runnable onLookChanged) {
        List<Entry> entries = new ArrayList<>();

        for (Page page : pages(mobile, HearthProfile.queryHearth(context) != null)) {
            entries.add(entry(context, page, mobile, onLookChanged));
        }

        return entries;
    }

    private static Entry entry(Context context, Page page, boolean mobile, Runnable onLookChanged) {
        switch (page) {
            case ACCOUNTS:
                return of(context, page, R.string.settings_accounts, R.drawable.settings_account,
                        () -> AccountSettingsPresenter.instance(context).show());
            case CAST:
                return of(context, page, R.string.settings_cast_from_phone, R.drawable.settings_cast,
                        () -> RemoteControlSettingsPresenter.instance(context).show());
            case GENERAL:
                return of(context, page, R.string.settings_general, R.drawable.settings_app,
                        () -> GeneralSettingsPresenter.instance(context).show());
            case LOOK:
                return of(context, page, R.string.settings_look_and_layout, R.drawable.settings_main_ui, mobile
                        ? () -> MainUISettingsPresenter.instance(context).showLook(onLookChanged)
                        : () -> MainUISettingsPresenter.instance(context).show());
            case PLAYER:
                return of(context, page, R.string.settings_player, R.drawable.settings_player, mobile
                        ? () -> PlayerSettingsPresenter.instance(context).showMobile()
                        : () -> PlayerSettingsPresenter.instance(context).show());
            case CONTENT:
                return of(context, page, R.string.settings_content_and_safety, R.drawable.settings_block,
                        () -> ContentSettingsPresenter.instance(context).show(mobile ? onLookChanged : null));
            case SEARCH:
                return of(context, page, R.string.settings_search, R.drawable.settings_search,
                        () -> SearchSettingsPresenter.instance(context).show());
            case LANGUAGE:
                return of(context, page, R.string.settings_language_country, R.drawable.settings_language,
                        () -> LanguageSettingsPresenter.instance(context).show());
            case BACKUP:
                return of(context, page, R.string.app_backup_restore, R.drawable.settings_backup,
                        () -> BackupSettingsPresenter.instance(context).show());
            case ABOUT:
            default:
                return of(context, page, R.string.settings_about, R.drawable.settings_about, mobile
                        ? () -> AboutSimpleSettingsPresenter.instance(context).showCredits()
                        : Helpers.equalsAny(context.getPackageName(), Utils.KNOWN_PACKAGES)
                                ? () -> AboutSettingsPresenter.instance(context).show()
                                : () -> AboutSimpleSettingsPresenter.instance(context).show());
        }
    }

    private static Entry of(Context context, Page page, int titleResId, int iconResId, Runnable open) {
        return new Entry(page, context.getString(titleResId), iconResId, open);
    }
}
