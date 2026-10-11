package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.utils.ParentGate;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.Locale;

/**
 * HearthTube: settings opened from outside, by Hearth's page for a kid: {@link #ACTION}, with an optional
 * {@link #EXTRA_SECTION} for one page of them ({@link #GENERAL}, {@link #PLAYER}, {@link #ACCOUNTS},
 * {@link #BLOCKED_WORDS}, {@link #ABOUT}); any other value, or none, opens the settings menu.
 * <p>
 * The panels open over HearthTube's home, as from its own menu (the PIN and keyboard screens need a screen under
 * them), and in a kids profile behind the parent PIN, just the same. When they were opened from outside, the Back that
 * closes the last panel leaves HearthTube for where the person was (Hearth), as does giving up on the PIN.
 */
public final class SettingsDeepLink {
    private static final String TAG = SettingsDeepLink.class.getSimpleName();
    public static final String ACTION = "com.thesiegs.hearthtube.action.OPEN_SETTINGS";
    public static final String EXTRA_SECTION = "section";
    static final String GENERAL = "general";
    static final String PLAYER = "player";
    static final String ACCOUNTS = "accounts";
    static final String BLOCKED_WORDS = "blocked_words";
    static final String ABOUT = "about";
    /** The home has to be up before the panel (or the PIN) opens over it: a start from cold takes a while */
    private static final long WAIT_STEP_MS = 150;
    private static final int WAIT_STEPS = 40;
    /** Opened from outside: closing the settings leaves HearthTube */
    private static boolean sLeaveOnClose;

    private SettingsDeepLink() {
    }

    public static boolean isOpenSettings(Intent intent) {
        return intent != null && ACTION.equals(intent.getAction());
    }

    /** The settings page asked for, as one of the known names, or null for the settings menu */
    static String sectionOf(String extra) {
        if (extra == null) {
            return null;
        }

        String section = extra.trim().toLowerCase(Locale.ROOT);

        switch (section) {
            case GENERAL:
            case PLAYER:
            case ACCOUNTS:
            case BLOCKED_WORDS:
            case ABOUT:
                return section;
            default:
                return null;
        }
    }

    public static void open(Context context, Intent intent) {
        ViewManager viewManager = ViewManager.instance(context);
        // HearthTube in front already (a person in it): its settings close back into it, as usual
        sLeaveOnClose = !viewManager.isTopViewVisible();
        String section = sectionOf(intent.getStringExtra(EXTRA_SECTION));
        Log.d(TAG, "Open settings: %s, from outside: %s", section, sLeaveOnClose);

        viewManager.startDefaultView();
        showWhenHomeIsUp(context, section, 0);
    }

    private static void showWhenHomeIsUp(Context context, String section, int tries) {
        ViewManager viewManager = ViewManager.instance(context);
        Activity front = MotherActivity.getFrontActivity();
        Class<?> top = viewManager.getTopView();
        boolean up = front != null && !front.isFinishing() && top != null && viewManager.getActivity(top) == front.getClass();

        if (!up && tries < WAIT_STEPS) {
            Utils.postDelayed(() -> showWhenHomeIsUp(context, section, tries + 1), WAIT_STEP_MS);
            return;
        }

        // Not the splash screen that took the link: it's going away, and a PIN screen on it would go too
        show(up ? front : context.getApplicationContext(), section);
    }

    private static void show(Context context, String section) {
        // A kids profile's settings are a parent's: the PIN, as when opened from the menu
        ParentGate.run(context, () -> showUnlocked(context, section), SettingsDeepLink::onPinCancelled);
    }

    private static void showUnlocked(Context context, String section) {
        if (section == null) {
            SettingsMenuPresenter.show(context); // unlocked now: no second PIN
            return;
        }

        switch (section) {
            case GENERAL:
                GeneralSettingsPresenter.instance(context).show();
                break;
            case PLAYER:
                PlayerSettingsPresenter.instance(context).show();
                break;
            case ACCOUNTS:
                AccountSettingsPresenter.instance(context).show();
                break;
            case BLOCKED_WORDS:
                BlockedWordsPresenter.show(context);
                break;
            case ABOUT:
                AboutSimpleSettingsPresenter.instance(context).show();
                break;
        }
    }

    /** The settings panel's last Back (AppDialogActivity): out of HearthTube when the settings came from outside */
    public static void onPanelsClosedByBack(Context context) {
        if (!sLeaveOnClose) {
            return;
        }

        sLeaveOnClose = false;
        // The home underneath moves aside as it comes back
        ViewManager.instance(context).moveToBackOnResume();
    }

    private static void onPinCancelled() {
        if (!sLeaveOnClose) {
            return;
        }

        sLeaveOnClose = false;
        Activity front = MotherActivity.getFrontActivity();

        if (front != null) {
            try {
                front.moveTaskToBack(true);
            } catch (RuntimeException e) {
                Log.e(TAG, "Can't step aside: %s", e.getMessage());
            }
        }
    }
}
