package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.util.Pair;

import com.liskovsoft.smartyoutubetv2.common.prefs.BlockedChannelData;
import com.liskovsoft.smartyoutubetv2.common.prefs.HiddenVideoData;
import com.liskovsoft.smartyoutubetv2.common.prefs.ParentBlockData;
import com.liskovsoft.smartyoutubetv2.common.prefs.ParentBlockData.Item;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * HearthTube: what a kids profile hid or blocked itself (Not interested, Don't show this creator, its own blocked
 * words), sent up to Hearth for a parent to see (provider contract 10, {@code report_kid_blocks}), and what a parent
 * took back there coming down ({@code kid_unblocks}), which is then taken back here. See docs/parent-channel.md.
 * <p>
 * Hearth is sent the whole of it each time it changes ({"videos": [{"key", "label"}], "channels": [{"key", "id",
 * "label"}], "words": [...]}), so a missed one is put right by the next. A channel's key is its id, or its name when it
 * was blocked without one (the same key as {@link ParentBlockData#kidMade}).
 */
public final class KidBlocks {
    /** Each list is cut to the newest this many: a provider call's Bundle has to stay small */
    static final int MAX = 500;
    private static final int LABEL_MAX = 150;
    /** What Hearth last took, in this process: a restart sends it once more, which is harmless */
    private static volatile String sSent;

    private KidBlocks() {
    }

    /** The kid's own lists, as Hearth is sent them */
    public static String snapshot(Context context) throws JSONException {
        JSONArray videos = new JSONArray();
        for (Pair<String, String> video : HiddenVideoData.instance(context).getVideos()) {
            if (videos.length() >= MAX) break;
            videos.put(new JSONObject().put("key", video.first).put("label", cut(video.second)));
        }

        JSONArray channels = new JSONArray();
        List<Pair<String, String>> blocked = BlockedChannelData.instance(context).getChannelIdsWithNames();
        for (int i = blocked.size() - 1; i >= 0 && channels.length() < MAX; i--) {
            Pair<String, String> channel = blocked.get(i);
            String key = channel.first != null ? channel.first : channel.second;
            if (key == null) continue;
            JSONObject entry = new JSONObject().put("key", key).put("label", cut(channel.second != null ? channel.second : key));
            if (channel.first != null) entry.put("id", channel.first);
            channels.put(entry);
        }

        JSONArray words = new JSONArray();
        for (String word : KeywordFilter.getWords(context)) {
            if (words.length() >= MAX) break;
            words.put(cut(word));
        }

        return new JSONObject().put("videos", videos).put("channels", channels).put("words", words).toString();
    }

    /**
     * Takes back what a parent took back in Hearth ({"videos": [keys], "channels": [keys], "words": [...]}). Only the
     * kid's own lists are touched; what a parent blocked stays. True when anything was taken back.
     */
    public static boolean applyUndo(Context context, String json) {
        if (json == null || json.isEmpty()) {
            return false;
        }

        JSONObject undo;
        try {
            undo = new JSONObject(json);
        } catch (JSONException e) {
            return false;
        }

        boolean changed = false;
        ParentBlockData made = ParentBlockData.instance(context);

        HiddenVideoData hidden = HiddenVideoData.instance(context);
        JSONArray videos = undo.optJSONArray("videos");
        for (int i = 0; videos != null && i < videos.length(); i++) {
            String key = videos.optString(i, null);
            if (key != null && hidden.isHidden(key)) {
                hidden.unhide(key);
                made.unmarkKidMade(Item.VIDEO, key);
                changed = true;
            }
        }

        BlockedChannelData channels = BlockedChannelData.instance(context);
        JSONArray channelKeys = undo.optJSONArray("channels");
        for (int i = 0; channelKeys != null && i < channelKeys.length(); i++) {
            String key = channelKeys.optString(i, null);
            if (key == null) continue;
            for (Pair<String, String> channel : channels.getChannelIdsWithNames()) {
                if (key.equals(channel.first) || (channel.first == null && key.equals(channel.second))) {
                    channels.removeChannel(channel.first, channel.second);
                    made.unmarkKidMade(Item.CHANNEL, key);
                    changed = true;
                }
            }
        }

        JSONArray words = undo.optJSONArray("words");
        for (int i = 0; words != null && i < words.length(); i++) {
            String word = words.optString(i, null);
            if (word != null && KeywordFilter.contains(context, word)) {
                KeywordFilter.remove(context, word);
                made.unmarkKidMade(Item.WORD, word);
                changed = true;
            }
        }

        return changed;
    }

    /** Sends Hearth the lists when they differ from what it last took; on the caller's thread (it blocks) */
    static void flush(Context context) {
        String now;
        try {
            now = snapshot(context);
        } catch (JSONException | RuntimeException e) {
            return;
        }

        if (now.equals(sSent)) {
            return;
        }

        if (HearthProfile.reportKidBlocks(context, now)) {
            sSent = now;
        }
    }

    private static String cut(String text) {
        if (text == null) {
            return "";
        }

        return text.length() > LABEL_MAX ? text.substring(0, LABEL_MAX) : text;
    }
}
