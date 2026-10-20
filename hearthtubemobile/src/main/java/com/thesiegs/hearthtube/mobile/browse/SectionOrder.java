package com.thesiegs.hearthtube.mobile.browse;

import java.util.ArrayList;
import java.util.List;

/**
 * The order of the section pills. The presenter lists the sections in its own order (Home, Sports, Live, News, Music,
 * Subscriptions...), which buries Subscriptions and Library; the TV shows the person's tabs first, in the order they
 * chose (Edit menu and tabs), so the phone does too, then every other section in the presenter's order. Plain Java, so
 * it is tested apart from Android.
 */
final class SectionOrder {
    private SectionOrder() {
    }

    /**
     * Where each pill goes: positions in {@code sectionIds} (the presenter's order), the tabs first in {@code tabIds}'s
     * order, a tab with no such section skipped, then the rest as they were
     */
    static List<Integer> display(List<Integer> sectionIds, List<Integer> tabIds) {
        List<Integer> order = new ArrayList<>();

        for (int tab : tabIds) {
            int at = sectionIds.indexOf(tab);

            if (at >= 0 && !order.contains(at)) {
                order.add(at);
            }
        }

        for (int i = 0; i < sectionIds.size(); i++) {
            if (!order.contains(i)) {
                order.add(i);
            }
        }

        return order;
    }
}
