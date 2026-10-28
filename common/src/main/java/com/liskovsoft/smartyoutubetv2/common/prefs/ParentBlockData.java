package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.utils.KeywordFilter;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * HearthTube: what a parent blocked for this profile, kept apart from what the viewer hid themselves (HiddenVideoData,
 * BlockedChannelData, the blocked words). A kid can hide and block on their own with no PIN and take their own back; a
 * parent's blocks are locked in a kids profile: only a parent (the PIN) takes one back, and one that came from Hearth
 * is taken back where it was set (Hearth, or Home Assistant through it).
 * <p>
 * Two sources: {@link Item#HEARTH}, the lists Hearth hands over (provider contract 8, {@code parent_blocks}; replaced
 * as a whole each time Hearth speaks), and {@link Item#LOCAL}, what a parent blocked on this device through the PIN.
 * Both apply. Also remembers what the viewer blocked themselves ("kid made"), so that is the only thing a kid may
 * take back without the PIN: what was blocked before this existed (who did it isn't known) keeps asking for the PIN.
 */
public class ParentBlockData implements ProfileChangeListener {
    private static final String PARENT_BLOCKS_DATA = "parent_blocks";
    private static final String KID_MADE_DATA = "kid_made_blocks";
    static final int MAX = 1000;
    @SuppressLint("StaticFieldLeak")
    private static ParentBlockData sInstance;
    private final AppPrefs mPrefs;
    private final List<Item> mItems = new ArrayList<>();
    private final LinkedHashSet<String> mKidMade = new LinkedHashSet<>();
    private final Runnable mPersistStateInt = this::persistStateInt;

    public static final class Item {
        public static final char HEARTH = 'h';
        public static final char LOCAL = 'l';
        public static final char VIDEO = 'v';
        public static final char CHANNEL = 'c';
        public static final char WORD = 'w';

        public final char source;
        public final char kind;
        /** A video id, a channel id, or the word */
        public final String id;
        /** What is shown: a title, a channel's name, the word */
        public final String label;

        public Item(char source, char kind, String id, String label) {
            this.source = source;
            this.kind = kind;
            this.id = kind == WORD ? foldWord(id) : id;
            this.label = label == null || label.trim().isEmpty() ? this.id : label;
        }

        boolean sameBlock(Item other) {
            return kind == other.kind && id.equals(other.id);
        }
    }

    private ParentBlockData(Context context) {
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        restoreState();
    }

    public static ParentBlockData instance(Context context) {
        if (sInstance == null) {
            sInstance = new ParentBlockData(context.getApplicationContext());
        }

        return sInstance;
    }

    /** A word as it is compared: trimmed, one space between words, lower case. Null when there is nothing in it. */
    static String foldWord(String word) {
        if (word == null) {
            return null;
        }

        String folded = word.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return folded.isEmpty() ? null : folded;
    }

    public synchronized boolean isVideoBlocked(String videoId) {
        return has(Item.VIDEO, videoId);
    }

    public synchronized boolean isChannelBlocked(String channelId) {
        return has(Item.CHANNEL, channelId);
    }

    public synchronized boolean isEmpty() {
        return mItems.isEmpty();
    }

    private boolean has(char kind, String id) {
        if (id == null || mItems.isEmpty()) {
            return false;
        }

        for (Item item : mItems) {
            if (item.kind == kind && item.id.equals(id)) {
                return true;
            }
        }

        return false;
    }

    /** Both sources, of one kind: {@link Item#VIDEO}, {@link Item#CHANNEL} or {@link Item#WORD}. A block in both is listed once. */
    public synchronized List<Item> getItems(char kind) {
        List<Item> result = new ArrayList<>();

        for (Item item : mItems) {
            if (item.kind != kind) {
                continue;
            }

            boolean listed = false;

            for (Item other : result) {
                listed |= other.sameBlock(item);
            }

            if (!listed) {
                result.add(item);
            }
        }

        return result;
    }

    /** The parent's words, for the word filter */
    public synchronized List<String> getWords() {
        List<String> words = new ArrayList<>();

        for (Item item : getItems(Item.WORD)) {
            words.add(item.id);
        }

        return words;
    }

    /**
     * What Hearth handed over for this profile ({@code {"videos":[…],"channels":[…],"words":[…]}} or null for none),
     * as the whole of its source: what it no longer lists goes. Returns true when anything changed.
     */
    public synchronized boolean setFromHearth(String json) {
        List<Item> fromHearth = parseHearth(json);
        List<Item> next = new ArrayList<>();

        for (Item item : mItems) {
            if (item.source != Item.HEARTH) {
                next.add(item);
            }
        }

        next.addAll(fromHearth);

        if (sameItems(mItems, next)) {
            return false;
        }

        mItems.clear();
        mItems.addAll(next);
        changed();
        return true;
    }

    /** A parent blocked it here (behind the PIN) */
    public synchronized void addLocal(char kind, String id, String label) {
        Item item = new Item(Item.LOCAL, kind, id, label);

        if (item.id == null) {
            return;
        }

        for (Item have : mItems) {
            if (have.source == Item.LOCAL && have.sameBlock(item)) {
                return;
            }
        }

        mItems.add(item);

        while (mItems.size() > MAX) {
            mItems.remove(0);
        }

        changed();
    }

    /** A parent took a block they set here back; false for one that came from Hearth (taken back there) */
    public synchronized boolean removeLocal(char kind, String id) {
        for (int i = mItems.size() - 1; i >= 0; i--) {
            Item item = mItems.get(i);

            if (item.source == Item.LOCAL && item.kind == kind && item.id.equals(id)) {
                mItems.remove(i);
                changed();
                return true;
            }
        }

        return false;
    }

    /** What the lists from Hearth look like as items (ids that aren't ids, and empty words, dropped) */
    static List<Item> parseHearth(String json) {
        List<Item> items = new ArrayList<>();

        if (json == null || json.isEmpty()) {
            return items;
        }

        try {
            org.json.JSONObject lists = new org.json.JSONObject(json);
            add(items, lists.optJSONArray("videos"), Item.VIDEO);
            add(items, lists.optJSONArray("channels"), Item.CHANNEL);
            add(items, lists.optJSONArray("words"), Item.WORD);
        } catch (org.json.JSONException e) {
            // Not a list: nothing blocked from there
        }

        return items;
    }

    private static void add(List<Item> into, org.json.JSONArray array, char kind) {
        if (array == null) {
            return;
        }

        Set<String> seen = new LinkedHashSet<>();

        for (int i = 0; i < array.length() && seen.size() < MAX; i++) {
            String value = array.optString(i, null);
            Item item = value == null ? null : new Item(Item.HEARTH, kind, value, null);

            if (item != null && item.id != null && (kind == Item.WORD || value.matches("[A-Za-z0-9_-]{5,40}")) && seen.add(item.id)) {
                into.add(item);
            }
        }
    }

    private static boolean sameItems(List<Item> a, List<Item> b) {
        if (a.size() != b.size()) {
            return false;
        }

        for (int i = 0; i < a.size(); i++) {
            Item x = a.get(i);
            Item y = b.get(i);

            if (x.source != y.source || !x.sameBlock(y) || !x.label.equals(y.label)) {
                return false;
            }
        }

        return true;
    }

    private void changed() {
        // The word filter folds its words once: it reads the parent's again
        KeywordFilter.onProfileChanged();
        persistState();
    }

    /** In a kids profile: the viewer blocked this themselves (the only kind they may take back without the PIN) */
    public static void kidMade(Context context, char kind, String key) {
        if (context != null && com.liskovsoft.smartyoutubetv2.common.utils.ParentGate.isKidsProfile(context)) {
            instance(context).markKidMade(kind, key);
            // And up to Hearth, for a parent to see (contract 10)
            com.liskovsoft.smartyoutubetv2.common.utils.ParentChannel.syncSoon(context);
        }
    }

    /**
     * Takes a block back: at once outside a kids profile or for what the kid blocked themselves, else after the parent
     * PIN (a parent may have set it, or it was blocked before who did was kept).
     */
    public static void unblockGate(Context context, char kind, String key, Runnable unblock) {
        ParentBlockData data = instance(context);

        if (!com.liskovsoft.smartyoutubetv2.common.utils.ParentGate.isKidsProfile(context) || data.isKidMade(kind, key)) {
            unblock.run();
            data.unmarkKidMade(kind, key);
            com.liskovsoft.smartyoutubetv2.common.utils.ParentChannel.syncSoon(context);
            return;
        }

        com.liskovsoft.smartyoutubetv2.common.utils.ParentGate.run(context, unblock);
    }

    // What the viewer blocked themselves: the only blocks a kid may take back with no PIN

    public synchronized void markKidMade(char kind, String key) {
        String made = kidKey(kind, key);

        if (made != null && mKidMade.add(made)) {
            while (mKidMade.size() > MAX) {
                mKidMade.remove(mKidMade.iterator().next());
            }

            persistState();
        }
    }

    public synchronized void unmarkKidMade(char kind, String key) {
        String made = kidKey(kind, key);

        if (made != null && mKidMade.remove(made)) {
            persistState();
        }
    }

    public synchronized boolean isKidMade(char kind, String key) {
        String made = kidKey(kind, key);
        return made != null && mKidMade.contains(made);
    }

    private static String kidKey(char kind, String key) {
        String id = kind == Item.WORD ? foldWord(key) : key;
        return id == null || id.isEmpty() ? null : kind + ":" + id;
    }

    private synchronized void restoreState() {
        mItems.clear();
        mKidMade.clear();

        for (String spec : Helpers.parseList(Helpers.splitData(mPrefs.getProfileData(PARENT_BLOCKS_DATA)), 0, item -> item)) {
            String[] fields = Helpers.splitObj(spec);

            if (fields != null && fields.length >= 3 && fields[0] != null && fields[1] != null && fields[0].length() == 1 && fields[1].length() == 1) {
                Item item = new Item(fields[0].charAt(0), fields[1].charAt(0), Helpers.parseStr(fields[2]),
                        fields.length > 3 ? Helpers.parseStr(fields[3]) : null);

                if (item.id != null) {
                    mItems.add(item);
                }
            }
        }

        for (String made : Helpers.parseList(Helpers.splitData(mPrefs.getProfileData(KID_MADE_DATA)), 0, item -> Helpers.parseStr(item))) {
            if (made != null) {
                mKidMade.add(made);
            }
        }
    }

    public void persistNow() {
        Utils.post(mPersistStateInt);
    }

    private void persistState() {
        Utils.postDelayed(mPersistStateInt, 2_000);
    }

    private synchronized void persistStateInt() {
        List<String> specs = new ArrayList<>();

        for (Item item : mItems) {
            specs.add(Helpers.mergeObj(String.valueOf(item.source), String.valueOf(item.kind), item.id, item.label));
        }

        mPrefs.setProfileData(PARENT_BLOCKS_DATA, Helpers.mergeData(specs));
        mPrefs.setProfileData(KID_MADE_DATA, Helpers.mergeData(new ArrayList<>(mKidMade)));
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }
}
