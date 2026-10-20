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
    public void removingAnAccountClearsEveryLinkToItAndNoOther() {
        Map<String, Object> saved = new HashMap<>();
        saved.put("link:user:10", "Alex Rivera");
        saved.put("link:user:11", "Sam Rivera");
        saved.put("link:user:12", "Alex Rivera");
        saved.put("kids:user:11", true); // an older version's flag: never touched

        List<String> keys = ProfileLinkData.linksTo(saved, "Alex Rivera");

        assertTrue(keys.contains("link:user:10"));
        assertTrue(keys.contains("link:user:12"));
        assertFalse(keys.contains("link:user:11"));
        assertTrue(keys.size() == 2);
    }

    @Test
    public void thePurgeRemovesEveryKidsFlagAndTheLinksOfKidsProfiles() {
        Map<String, Object> saved = new HashMap<>();
        saved.put("link:user:10", "Alex Rivera");
        saved.put("kids:user:10", false); // an adult's profile: its link stays
        saved.put("link:user:11", "Sam Rivera");
        saved.put("kids:user:11", true);
        saved.put("link:user:12", "Jordan Smith"); // kids flag never seen: stays
        saved.put("name:user:10", "Alex");

        List<String> keys = ProfileLinkData.obsoleteKeys(saved, false);

        assertTrue(keys.contains("kids:user:10"));
        assertTrue(keys.contains("kids:user:11"));
        assertTrue(keys.contains("link:user:11"));
        assertFalse(keys.contains("link:user:10"));
        assertFalse(keys.contains("link:user:12"));
        assertFalse(keys.contains("name:user:10"));
        assertTrue(keys.size() == 3);
    }

    @Test
    public void inAKidsOwnCopyThePurgeRemovesEveryLinkAndName() {
        Map<String, Object> saved = new HashMap<>();
        saved.put("link:user:10", "Sam Rivera");
        saved.put("name:user:10", "Sam");
        saved.put("follow_google_tv_profile", true);
        saved.put("parent_pin", "sha256:00");

        List<String> keys = ProfileLinkData.obsoleteKeys(saved, true);

        assertTrue(keys.contains("link:user:10"));
        assertTrue(keys.contains("name:user:10"));
        assertFalse(keys.contains("follow_google_tv_profile"));
        assertFalse(keys.contains("parent_pin")); // the PIN is never touched
        assertTrue(keys.size() == 2);
    }

    @Test
    public void thePurgeOnNothingOrTwiceFindsNothing() {
        assertTrue(ProfileLinkData.obsoleteKeys(new HashMap<String, Object>(), false).isEmpty());
        assertTrue(ProfileLinkData.obsoleteKeys(new HashMap<String, Object>(), true).isEmpty());

        Map<String, Object> saved = new HashMap<>();
        saved.put("link:user:10", "Alex Rivera");
        saved.put("name:user:10", "Alex");
        assertTrue(ProfileLinkData.obsoleteKeys(saved, false).isEmpty()); // after a first run, nothing is left
    }
}
