package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import static com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.HearthSettings.Page;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class HearthSettingsTest {
    @Test
    public void theTvWithoutHearthHasEveryPage() {
        assertEquals(Arrays.asList(Page.values()), HearthSettings.pages(false, false));
    }

    @Test
    public void pagesAreInTheSameOrderOnBothApps() {
        List<Page> tv = HearthSettings.pages(false, false);
        List<Page> phone = HearthSettings.pages(true, false);

        // The phone's list is the TV's list with some pages left out, never reordered
        List<Page> tvOnly = tv;
        tvOnly.retainAll(phone);
        assertEquals(phone, tvOnly);
    }

    @Test
    public void thePhoneHasNoPagesAboutARemoteControlOrTheTv() {
        List<Page> phone = HearthSettings.pages(true, false);

        assertFalse(phone.contains(Page.CAST));
        assertFalse(phone.contains(Page.GENERAL));
        assertFalse(phone.contains(Page.SEARCH));
        assertFalse(phone.contains(Page.BACKUP));
        assertTrue(phone.containsAll(Arrays.asList(Page.ACCOUNTS, Page.LOOK, Page.PLAYER, Page.CONTENT, Page.ABOUT)));
    }

    @Test
    public void languageIsAskedForOnlyWithoutHearth() {
        assertTrue(HearthSettings.pages(false, false).contains(Page.LANGUAGE));
        assertFalse(HearthSettings.pages(false, true).contains(Page.LANGUAGE));
        assertFalse(HearthSettings.pages(true, true).contains(Page.LANGUAGE));
        assertTrue(HearthSettings.pages(true, false).contains(Page.LANGUAGE));
    }

    @Test
    public void accountsComeFirstAndAboutLast() {
        for (boolean mobile : new boolean[] {false, true}) {
            for (boolean hearth : new boolean[] {false, true}) {
                List<Page> pages = HearthSettings.pages(mobile, hearth);
                assertEquals(Page.ACCOUNTS, pages.get(0));
                assertEquals(Page.ABOUT, pages.get(pages.size() - 1));
            }
        }
    }
}
