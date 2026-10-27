package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Pair;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * HearthTube: single videos the viewer chose to hide ("Hide this video"). They stay out of every list, in this profile,
 * with or without a signed-in account. Newest first; the oldest go when there are too many.
 */
public class HiddenVideoData implements ProfileChangeListener {
    private static final String HIDDEN_VIDEO_DATA = "hidden_video_data";
    private static final String OFF_CONTINUE_DATA = "off_continue_video_ids";
    static final int MAX = 500;
    @SuppressLint("StaticFieldLeak")
    private static HiddenVideoData sInstance;
    private final AppPrefs mPrefs;
    private final Context mContext;
    /** videoId -> title */
    private final LinkedHashMap<String, String> mVideos = new LinkedHashMap<>();
    /** Videos marked Not interested or thumbed down: not offered to resume (Continue watching) */
    private final LinkedHashSet<String> mOffContinue = new LinkedHashSet<>();
    private final Runnable mPersistStateInt = this::persistStateInt;

    private HiddenVideoData(Context context) {
        mContext = context;
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
        // The viewer's own hides, and what a parent blocked
        return videoId != null && (mVideos.containsKey(videoId) || ParentBlockData.instance(mContext).isVideoBlocked(videoId));
    }

    /** Not to be offered in Continue watching: hidden, marked Not interested or thumbed down */
    public synchronized boolean isOffContinue(String videoId) {
        return videoId != null && (mVideos.containsKey(videoId) || mOffContinue.contains(videoId)
                || ParentBlockData.instance(mContext).isVideoBlocked(videoId));
    }

    public synchronized void keepOffContinue(String videoId) {
        if (videoId == null) {
            return;
        }

        mOffContinue.remove(videoId);
        mOffContinue.add(videoId);

        while (mOffContinue.size() > MAX) {
            mOffContinue.remove(mOffContinue.iterator().next());
        }

        persistState();
    }

    /** The thumbs down was taken back */
    public synchronized void allowContinue(String videoId) {
        if (videoId != null && mOffContinue.remove(videoId)) {
            persistState();
        }
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
        mOffContinue.clear();

        for (String spec : Helpers.parseList(split, 0, item -> item)) {
            String[] entry = parse(spec);

            if (entry != null) {
                mVideos.put(entry[0], entry[1]);
            }
        }

        String[] offSplit = Helpers.splitData(mPrefs.getProfileData(OFF_CONTINUE_DATA));

        for (String id : Helpers.parseList(offSplit, 0, item -> Helpers.parseStr(item))) {
            if (id != null) {
                mOffContinue.add(id);
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
        mPrefs.setProfileData(OFF_CONTINUE_DATA, Helpers.mergeData(new ArrayList<>(mOffContinue)));
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }
}
