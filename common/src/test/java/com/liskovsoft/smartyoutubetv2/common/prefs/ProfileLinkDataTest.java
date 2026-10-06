package com.liskovsoft.smartyoutubetv2.common.prefs;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ProfileLinkDataTest {
    private static Account account(String name) {
        return new Account() {
            @Override public int getId() { return name.hashCode(); }
            @Override public String getName() { return name; }
            @Override public String getEmail() { return null; }
            @Override public String getAvatarImageUrl() { return null; }
            @Override public boolean isSelected() { return false; }
            @Override public boolean isEmpty() { return false; }
        };
    }

    @Test
    public void firstNameMatchesFullName() {
        Account alex = account("Alex Rivera");
        List<Account> accounts = Arrays.asList(account("Sam Rivera"), alex);

        assertSame(alex, ProfileLinkData.guessAccount("Alex", accounts));
        assertSame(alex, ProfileLinkData.guessAccount(" alex ", accounts));
    }

    @Test
    public void fullNameMatches() {
        Account alex = account("Alex  Rivera");

        assertSame(alex, ProfileLinkData.guessAccount("Alex Rivera", Arrays.asList(alex)));
    }

    @Test
    public void ambiguousOrMissingMatchAsks() {
        List<Account> accounts = Arrays.asList(account("Alex Rivera"), account("Alex Smith"));

        assertNull(ProfileLinkData.guessAccount("Alex", accounts));
        assertNull(ProfileLinkData.guessAccount("Jordan", accounts));
        assertNull(ProfileLinkData.guessAccount("Kev", accounts));
        assertNull(ProfileLinkData.guessAccount("", accounts));
        assertNull(ProfileLinkData.guessAccount(null, accounts));
        assertNull(ProfileLinkData.guessAccount("Alex", null));
    }

    @Test
    public void accountWithoutNameIsSkipped() {
        Account alex = account("Alex");

        assertSame(alex, ProfileLinkData.guessAccount("Alex", Arrays.asList(account(null), alex)));
    }
}
