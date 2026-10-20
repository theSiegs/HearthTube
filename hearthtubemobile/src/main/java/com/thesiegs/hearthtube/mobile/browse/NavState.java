package com.thesiegs.hearthtube.mobile.browse;

import java.util.EnumMap;
import java.util.Map;

/**
 * What the person has picked: the section on show and, for each destination, the chip they last picked there (so
 * coming back to Library lands on Watch later again). It is kept as a short string in the activity's saved state, so a
 * rotation or a restart after the system stopped the app opens where they were. Plain Java.
 */
final class NavState {
    private int mSection = -1;
    private final Map<Destination, Integer> mChips = new EnumMap<>(Destination.class);

    /** The section on show, -1 when nothing was picked yet */
    int section() {
        return mSection;
    }

    /** A chip was picked, or the presenter showed a section */
    void show(int sectionId) {
        mSection = sectionId;
        mChips.put(NavRules.destinationOf(sectionId), sectionId);
    }

    /** The chip last picked in a destination, -1 for none */
    int remembered(Destination destination) {
        Integer id = mChips.get(destination);
        return id != null ? id : -1;
    }

    /** "section;HOME=1,LIBRARY=92" */
    String encode() {
        StringBuilder out = new StringBuilder().append(mSection).append(';');
        boolean first = true;

        for (Map.Entry<Destination, Integer> entry : mChips.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(entry.getKey().name()).append('=').append(entry.getValue());
        }

        return out.toString();
    }

    /** Whatever was saved; nothing readable is an empty state */
    static NavState decode(String saved) {
        NavState state = new NavState();

        if (saved == null) {
            return state;
        }

        String[] parts = saved.split(";", -1);

        try {
            state.mSection = Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException e) {
            return new NavState();
        }

        if (parts.length > 1) {
            for (String pair : parts[1].split(",")) {
                String[] keyValue = pair.split("=");
                Destination destination = keyValue.length == 2 ? Destination.parse(keyValue[0]) : null;

                if (destination != null) {
                    try {
                        state.mChips.put(destination, Integer.parseInt(keyValue[1].trim()));
                    } catch (NumberFormatException e) {
                        // skip
                    }
                }
            }
        }

        return state;
    }
}
