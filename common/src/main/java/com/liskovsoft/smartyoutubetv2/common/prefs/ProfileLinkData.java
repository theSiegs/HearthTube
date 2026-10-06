package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.Helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Which YouTube account each Google TV profile watches with. Keys are Google TV profile names
 * (from {@link com.liskovsoft.smartyoutubetv2.common.utils.HearthProfile}), values are account names
 * (the same key {@link AccountsData} uses for PINs), or "" for the guest.
 */
public class ProfileLinkData {
    private static final String PREFS_NAME = "hearth_profile_links";
    private static final String LINK_PREFIX = "link:";
    private static final String FOLLOW_KEY = "follow_google_tv_profile";
    private static final String PARENT_PIN_KEY = "parent_pin";
    private static final String GUEST = "";
    @SuppressLint("StaticFieldLeak")
    private static ProfileLinkData sInstance;
    private final SharedPreferences mPrefs;

    /** A profile's link: the account it watches with, or the guest when {@link #account} is null. */
    public static class Link {
        @Nullable
        public final Account account;

        private Link(@Nullable Account account) {
            this.account = account;
        }
    }

    private ProfileLinkData(Context context) {
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static ProfileLinkData instance(Context context) {
        if (sInstance == null) {
            sInstance = new ProfileLinkData(context.getApplicationContext());
        }

        return sInstance;
    }

    /**
     * Unlocks account changes in kids profiles (ParentGate). Separate from account PINs, which a kid may know
     * (their own). Null when not set.
     */
    @Nullable
    public String getParentPin() {
        return mPrefs.getString(PARENT_PIN_KEY, null);
    }

    public void setParentPin(@Nullable String pin) {
        mPrefs.edit().putString(PARENT_PIN_KEY, pin).apply();
    }

    /** Switch accounts to follow the Google TV profile. On by default; does nothing without Hearth. */
    public boolean isFollowEnabled() {
        return mPrefs.getBoolean(FOLLOW_KEY, true);
    }

    public void setFollowEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(FOLLOW_KEY, enabled).apply();
    }

    /**
     * The account linked to this profile, if it's still signed in. Null when the profile has no link yet,
     * or its account was removed.
     */
    @Nullable
    public Link getLink(String profileName, List<Account> accounts) {
        String accountName = mPrefs.getString(LINK_PREFIX + profileName, null);

        if (accountName == null) {
            return null;
        }

        if (GUEST.equals(accountName)) {
            return new Link(null);
        }

        Account account = findByName(accounts, accountName);

        return account != null ? new Link(account) : null;
    }

    /** Link a profile to an account, or to the guest with null. */
    public void setLink(String profileName, @Nullable Account account) {
        String value = account != null && account.getName() != null ? account.getName() : GUEST;
        mPrefs.edit().putString(LINK_PREFIX + profileName, value).apply();
    }

    public void removeLink(String profileName) {
        mPrefs.edit().remove(LINK_PREFIX + profileName).apply();
    }

    /** Every Google TV profile seen so far, sorted. */
    public List<String> getProfileNames() {
        List<String> names = new ArrayList<>();

        for (String key : mPrefs.getAll().keySet()) {
            if (key.startsWith(LINK_PREFIX)) {
                names.add(key.substring(LINK_PREFIX.length()));
            }
        }

        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);

        return names;
    }

    /**
     * The one account that plainly belongs to this profile: Google TV names profiles after the
     * person's first name ("Alex"), YouTube accounts carry the full name ("Alex Rivera").
     * Null when none or several match, so the picker asks instead.
     */
    @Nullable
    public static Account guessAccount(String profileName, List<Account> accounts) {
        if (profileName == null || accounts == null) {
            return null;
        }

        String profile = normalize(profileName);
        Account match = null;

        for (Account account : accounts) {
            if (!profile.isEmpty() && (profile.equals(firstWord(account.getName())) || profile.equals(normalize(account.getName())))) {
                if (match != null) {
                    return null; // two Alexes: let the picker ask
                }
                match = account;
            }
        }

        return match;
    }

    @Nullable
    private static Account findByName(List<Account> accounts, String name) {
        if (accounts == null) {
            return null;
        }

        for (Account account : accounts) {
            if (Helpers.equals(account.getName(), name)) {
                return account;
            }
        }

        return null;
    }

    private static String firstWord(String name) {
        String normalized = normalize(name);
        int space = normalized.indexOf(' ');
        return space != -1 ? normalized.substring(0, space) : normalized;
    }

    private static String normalize(String name) {
        return name != null ? name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT) : "";
    }
}
