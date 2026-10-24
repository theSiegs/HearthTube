package com.liskovsoft.smartyoutubetv2.common.utils;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;

import java.util.ArrayList;
import java.util.List;

/**
 * HearthTube: the picker listed one person twice, the same name once with the email and once without (a leftover entry
 * from signing in again). Such a twin stays out of the lists unless it is the one in use. Nothing is deleted.
 */
public final class AccountTwins {
    private AccountTwins() {
    }

    public static List<Account> withoutTwins(List<Account> accounts) {
        if (accounts == null || accounts.size() < 2) {
            return accounts;
        }

        List<Account> result = new ArrayList<>();

        for (Account account : accounts) {
            if (!isTwin(account, accounts)) {
                result.add(account);
            }
        }

        return result;
    }

    /** No email, not the one in use, and another entry of the same name has an email */
    private static boolean isTwin(Account account, List<Account> accounts) {
        if (account == null || hasEmail(account) || account.isSelected() || account.getName() == null) {
            return false;
        }

        for (Account other : accounts) {
            if (other != account && other != null && hasEmail(other) && account.getName().equalsIgnoreCase(other.getName())) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasEmail(Account account) {
        return account.getEmail() != null && !account.getEmail().isEmpty();
    }
}
