package com.thesiegs.hearthtube.mobile.browse;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.HearthSections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Which destinations the bar shows and which section chips each one has. The presenter lists the sections (the same
 * ones the TV has, in its own order); this sorts them under Home, Subscriptions and Library. Every kids rule of the home
 * screen stays: a kid with nobody signed in gets no bar at all (only "Ask a parent to sign in"), a destination with
 * nothing to show for this person is not on the bar, and a kid's Library leaves out the play queue (adding to it is a
 * parent's). Plain Java, so it is tested apart from Android.
 */
final class NavRules {
    /** What Back does on a destination */
    enum Back {
        GO_HOME, LEAVE
    }

    private static final int[] SUBSCRIPTIONS = {MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_CHANNEL_UPLOADS};
    /** The library's own list (Continue watching, History and the playlists are rows inside the first one), then the rest */
    private static final int[] LIBRARY = {
            HearthSections.TYPE_LIBRARY, MediaGroup.TYPE_HISTORY, HearthSections.TYPE_WATCH_LATER,
            MediaGroup.TYPE_PLAYBACK_QUEUE, MediaGroup.TYPE_USER_PLAYLISTS, MediaGroup.TYPE_MY_VIDEOS
    };

    private NavRules() {
    }

    /** Where a section lives: everything that is not a subscription or a library list is Home (the TV's other tabs) */
    static Destination destinationOf(int sectionId) {
        if (contains(SUBSCRIPTIONS, sectionId)) {
            return Destination.SUBSCRIPTIONS;
        }

        if (contains(LIBRARY, sectionId)) {
            return Destination.LIBRARY;
        }

        return Destination.HOME;
    }

    /**
     * The destinations on the bar, in order. Nobody to show anything to (a kid, nobody signed in) is an empty bar; a
     * destination with no section for this person is left out; Search is always there when there is something to search.
     */
    static List<Destination> bar(boolean gated, boolean kid, List<Integer> sectionIds) {
        List<Destination> bar = new ArrayList<>();

        // Nothing listed yet (the sections arrive a moment after the screen) is no bar, not a bar with only Search
        if (gated || sectionIds.isEmpty()) {
            return bar;
        }

        for (Destination destination : Arrays.asList(Destination.HOME, Destination.SUBSCRIPTIONS, Destination.LIBRARY)) {
            if (!chips(destination, kid, sectionIds, Collections.<Integer>emptyList()).isEmpty()) {
                bar.add(destination);
            }
        }

        bar.add(Destination.SEARCH);
        return bar;
    }

    /**
     * The positions in {@code sectionIds} (the presenter's order) of a destination's chips, in the order they show: Home
     * has the person's tabs first as the TV does (see {@link SectionOrder}), Subscriptions and Library a fixed order.
     */
    static List<Integer> chips(Destination destination, boolean kid, List<Integer> sectionIds, List<Integer> tabIds) {
        List<Integer> chips = new ArrayList<>();

        if (destination == Destination.SEARCH) {
            return chips;
        }

        if (destination == Destination.HOME) {
            for (int at : SectionOrder.display(sectionIds, tabIds)) {
                if (destinationOf(sectionIds.get(at)) == Destination.HOME) {
                    chips.add(at);
                }
            }

            return chips;
        }

        for (int id : destination == Destination.SUBSCRIPTIONS ? SUBSCRIPTIONS : LIBRARY) {
            int at = sectionIds.indexOf(id);

            if (at >= 0 && !(kid && id == MediaGroup.TYPE_PLAYBACK_QUEUE)) {
                chips.add(at);
            }
        }

        return chips;
    }

    /** The row of chips is drawn only when there is a choice to make */
    static boolean showChips(int count) {
        return count >= 2;
    }

    /**
     * The section a destination opens on: the chip the person last picked there when it is still there, else its first
     * chip. -1 when it has none.
     */
    static int sectionFor(Destination destination, boolean kid, List<Integer> sectionIds, List<Integer> tabIds, int remembered) {
        List<Integer> chips = chips(destination, kid, sectionIds, tabIds);

        if (chips.isEmpty()) {
            return -1;
        }

        for (int at : chips) {
            if (sectionIds.get(at) == remembered) {
                return remembered;
            }
        }

        return sectionIds.get(chips.get(0));
    }

    /** Back from anywhere but Home goes to Home; Back at Home leaves the app */
    static Back back(Destination current) {
        return current == Destination.HOME ? Back.LEAVE : Back.GO_HOME;
    }

    private static boolean contains(int[] ids, int id) {
        for (int each : ids) {
            if (each == id) {
                return true;
            }
        }

        return false;
    }
}
