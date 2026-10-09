package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.ProfileLinkData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.GlideIconFetcher;
import com.liskovsoft.smartyoutubetv2.common.utils.HearthProfile;
import com.liskovsoft.smartyoutubetv2.common.utils.PinDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import com.liskovsoft.smartyoutubetv2.common.utils.ParentGate;

public class AccountSettingsPresenter extends BasePresenter<Void> {
    private static final String TAG = AccountSettingsPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static AccountSettingsPresenter sInstance;
    private final MediaServiceManager mMediaServiceManager;

    public AccountSettingsPresenter(Context context) {
        super(context);
        mMediaServiceManager = MediaServiceManager.instance();
    }

    public static AccountSettingsPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new AccountSettingsPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void unhold() {
        sInstance = null;
    }

    /** PIN keypads opened from this panel, still open or about to open */
    private int mPinsOpen;

    /**
     * A PIN keypad opened from Accounts. When the last one in a row closes (done, or Back), Accounts comes back
     * instead of leaving you on Browse.
     */
    private void showPin(String title, String message, com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog.OnChange onChange) {
        mPinsOpen++;
        PinDialog.show(getContext(), title, message, onChange, () -> Utils.post(() -> {
            // A next keypad (confirm, try again) is already counted by now
            if (--mPinsOpen == 0) {
                mMediaServiceManager.loadAccounts(this::fetchImagesAndShowDialog);
            }
        }));
    }

    public void show() {
        // HearthTube: kids profiles can't change accounts, PINs or pairings
        ParentGate.run(getContext(), () -> mMediaServiceManager.loadAccounts(this::fetchImagesAndShowDialog));
    }

    private void fetchImagesAndShowDialog(List<Account> accounts) {
        GlideIconFetcher.fetchDrawables(getContext(),
                Helpers.map(accounts, Account::getAvatarImageUrl),
                icons -> createAndShowDialog(accounts, icons));
    }

    private void createAndShowDialog(List<Account> accounts, List<Drawable> icons) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendSelectAccountSection(accounts, icons, settingsPresenter);
        appendSignInButton(settingsPresenter);
        appendSignOutSection(accounts, icons, settingsPresenter);
        appendProtectAccountWithPassword(settingsPresenter);
        appendParentPin(settingsPresenter);
        appendSeparateSettings(settingsPresenter);
        appendSelectAccountOnBoot(settingsPresenter);
        appendGoogleTvProfiles(accounts, icons, settingsPresenter);

        Account account = getSignInService().getSelectedAccount();
        int accountIndex = accounts != null ? accounts.indexOf(account) : -1;
        CharSequence accountIcon = accountIndex != -1 ? Utils.icon(icons.get(accountIndex)) : null;
        settingsPresenter.showDialog(account != null ? TextUtils.concat(accountIcon, " ", account.getName())
                : getContext().getString(R.string.settings_accounts), this::unhold);
    }

    private void appendSelectAccountSection(List<Account> accounts, List<Drawable> icons, AppDialogPresenter settingsPresenter) {
        if (accounts == null || accounts.isEmpty()) {
            return;
        }

        List<OptionItem> optionItems = new ArrayList<>();

        CharSequence accountName = " (" + getContext().getString(R.string.dialog_account_none) + ")";

        int index = -1;

        for (Account account : accounts) {
            index++;
            CharSequence icon = Utils.icon(icons.get(index));
            optionItems.add(UiOptionItem.from(
                    TextUtils.concat(icon, " ", getFullName(account)), option -> {
                        AccountSelectionPresenter.instance(getContext()).selectAccount(account);
                        settingsPresenter.closeDialog();
                    }, account.isSelected()
            ));

            if (account.isSelected()) {
                accountName = " (" + getSimpleName(account) + ")";
            }
        }

        String categoryTitle = getContext().getString(R.string.dialog_account_list);
        settingsPresenter.appendRadioCategory(categoryTitle, optionItems);
    }

    private void appendSignOutSection(List<Account> accounts, List<Drawable> icons, AppDialogPresenter settingsPresenter) {
        if (accounts == null || accounts.isEmpty()) {
            return;
        }

        List<OptionItem> optionItems = new ArrayList<>();

        int index = -1;

        for (Account account : accounts) {
            index++;
            CharSequence icon = Utils.icon(icons.get(index));
            optionItems.add(UiOptionItem.from(
                    TextUtils.concat(icon, " ", getFullName(account)), option ->
                        AppDialogUtil.showConfirmationDialog(
                                getContext(), getContext().getString(R.string.dialog_remove_account), () -> {
                                    removeAccount(account);
                                    settingsPresenter.closeDialog();
                                    MessageHelpers.showMessage(getContext(), R.string.msg_done);
                                })
            ));
        }

        settingsPresenter.appendStringsCategory(getContext().getString(R.string.dialog_remove_account), optionItems);
    }

    private void appendSignInButton(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.dialog_add_account), option -> YTSignInPresenter.instance(getContext()).start()));
    }

    private void appendSelectAccountOnBoot(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.select_account_on_boot), optionItem -> {
            AccountsData.instance(getContext()).selectAccountOnBoot(optionItem.isSelected());
        }, AccountsData.instance(getContext()).isSelectAccountOnBootEnabled()));
    }

    /**
     * The PIN that unlocks account changes in Google TV kids profiles (ParentGate). Turning it off takes the PIN
     * itself, in any profile: a kid could otherwise do it from a grown-up's profile.
     */
    private void appendParentPin(AppDialogPresenter settingsPresenter) {
        // One PIN for both apps: with Hearth's set, HearthTube's own isn't used (or offered)
        if (ParentGate.usesHearthPin(getContext())) {
            settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.parent_pin_from_hearth)));
            return;
        }

        ProfileLinkData links = ProfileLinkData.instance(getContext());

        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.parent_pin_setting), optionItem -> {
            settingsPresenter.closeDialog();

            if (optionItem.isSelected()) {
                showSetParentPinDialog(null);
            } else {
                showPin(getContext().getString(R.string.parent_pin_title), null,
                        pin -> {
                            if (links.checkParentPin(pin)) {
                                links.setParentPin(null);
                                MessageHelpers.showMessage(getContext(), R.string.msg_done);
                                return true;
                            }
                            return false;
                        });
            }
        }, links.hasParentPin()));
    }

    private void showSetParentPinDialog(String message) {
        showPin(
                getContext().getString(R.string.set_parent_pin),
                message != null ? message : getContext().getString(R.string.parent_pin_setting_hint),
                newPin -> {
                    Utils.post(() -> showPin(
                            getContext().getString(R.string.confirm_profile_pin),
                            null,
                            confirmed -> {
                                if (newPin.equals(confirmed)) {
                                    ProfileLinkData.instance(getContext()).setParentPin(newPin);
                                    MessageHelpers.showMessage(getContext(), R.string.msg_done);
                                } else {
                                    Utils.post(() -> showSetParentPinDialog(getContext().getString(R.string.pin_mismatch)));
                                }
                                return true;
                            }));
                    return true;
                });
    }

    private void appendProtectAccountWithPassword(AppDialogPresenter settingsPresenter) {
        // A PIN belongs to an account: nothing to lock while watching signed out
        if (getSignInService().getSelectedAccount() == null) {
            return;
        }

        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.protect_account_with_pin), optionItem -> {
            if (optionItem.isSelected()) {
                showAddPasswordDialog(settingsPresenter);
            } else {
                showRemovePasswordDialog(settingsPresenter);
            }
        }, AccountsData.instance(getContext()).getAccountPassword() != null));
    }

    private void appendSeparateSettings(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.multi_profiles),
                option -> {
                    AppPrefs.instance(getContext()).enableMultiProfiles(option.isSelected());
                    BrowsePresenter.instance(getContext()).updateSections();
                },
                AppPrefs.instance(getContext()).isMultiProfilesEnabled()));
    }

    /**
     * Which YouTube account each Google TV profile watches with. Only with Hearth around (or links made before).
     */
    private void appendGoogleTvProfiles(List<Account> accounts, List<Drawable> icons, AppDialogPresenter settingsPresenter) {
        if (accounts == null || accounts.isEmpty()) {
            return;
        }

        ProfileLinkData links = ProfileLinkData.instance(getContext());
        List<String> profiles = links.getProfileKeys();
        HearthProfile active = HearthProfile.query(getContext());

        if (active != null) {
            links.adoptName(active.key(), active.name);
            if (!profiles.contains(active.key())) {
                profiles.add(0, active.key());
            }
        }

        if (profiles.isEmpty()) {
            return;
        }

        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.follow_google_tv_profile),
                option -> links.setFollowEnabled(option.isSelected()), links.isFollowEnabled()));

        for (String profile : profiles) {
            ProfileLinkData.Link link = links.getLink(profile, accounts);
            List<OptionItem> optionItems = new ArrayList<>();

            optionItems.add(UiOptionItem.from(getContext().getString(R.string.google_tv_profile_ask),
                    option -> links.removeLink(profile), link == null));

            int index = -1;

            for (Account account : accounts) {
                index++;
                CharSequence icon = Utils.icon(icons.get(index));
                boolean isLinked = link != null && link.account != null && Helpers.equals(link.account.getName(), account.getName());
                optionItems.add(UiOptionItem.from(TextUtils.concat(icon, " ", getSimpleName(account)),
                        option -> linkWithPin(profile, account, settingsPresenter), isLinked));
            }

            String title = getContext().getString(R.string.google_tv_profiles) + ": " + links.getDisplayName(profile);
            settingsPresenter.appendRadioCategory(title, optionItems);
        }
    }

    /**
     * Linking a profile to a locked account would hand that profile the account without its PIN, so ask for it.
     */
    private void linkWithPin(String profile, Account account, AppDialogPresenter settingsPresenter) {
        String pin = AccountsData.instance(getContext()).getAccountPassword(account.getName());

        if (pin == null) {
            ProfileLinkData.instance(getContext()).setLink(profile, account);
            return;
        }

        settingsPresenter.closeDialog();
        showPin(
                getContext().getString(R.string.enter_profile_pin),
                null,
                newValue -> {
                    if (Utils.passwordMatch(pin, newValue)) {
                        ProfileLinkData.instance(getContext()).setLink(profile, account);
                        return true;
                    }
                    return false;
                });
    }

    private String getFullName(Account account) {
        String format;

        if (account.getEmail() != null) {
            format = String.format("%s (%s)", account.getName(), account.getEmail());
        } else {
            format = account.getName();
        }

        return format;
    }

    private String getSimpleName(Account account) {
        return account.getName() != null ? account.getName() : account.getEmail();
    }

    private void removeAccount(Account account) {
        getSignInService().removeAccount(account);
        BrowsePresenter.instance(getContext()).refresh(false);
    }

    private void showAddPasswordDialog(AppDialogPresenter settingsPresenter) {
        settingsPresenter.closeDialog();
        showSetPinDialog(null);
    }

    private void showSetPinDialog(String message) {
        showPin(
                getContext().getString(R.string.set_profile_pin),
                message,
                newPin -> {
                    // Dismiss first, then ask again to catch a mistyped digit
                    Utils.post(() -> showConfirmPinDialog(newPin));
                    return true;
                });
    }

    private void showConfirmPinDialog(String newPin) {
        showPin(
                getContext().getString(R.string.confirm_profile_pin),
                null,
                newValue -> {
                    if (newPin.equals(newValue)) {
                        AccountsData.instance(getContext()).setAccountPassword(newPin);
                        AccountsData.instance(getContext()).setPasswordAccepted(true); // the one who set it is already in
                        BrowsePresenter.instance(getContext()).updateSections();
                        MessageHelpers.showMessage(getContext(), R.string.msg_done);
                    } else {
                        Utils.post(() -> showSetPinDialog(getContext().getString(R.string.pin_mismatch)));
                    }
                    return true;
                });
    }

    private void showRemovePasswordDialog(AppDialogPresenter settingsPresenter) {
        String password = AccountsData.instance(getContext()).getAccountPassword();

        if (password == null) {
            return;
        }

        settingsPresenter.closeDialog();
        showPin(
                getContext().getString(R.string.enter_profile_pin),
                null,
                newValue -> {
                    if (Utils.passwordMatch(password, newValue)) {
                        AccountsData.instance(getContext()).setAccountPassword(null);
                        BrowsePresenter.instance(getContext()).updateSections();
                        return true;
                    }
                    return false;
                });
    }

    public void showCheckPasswordDialog() {
        String password = AccountsData.instance(getContext()).getAccountPassword();

        if (password == null) {
            return;
        }

        PinDialog.show(
                getContext(),
                getContext().getString(R.string.enter_profile_pin),
                newValue -> {
                    if (Utils.passwordMatch(password, newValue)) {
                        AccountsData.instance(getContext()).setPasswordAccepted(true);
                        BrowsePresenter.instance(getContext()).updateSections();
                        return true;
                    }
                    return false;
                });
    }
}
