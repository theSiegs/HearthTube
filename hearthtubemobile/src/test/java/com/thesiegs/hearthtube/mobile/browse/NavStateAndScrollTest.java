package com.thesiegs.hearthtube.mobile.browse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.HearthSections;

import org.junit.Test;

public class NavStateAndScrollTest {
    @Test
    public void theStateSurvivesBeingSavedAndRead() {
        NavState state = new NavState();
        state.show(MediaGroup.TYPE_MUSIC);
        state.show(HearthSections.TYPE_WATCH_LATER);

        NavState back = NavState.decode(state.encode());

        assertEquals(HearthSections.TYPE_WATCH_LATER, back.section());
        assertEquals(MediaGroup.TYPE_MUSIC, back.remembered(Destination.HOME));
        assertEquals(HearthSections.TYPE_WATCH_LATER, back.remembered(Destination.LIBRARY));
        assertEquals(-1, back.remembered(Destination.SUBSCRIPTIONS));
    }

    @Test
    public void junkIsAnEmptyState() {
        for (String junk : new String[] {null, "", "x", "abc;HOME=1", ";;;"}) {
            NavState state = NavState.decode(junk);
            assertEquals(-1, state.section());
            assertEquals(-1, state.remembered(Destination.HOME));
        }
    }

    @Test
    public void partlyBrokenChipsKeepWhatCanBeRead() {
        NavState state = NavState.decode("5;HOME=1,NOPE=2,LIBRARY=x,SUBSCRIPTIONS=3");
        assertEquals(5, state.section());
        assertEquals(1, state.remembered(Destination.HOME));
        assertEquals(-1, state.remembered(Destination.LIBRARY));
        assertEquals(3, state.remembered(Destination.SUBSCRIPTIONS));
    }

    @Test
    public void theBarsHideGoingDownAndComeBackGoingUp() {
        ScrollHide hide = new ScrollHide(true, 4);
        assertTrue(hide.isVisible());
        assertFalse(hide.onScrolled(2, false)); // jitter
        assertTrue(hide.onScrolled(20, false));
        assertFalse(hide.isVisible());
        assertFalse(hide.onScrolled(30, false)); // already hidden
        assertTrue(hide.onScrolled(-10, false));
        assertTrue(hide.isVisible());
    }

    @Test
    public void theBarsAreAlwaysThereAtTheTop() {
        ScrollHide hide = new ScrollHide(true, 4);
        hide.onScrolled(50, false);
        assertTrue(hide.onScrolled(0, true));
        assertTrue(hide.isVisible());
        assertFalse(hide.onScrolled(50, true)); // down while still at the top (overscroll): they stay
    }

    @Test
    public void aTabletNeverHidesThem() {
        ScrollHide hide = new ScrollHide(false, 4);
        assertFalse(hide.onScrolled(500, false));
        assertTrue(hide.isVisible());
    }

    @Test
    public void showBringsThemBack() {
        ScrollHide hide = new ScrollHide(true, 4);
        hide.onScrolled(50, false);
        assertTrue(hide.show());
        assertFalse(hide.show());
        assertTrue(hide.isVisible());
    }
}
