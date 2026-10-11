package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;

import org.junit.Test;

public class ProfilePickerPresenterTest {
    @Test
    public void aChannelOfTheSameGoogleAccountIsTheSamePerson() {
        assertTrue(ProfilePickerPresenter.isSamePerson(account("Alex Rivera", "alex@example.com"),
                account("Alex's Workshop", "ALEX@example.com")));
    }

    @Test
    public void anotherGoogleAccountIsNot() {
        assertFalse(ProfilePickerPresenter.isSamePerson(account("Alex Rivera", "alex@example.com"),
                account("Sam Rivera", "sam@example.com")));
    }

    @Test
    public void withoutEmailsTheNameDecides() {
        assertTrue(ProfilePickerPresenter.isSamePerson(account("Alex", null), account("Alex", "alex@example.com")));
        assertFalse(ProfilePickerPresenter.isSamePerson(account("Alex", null), account("Sam", null)));
        assertFalse(ProfilePickerPresenter.isSamePerson(null, account("Sam", null)));
    }

    private static Account account(String name, String email) {
        return new Account() {
            @Override public int getId() { return 0; }
            @Override public String getName() { return name; }
            @Override public String getEmail() { return email; }
            @Override public String getAvatarImageUrl() { return null; }
            @Override public boolean isSelected() { return false; }
            @Override public boolean isEmpty() { return false; }
        };
    }
}
