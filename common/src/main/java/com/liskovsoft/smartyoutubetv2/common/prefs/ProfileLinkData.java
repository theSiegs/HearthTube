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
    private static final String USING_HEARTH_PIN_KEY = "using_hearth_pin";
    private static final String GUEST_ENABLED_KEY = "guest_enabled";
    /** "kids:<profile>" = true for a Google TV kids profile, false for a grown-up's (as last seen) */
    private static final String KIDS_PREFIX = "kids:";
    /** "name:<key>" = the profile's name when last seen (links are saved under Hearth's lasting profile key) */
    private static final String NAME_PREFIX = "name:";
    private static final String GUEST = "";
    @SuppressLint("StaticFieldLeak")
    private static ProfileLinkData sInstance;
    private final SharedPreferences mPrefs;
    private final Context mContext;

    /** A profile's link: the account it watches with, or the guest when {@link #account} is null. */
    public static class Link {
        @Nullable
        public final Account account;

        private Link(@Nullable Account account) {
            this.account = account;
        }
    }

    private ProfileLinkData(Context context) {
        mContext = context;
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
     * (their own).
     */
    public boolean hasParentPin() {
        return mPrefs.getString(PARENT_PIN_KEY, null) != null;
    }

    /** True when the PIN is the parent PIN. A PIN saved as plain digits by an older version is hashed on success. */
    public boolean checkParentPin(@Nullable String pin) {
        String saved = mPrefs.getString(PARENT_PIN_KEY, null);

        if (saved == null || pin == null) {
            return false;
        }

        if (saved.equals(hash(pin))) {
            return true;
        }

        if (saved.equals(pin)) { // older versions kept it as is
            setParentPin(pin);
            return true;
        }

        return false;
    }

    /** Saved hashed, not as the digits themselves. Null removes it. */
    public void setParentPin(@Nullable String pin) {
        mPrefs.edit().putString(PARENT_PIN_KEY, pin != null ? hash(pin) : null).apply();
    }

    private static String hash(String pin) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(("hearthtube-parent-pin:" + pin).getBytes(java.nio.charset.Charset.forName("UTF-8")));
            StringBuilder hex = new StringBuilder("sha256:");
            for (byte b : bytes) {
                hex.append(String.format(Locale.US, "%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** HearthTube has been using Hearth's parent PIN (so its own one, if any, is likely forgotten) */
    public boolean isUsingHearthPin() {
        return mPrefs.getBoolean(USING_HEARTH_PIN_KEY, false);
    }

    public void setUsingHearthPin(boolean using) {
        if (using != isUsingHearthPin()) {
            mPrefs.edit().putBoolean(USING_HEARTH_PIN_KEY, using).apply();
        }
    }

    /**
     * Watching signed out (the guest) is allowed. Off by default: everyone watches as themselves, and a
     * profile linked to the guest asks again.
     */
    public boolean isGuestEnabled() {
        return mPrefs.getBoolean(GUEST_ENABLED_KEY, false);
    }

    /** The guest is on, and this isn't a kids profile: kids always stay on their own account */
    public boolean isGuestAllowed() {
        return isGuestEnabled() && !com.liskovsoft.smartyoutubetv2.common.utils.ParentGate.isKidsProfile(mContext);
    }

    public void setGuestEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(GUEST_ENABLED_KEY, enabled).apply();
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
            return isGuestAllowed() ? new Link(null) : null;
        }

        Account account = findByName(accounts, accountName);

        return account != null ? new Link(account) : null;
    }

    /** Link a profile to an account, or to the guest with null. */
    public void setLink(String profileName, @Nullable Account account) {
        String value = account != null && account.getName() != null ? account.getName() : GUEST;
        mPrefs.edit().putString(LINK_PREFIX + profileName, value).apply();
    }

    /** Note whether this Google TV profile is a kids profile (it was the active one just now) */
    public void setKidsProfile(String profileName, boolean kids) {
        if (profileName != null && mPrefs.getBoolean(KIDS_PREFIX + profileName, !kids) != kids) {
            mPrefs.edit().putBoolean(KIDS_PREFIX + profileName, kids).apply();
        }
    }

    /**
     * The account is a grown-up's: some profile seen as not a kids profile watches with it. The guest counts too:
     * signed out, nothing is filtered.
     */
    public boolean isGrownUpAccount(@Nullable Account account) {
        return isGrownUp(mPrefs.getAll(), account != null ? account.getName() : null);
    }

    /** {@link #isGrownUpAccount} over the saved links ("link:<profile>" = account, "kids:<profile>" = kids flag) */
    static boolean isGrownUp(java.util.Map<String, ?> saved, @Nullable String accountName) {
        if (accountName == null) {
            return true;
        }

        for (java.util.Map.Entry<String, ?> entry : saved.entrySet()) {
            String key = entry.getKey();

            if (key.startsWith(LINK_PREFIX) && accountName.equals(entry.getValue())) {
                Object kids = saved.get(KIDS_PREFIX + key.substring(LINK_PREFIX.length()));

                if (Boolean.FALSE.equals(kids)) {
                    return true;
                }
            }
        }

        return false;
    }

    public void removeLink(String profileName) {
        mPrefs.edit().remove(LINK_PREFIX + profileName).apply();
    }

    /**
     * Links are saved under Hearth's lasting profile key ("user:11", {@link
     * com.liskovsoft.smartyoutubetv2.common.utils.HearthProfile#key}), so a renamed Google TV profile keeps its
     * account. Remembers the profile's current name for showing, and moves a link (and kids flag) saved under
     * that name, from before Hearth had keys, to the key.
     */
    public void adoptName(String key, @Nullable String name) {
        if (key == null || name == null || key.equals(name)) {
            return;
        }

        SharedPreferences.Editor editor = mPrefs.edit();
        if (!name.equals(mPrefs.getString(NAME_PREFIX + key, null))) {
            editor.putString(NAME_PREFIX + key, name);
        }

        String oldLink = mPrefs.getString(LINK_PREFIX + name, null);
        if (oldLink != null) {
            if (!mPrefs.contains(LINK_PREFIX + key)) {
                editor.putString(LINK_PREFIX + key, oldLink);
                if (mPrefs.contains(KIDS_PREFIX + name)) {
                    editor.putBoolean(KIDS_PREFIX + key, mPrefs.getBoolean(KIDS_PREFIX + name, false));
                }
            }
            editor.remove(LINK_PREFIX + name).remove(KIDS_PREFIX + name);
        }
        editor.apply();
    }

    /** The name to show for a profile key: its name when last seen, or the key itself (a name, with an older Hearth). */
    public String getDisplayName(String key) {
        return mPrefs.getString(NAME_PREFIX + key, key);
    }

    /** Every Google TV profile seen so far (their keys), sorted by name. */
    public List<String> getProfileKeys() {
        List<String> keys = new ArrayList<>();

        for (String key : mPrefs.getAll().keySet()) {
            if (key.startsWith(LINK_PREFIX)) {
                keys.add(key.substring(LINK_PREFIX.length()));
            }
        }

        Collections.sort(keys, (a, b) -> getDisplayName(a).compareToIgnoreCase(getDisplayName(b)));

        return keys;
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
