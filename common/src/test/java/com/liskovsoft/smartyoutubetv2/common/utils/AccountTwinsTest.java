package com.liskovsoft.smartyoutubetv2.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class AccountTwinsTest {
    private static Account account(String name, String email, boolean selected) {
        return new Account() {
            @Override public int getId() { return 0; }
            @Override public String getName() { return name; }
            @Override public String getEmail() { return email; }
            @Override public String getAvatarImageUrl() { return null; }
            @Override public boolean isSelected() { return selected; }
            @Override public boolean isEmpty() { return false; }
        };
    }

    @Test
    public void theEmaillessTwinIsLeftOut() {
        Account plain = account("alex", null, false);
        Account full = account("alex", "alex@example.com", true);

        List<Account> result = AccountTwins.withoutTwins(Arrays.asList(plain, full));

        assertEquals(1, result.size());
        assertSame(full, result.get(0));
    }

    @Test
    public void theTwinInUseStays() {
        Account plain = account("alex", null, true);
        Account full = account("Alex", "alex@example.com", false);

        assertEquals(2, AccountTwins.withoutTwins(Arrays.asList(plain, full)).size());
    }

    @Test
    public void differentNamesAreNotTwins() {
        Account sam = account("sam", null, false);
        Account alex = account("alex", "alex@example.com", true);

        assertEquals(2, AccountTwins.withoutTwins(Arrays.asList(sam, alex)).size());
    }

    @Test
    public void twoWithEmailsStay() {
        Account one = account("alex", "a@example.com", false);
        Account two = account("alex", "b@example.com", true);

        assertEquals(2, AccountTwins.withoutTwins(Arrays.asList(one, two)).size());
    }

    @Test
    public void nullAndSingleListsPassThrough() {
        assertEquals(null, AccountTwins.withoutTwins(null));
        assertEquals(1, AccountTwins.withoutTwins(Arrays.asList(account("alex", null, true))).size());
    }
}
