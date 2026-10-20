package com.thesiegs.hearthtube.mobile.browse;

/**
 * The four places the bottom bar (a side rail on a tablet) goes to. Search is not a section of the presenter: it opens
 * the search screen. Plain Java, so the rules around it are tested apart from Android.
 */
enum Destination {
    HOME, SUBSCRIPTIONS, LIBRARY, SEARCH;

    /** The destination named in a saved state, or null */
    static Destination parse(String name) {
        for (Destination destination : values()) {
            if (destination.name().equals(name)) {
                return destination;
            }
        }

        return null;
    }
}
