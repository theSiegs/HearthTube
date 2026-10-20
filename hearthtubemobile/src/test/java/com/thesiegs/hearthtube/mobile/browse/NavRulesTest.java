package com.thesiegs.hearthtube.mobile.browse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.HearthSections;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class NavRulesTest {
    private static final int HOME = MediaGroup.TYPE_HOME;
    private static final int SPORTS = MediaGroup.TYPE_SPORTS;
    private static final int MUSIC = MediaGroup.TYPE_MUSIC;
    private static final int SUBS = MediaGroup.TYPE_SUBSCRIPTIONS;
    private static final int LIBRARY = HearthSections.TYPE_LIBRARY;
    private static final int LATER = HearthSections.TYPE_WATCH_LATER;
    private static final int QUEUE = MediaGroup.TYPE_PLAYBACK_QUEUE;
    private static final int AMBIANCE = HearthSections.TYPE_AMBIANCE;

    /** The presenter's order: Home, Sports, Music, Subscriptions, Ambiance, Library, Watch later, Queued */
    private static final List<Integer> ALL = Arrays.asList(HOME, SPORTS, MUSIC, SUBS, AMBIANCE, LIBRARY, LATER, QUEUE);

    private static List<Integer> ids(List<Integer> positions, List<Integer> sectionIds) {
        java.util.ArrayList<Integer> out = new java.util.ArrayList<>();

        for (int at : positions) {
            out.add(sectionIds.get(at));
        }

        return out;
    }

    @Test
    public void anAdultSeesAllFourDestinations() {
        assertEquals(Arrays.asList(Destination.HOME, Destination.SUBSCRIPTIONS, Destination.LIBRARY, Destination.SEARCH),
                NavRules.bar(false, false, ALL));
    }

    @Test
    public void aKidWithNobodySignedInSeesNoBarAtAll() {
        assertTrue(NavRules.bar(true, true, ALL).isEmpty());
        assertTrue(NavRules.bar(true, true, Collections.<Integer>emptyList()).isEmpty());
    }

    @Test
    public void noSectionsYetIsNoBar() {
        assertTrue(NavRules.bar(false, false, Collections.<Integer>emptyList()).isEmpty());
    }

    @Test
    public void aDestinationWithNothingForThisPersonIsHidden() {
        assertEquals(Arrays.asList(Destination.HOME, Destination.SEARCH),
                NavRules.bar(false, true, Arrays.asList(HOME, SPORTS)));
        // Only the play queue in Library, and a kid doesn't get it
        assertEquals(Arrays.asList(Destination.HOME, Destination.SUBSCRIPTIONS, Destination.SEARCH),
                NavRules.bar(false, true, Arrays.asList(HOME, SUBS, QUEUE)));
        assertEquals(Arrays.asList(Destination.HOME, Destination.SUBSCRIPTIONS, Destination.LIBRARY, Destination.SEARCH),
                NavRules.bar(false, false, Arrays.asList(HOME, SUBS, QUEUE)));
    }

    @Test
    public void homeChipsFollowTheTabsThenTheRestAndLeaveSubscriptionsAndLibraryOut() {
        // Tabs: Home, Subscriptions, Music, Ambiance, Library
        List<Integer> tabs = Arrays.asList(HOME, SUBS, MUSIC, AMBIANCE, LIBRARY);
        assertEquals(Arrays.asList(HOME, MUSIC, AMBIANCE, SPORTS),
                ids(NavRules.chips(Destination.HOME, false, ALL, tabs), ALL));
    }

    @Test
    public void libraryChipsHaveAFixedOrder() {
        assertEquals(Arrays.asList(LIBRARY, LATER, QUEUE),
                ids(NavRules.chips(Destination.LIBRARY, false, ALL, Collections.<Integer>emptyList()), ALL));
    }

    @Test
    public void aKidsLibraryOmitsTheQueue() {
        assertEquals(Arrays.asList(LIBRARY, LATER),
                ids(NavRules.chips(Destination.LIBRARY, true, ALL, Collections.<Integer>emptyList()), ALL));
    }

    @Test
    public void searchHasNoChips() {
        assertTrue(NavRules.chips(Destination.SEARCH, false, ALL, Collections.<Integer>emptyList()).isEmpty());
    }

    @Test
    public void chipsShowOnlyWhenThereIsAChoice() {
        assertFalse(NavRules.showChips(0));
        assertFalse(NavRules.showChips(1));
        assertTrue(NavRules.showChips(2));
    }

    @Test
    public void sectionsAreSortedUnderTheirDestination() {
        assertEquals(Destination.HOME, NavRules.destinationOf(HOME));
        assertEquals(Destination.HOME, NavRules.destinationOf(MUSIC));
        assertEquals(Destination.HOME, NavRules.destinationOf(99999)); // a pinned item
        assertEquals(Destination.SUBSCRIPTIONS, NavRules.destinationOf(SUBS));
        assertEquals(Destination.LIBRARY, NavRules.destinationOf(LATER));
        assertEquals(Destination.LIBRARY, NavRules.destinationOf(MediaGroup.TYPE_HISTORY));
    }

    @Test
    public void aDestinationOpensOnTheChipLastPickedThereElseItsFirst() {
        List<Integer> tabs = Collections.emptyList();
        assertEquals(LATER, NavRules.sectionFor(Destination.LIBRARY, false, ALL, tabs, LATER));
        assertEquals(LIBRARY, NavRules.sectionFor(Destination.LIBRARY, false, ALL, tabs, -1));
        assertEquals(LIBRARY, NavRules.sectionFor(Destination.LIBRARY, false, ALL, tabs, SPORTS)); // not a library chip
        assertEquals(SUBS, NavRules.sectionFor(Destination.SUBSCRIPTIONS, false, ALL, tabs, -1));
    }

    @Test
    public void aRememberedChipThatIsNoLongerAllowedIsNotUsed() {
        // The kid's copy no longer has the queue
        assertEquals(LIBRARY, NavRules.sectionFor(Destination.LIBRARY, true, ALL, Collections.<Integer>emptyList(), QUEUE));
    }

    @Test
    public void aDestinationWithoutChipsOpensNothing() {
        assertEquals(-1, NavRules.sectionFor(Destination.LIBRARY, false, Arrays.asList(HOME), Collections.<Integer>emptyList(), -1));
        assertEquals(-1, NavRules.sectionFor(Destination.SEARCH, false, ALL, Collections.<Integer>emptyList(), -1));
    }

    @Test
    public void backGoesHomeFromAnywhereElseAndLeavesAtHome() {
        assertEquals(NavRules.Back.LEAVE, NavRules.back(Destination.HOME));
        assertEquals(NavRules.Back.GO_HOME, NavRules.back(Destination.SUBSCRIPTIONS));
        assertEquals(NavRules.Back.GO_HOME, NavRules.back(Destination.LIBRARY));
    }
}
