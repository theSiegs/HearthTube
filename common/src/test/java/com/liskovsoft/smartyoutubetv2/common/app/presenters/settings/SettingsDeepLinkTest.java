package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** The "section" extra of Hearth's settings link */
public class SettingsDeepLinkTest {
    @Test
    public void knownSections() {
        assertEquals(SettingsDeepLink.GENERAL, SettingsDeepLink.sectionOf("general"));
        assertEquals(SettingsDeepLink.PLAYER, SettingsDeepLink.sectionOf("player"));
        assertEquals(SettingsDeepLink.ACCOUNTS, SettingsDeepLink.sectionOf("accounts"));
        assertEquals(SettingsDeepLink.BLOCKED_WORDS, SettingsDeepLink.sectionOf("blocked_words"));
        assertEquals(SettingsDeepLink.ABOUT, SettingsDeepLink.sectionOf("about"));
    }

    @Test
    public void spellingIsForgiven() {
        assertEquals(SettingsDeepLink.BLOCKED_WORDS, SettingsDeepLink.sectionOf(" Blocked_Words "));
        assertEquals(SettingsDeepLink.GENERAL, SettingsDeepLink.sectionOf("GENERAL"));
    }

    @Test
    public void anythingElseOpensTheMenu() {
        assertNull(SettingsDeepLink.sectionOf(null));
        assertNull(SettingsDeepLink.sectionOf(""));
        assertNull(SettingsDeepLink.sectionOf("bogus"));
        assertNull(SettingsDeepLink.sectionOf("blocked words"));
        assertNull(SettingsDeepLink.sectionOf("subtitles"));
    }
}
