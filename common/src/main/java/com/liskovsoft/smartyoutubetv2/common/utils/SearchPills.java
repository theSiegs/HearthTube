package com.liskovsoft.smartyoutubetv2.common.utils;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * HearthTube: the search suggestions (the pills under the search bar: YouTube's as you type, the search history before
 * you do) don't lead to what the results leave out anyway. While Shorts are hidden (kids profiles always, grown-ups with
 * Show Shorts off) that's TikTok and Instagram clips, reels and Shorts ({@link KidsShorts}); in every profile, blocked
 * words ({@link KeywordFilter}). The same rules as for the videos, so they stay in one place.
 * <p>
 * When what's typed is such a search itself ("tiktok"), there are no pills at all: they'd be more of the same, or
 * worse, look like a way around it. The results then say why they're empty, as they already do.
 */
public final class SearchPills {
    private SearchPills() {
    }

    /** What was typed is a search for what's left out: no pills for it */
    public static boolean isHidden(String query) {
        return isHidden(query, KidsShorts.isHidingShorts(), KeywordFilter.getMatcher());
    }

    /** The pills to show for what was typed; null for none */
    @Nullable
    public static List<Tag> filter(String query, @Nullable List<Tag> pills) {
        return filter(query, pills, KidsShorts.isHidingShorts(), KeywordFilter.getMatcher());
    }

    static boolean isHidden(String text, boolean hidingShorts, KeywordMatcher blockedWords) {
        return text != null && ((hidingShorts && KidsShorts.isClipsSearch(text)) || blockedWords.matches(text));
    }

    @Nullable
    static List<Tag> filter(String query, @Nullable List<Tag> pills, boolean hidingShorts, KeywordMatcher blockedWords) {
        if (pills == null || isHidden(query, hidingShorts, blockedWords)) {
            return null;
        }

        List<Tag> shown = new ArrayList<>();

        for (Tag pill : pills) {
            if (pill != null && pill.tag != null && !isHidden(pill.tag, hidingShorts, blockedWords)) {
                shown.add(pill);
            }
        }

        return shown.isEmpty() ? null : shown;
    }
}
