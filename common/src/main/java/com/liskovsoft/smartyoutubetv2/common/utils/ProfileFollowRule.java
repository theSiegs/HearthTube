package com.liskovsoft.smartyoutubetv2.common.utils;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.ProfileLinkData;

import java.util.ArrayList;
import java.util.List;

/**
 * Which YouTube account a launch carries on with, decided apart from Android so every branch can be tested.
 * Two situations need different rules, and the caller says which one it is in:
 * <ul>
 * <li><b>One-account profile</b> (a kid's profile: a Google TV profile that is its own Android user, or a kids phone).
 * One account is the normal case. Nothing is asked and no link is written: the account that is selected stays, else
 * the only one is selected. With several accounts and none selected (a parent signed in two, or one Google account
 * has several channels) the app never picks one: it is the "Ask a parent to sign in" state, and the parent chooses
 * (right after signing in, or in the PIN-protected Accounts page). The kid never gets a chooser.</li>
 * <li><b>The owner's user</b>, shared by adults who each have their own Google TV profile. The profile (Hearth's
 * lasting key) is linked to an account: use that one; else the account named like the profile's first name, if just
 * one is; else ask in the picker, which offers the accounts no other profile watches with, and "Sign in". A link
 * or a cast (a deep link) never takes the account that is selected away. Without Hearth's key there's nothing to
 * link to, and the usual launch applies.</li>
 * </ul>
 */
public final class ProfileFollowRule {
    public enum Kind {
        /** Not decided here: the usual launch applies (native account dialog, picker setting, the welcome when nobody is signed in) */
        STANDARD,
        /** Carry on with {@link Decision#account} */
        USE_ACCOUNT,
        /** Ask in the picker (it offers the accounts for this profile, see {@link #offered}) */
        ASK,
        /** A kid's profile with several accounts and none selected: nothing opens until a parent chooses one */
        ASK_PARENT
    }

    public static final class Decision {
        public final Kind kind;
        /** USE_ACCOUNT: the account to watch with (it may be the selected one already) */
        @Nullable
        public final Account account;
        /** USE_ACCOUNT: save it as this profile's account */
        public final boolean link;
        /** USE_ACCOUNT: it was picked by the profile's name, and a short message says so */
        public final boolean guessed;
        /** ASK: put the selected account away first (it stays signed in), it isn't this profile's */
        public final boolean deselect;

        private Decision(Kind kind, @Nullable Account account, boolean link, boolean guessed, boolean deselect) {
            this.kind = kind;
            this.account = account;
            this.link = link;
            this.guessed = guessed;
            this.deselect = deselect;
        }

        static Decision standard() {
            return new Decision(Kind.STANDARD, null, false, false, false);
        }

        static Decision use(Account account, boolean link, boolean guessed) {
            return new Decision(Kind.USE_ACCOUNT, account, link, guessed, false);
        }

        static Decision askParent() {
            return new Decision(Kind.ASK_PARENT, null, false, false, false);
        }

        static Decision ask(boolean deselect) {
            return new Decision(Kind.ASK, null, false, false, deselect);
        }
    }

    private ProfileFollowRule() {
    }

    /**
     * @param oneAccountProfile a kid's profile (see above); false for the owner's user and for standalone adults
     * @param followEnabled     the "follow the Google TV profile" setting (owner's user only)
     * @param hearthKey         Hearth's lasting key of the active Google TV profile, null without Hearth
     * @param hearthName        the profile's name (its first name is guessed against the accounts)
     * @param linked            the account linked to the profile, null when there's no link or its account is gone
     * @param accounts          the accounts signed in
     * @param selected          the account selected now
     * @param deepLink          the launch carries something to open (a link, a cast, a search)
     * @param linkedElsewhere   the accounts that other profiles are linked to (one login per profile)
     */
    public static Decision decide(boolean oneAccountProfile, boolean followEnabled, @Nullable String hearthKey,
                                  @Nullable String hearthName, @Nullable Account linked, @Nullable List<Account> accounts,
                                  @Nullable Account selected, boolean deepLink, @Nullable List<Account> linkedElsewhere) {
        // Nobody signed in: the welcome asks for that (the sign-in is the parent's in a kid's profile, LaunchGate)
        if (accounts == null || accounts.isEmpty()) {
            return Decision.standard();
        }

        if (oneAccountProfile) {
            Account keep = find(accounts, selected);

            if (keep != null) {
                return Decision.use(keep, false, false);
            }

            return accounts.size() == 1 ? Decision.use(accounts.get(0), false, false) : Decision.askParent();
        }

        if (!followEnabled || hearthKey == null) {
            return Decision.standard();
        }

        if (linked != null) {
            return Decision.use(linked, false, false);
        }

        Account guess = ProfileLinkData.guessAccount(hearthName, notIn(accounts, linkedElsewhere));
        if (guess != null) {
            return Decision.use(guess, true, true);
        }

        // A link or a cast plays first, with the account selected now
        if (deepLink) {
            return Decision.standard();
        }

        // One login per Google TV profile: nobody else's account meanwhile
        return Decision.ask(selected != null);
    }

    /**
     * The accounts the picker offers while following a Google TV profile: only its own account (and that account's
     * channels, YouTube lists each as an account with the same email), or, with no link yet, the accounts no other
     * profile is linked to.
     */
    public static List<Account> offered(@Nullable Account linked, List<Account> accounts, @Nullable List<Account> linkedElsewhere) {
        List<Account> own = new ArrayList<>();

        for (Account account : accounts) {
            if (linked != null ? isSamePerson(account, linked) : !contains(linkedElsewhere, account)) {
                own.add(account);
            }
        }

        return own;
    }

    /** The same Google account: by email when both have one, else by name */
    public static boolean isSamePerson(@Nullable Account account, @Nullable Account other) {
        if (account == null || other == null) {
            return false;
        }

        if (account.getEmail() != null && other.getEmail() != null) {
            return account.getEmail().equalsIgnoreCase(other.getEmail());
        }

        return Helpers.equals(account.getName(), other.getName());
    }

    /** The account in the list with the same name, or null */
    @Nullable
    private static Account find(List<Account> accounts, @Nullable Account wanted) {
        if (wanted == null) {
            return null;
        }

        for (Account account : accounts) {
            if (Helpers.equals(account.getName(), wanted.getName())) {
                return account;
            }
        }

        return null;
    }

    private static boolean contains(@Nullable List<Account> accounts, Account account) {
        if (accounts != null) {
            for (Account other : accounts) {
                if (isSamePerson(account, other)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static List<Account> notIn(List<Account> accounts, @Nullable List<Account> excluded) {
        List<Account> rest = new ArrayList<>();

        for (Account account : accounts) {
            if (!contains(excluded, account)) {
                rest.add(account);
            }
        }

        return rest;
    }
}
