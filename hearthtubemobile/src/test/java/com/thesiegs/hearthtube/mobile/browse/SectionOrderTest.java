package com.thesiegs.hearthtube.mobile.browse;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class SectionOrderTest {
    @Test
    public void tabsComeFirstInTheOrderTheyWereChosenThenTheRestAsTheyWere() {
        // The presenter's order: Home, Sports, Live, Music, Subscriptions, Library, Podcasts (ids as stand-ins)
        // The tabs: Home, Subscriptions, Music, Library
        assertEquals(Arrays.asList(0, 4, 3, 5, 1, 2, 6),
                SectionOrder.display(Arrays.asList(10, 20, 30, 40, 50, 60, 70), Arrays.asList(10, 50, 40, 60)));
    }

    @Test
    public void aTabWhoseSectionIsSwitchedOffIsSkipped() {
        assertEquals(Arrays.asList(1, 0, 2), SectionOrder.display(Arrays.asList(10, 20, 30), Arrays.asList(99, 20, 10)));
    }

    @Test
    public void withNoTabsTheSectionsKeepTheirOrder() {
        assertEquals(Arrays.asList(0, 1, 2), SectionOrder.display(Arrays.asList(10, 20, 30), Collections.emptyList()));
    }

    @Test
    public void aTabListedTwiceCountsOnce() {
        assertEquals(Arrays.asList(1, 0), SectionOrder.display(Arrays.asList(10, 20), Arrays.asList(20, 20)));
    }

    @Test
    public void noSectionsNoPills() {
        assertEquals(Collections.<Integer>emptyList(), SectionOrder.display(Collections.emptyList(), Arrays.asList(10)));
    }
}
