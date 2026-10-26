package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Pair;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HearthTube: single videos the viewer chose to hide ("Hide this video"). They stay out of every list, in this profile,
 * with or without a signed-in account. Newest first; the oldest go when there are too many.
 */
public class HiddenVideoData implements ProfileChangeListener {
    private static final String HIDDEN_VIDEO_DATA = "hidden_video_data";
    static final int MAX = 500;
    @SuppressLint("StaticFieldLeak")
    private static HiddenVideoData sInstance;
    private final AppPrefs mPrefs;
    /** videoId -> title */
    private final LinkedHashMap<String, String> mVideos = new LinkedHashMap<>();
    private final Runnable mPersistStateInt = this::persistStateInt;

    private HiddenVideoData(Context context) {
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        restoreState();
    }

    public static HiddenVideoData instance(Context context) {
        if (sInstance == null) {
            sInstance = new HiddenVideoData(context.getApplicationContext());
        }

        return sInstance;
    }

    public synchronized boolean isHidden(String videoId) {
        return videoId != null && !mVideos.isEmpty() && mVideos.containsKey(videoId);
    }

    public synchronized boolean isEmpty() {
        return mVideos.isEmpty();
    }

    public synchronized void hide(String videoId, String title) {
        if (videoId == null) {
            return;
        }

        mVideos.remove(videoId);
        mVideos.put(videoId, titleOrId(videoId, title));

        while (mVideos.size() > MAX) {
            mVideos.remove(mVideos.keySet().iterator().next());
        }

        persistState();
    }

    public synchronized void unhide(String videoId) {
        if (videoId != null && mVideos.remove(videoId) != null) {
            persistState();
        }
    }

    /** Newest first: videoId -> title */
    public synchronized List<Pair<String, String>> getVideos() {
        List<Pair<String, String>> result = new ArrayList<>();

        for (Map.Entry<String, String> entry : mVideos.entrySet()) {
            result.add(0, new Pair<>(entry.getKey(), entry.getValue()));
        }

        return result;
    }

    /** A tile with no title is listed by its id (an empty one wouldn't survive being saved) */
    static String titleOrId(String videoId, String title) {
        return title == null || title.trim().isEmpty() ? videoId : title;
    }

    /** One saved entry as {id, title}; null when it isn't one */
    static String[] parse(String spec) {
        String[] pair = Helpers.splitObj(spec);

        if (pair == null || pair.length == 0 || Helpers.parseStr(pair[0]) == null) {
            return null;
        }

        String id = Helpers.parseStr(pair[0]);

        return new String[] {id, pair.length > 1 ? titleOrId(id, Helpers.parseStr(pair[1])) : id};
    }

    private synchronized void restoreState() {
        String[] split = Helpers.splitData(mPrefs.getProfileData(HIDDEN_VIDEO_DATA));

        mVideos.clear();

        for (String spec : Helpers.parseList(split, 0, item -> item)) {
            String[] entry = parse(spec);

            if (entry != null) {
                mVideos.put(entry[0], entry[1]);
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

        for (Map.Entry<String, String> entry : mVideos.entrySet()) {
            specs.add(Helpers.mergeObj(entry.getKey(), entry.getValue()));
        }

        mPrefs.setProfileData(HIDDEN_VIDEO_DATA, Helpers.mergeData(specs));
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }
}
