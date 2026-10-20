package com.liskovsoft.smartyoutubetv2.common.prefs;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Test
    public void adultAccountIsOneLinkedFromAnAdultsProfile() {
        Map<String, Object> saved = new HashMap<>();
        saved.put("link:Alex", "Alex Rivera");
        saved.put("kids:Alex", false);
        saved.put("link:Sam", "Sam Rivera");
        saved.put("kids:Sam", true);

        assertTrue(ProfileLinkData.isAdult(saved, "Alex Rivera"));
        assertFalse(ProfileLinkData.isAdult(saved, "Sam Rivera"));
    }

    @Test
    public void unknownAccountsAreNotAdultsButTheGuestIs() {
        Map<String, Object> saved = new HashMap<>();
        saved.put("link:Alex", "Alex Rivera"); // kids flag never seen

        assertFalse(ProfileLinkData.isAdult(saved, "Alex Rivera"));
        assertFalse(ProfileLinkData.isAdult(saved, "Jordan Rivera"));
        assertTrue(ProfileLinkData.isAdult(saved, null));
    }

    @Test
    public void anAccountSharedWithAnAdultsProfileIsAnAdults() {
        Map<String, Object> saved = new HashMap<>();
        saved.put("link:Sam", "Alex Rivera");
        saved.put("kids:Sam", true);
        saved.put("link:Living room", "Alex Rivera");
        saved.put("kids:Living room", false);

        assertTrue(ProfileLinkData.isAdult(saved, "Alex Rivera"));
    }

    @Test
    public void removingAnAccountClearsEveryLinkToItAndNoOther() {
        Map<String, Object> saved = new HashMap<>();
        saved.put("link:user:10", "Alex Rivera");
        saved.put("link:user:11", "Sam Rivera");
        saved.put("link:user:12", "Alex Rivera");
        saved.put("kids:user:11", true);

        List<String> keys = ProfileLinkData.linksTo(saved, "Alex Rivera");

        assertTrue(keys.contains("link:user:10"));
        assertTrue(keys.contains("link:user:12"));
        assertFalse(keys.contains("link:user:11"));
        assertTrue(keys.size() == 2);
    }
}
