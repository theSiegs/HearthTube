package com.liskovsoft.smartyoutubetv2.common.utils;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.LinkedHashMap;
import java.util.Map;

import io.reactivex.disposables.Disposable;

/**
 * HearthTube: when a video was published, for the line under the title in Hearth's rows. YouTube's TV tiles on Home
 * carry the channel and views but not the date, so it is asked for (the video's details) once a tile has rested in
 * focus a moment, and kept for the session.
 */
public final class PublishedDates {
    public interface Callback {
        void onDate(String videoId, String date);
    }

    /** A tile has to rest this long before its date is asked for: a fast sweep along a row asks nothing */
    private static final long REST_MS = 500;
    private static final int MAX = 300;
    private static final Map<String, String> sDates = new LinkedHashMap<String, String>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX;
        }
    };
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static Runnable sPending;
    private static Disposable sAction;

    private PublishedDates() {
    }

    /** The date when it is known (an empty string: YouTube gave none), else null */
    public static String known(String videoId) {
        return videoId != null ? sDates.get(videoId) : null;
    }

    /** Asks for the video's date after a short rest, unless another is focused meanwhile; main thread */
    public static void request(String videoId, Callback callback) {
        cancel();

        if (videoId == null || callback == null || sDates.containsKey(videoId)) {
            return;
        }

        sPending = () -> sAction = YouTubeServiceManager.instance().getMediaItemService().getMetadataObserve(videoId)
                .subscribe(metadata -> {
                    String date = metadata != null && !TextUtils.isEmpty(metadata.getPublishedDate()) ? metadata.getPublishedDate() : "";
                    sDates.put(videoId, date);
                    if (!date.isEmpty()) {
                        callback.onDate(videoId, date);
                    }
                }, error -> {
                    // No date then: the line stays as YouTube's tile had it
                });
        sHandler.postDelayed(sPending, REST_MS);
    }

    public static void cancel() {
        if (sPending != null) {
            sHandler.removeCallbacks(sPending);
            sPending = null;
        }

        RxHelper.disposeActions(sAction);
    }

    /** The tile's line with the date added, unless it already says it */
    public static CharSequence withDate(CharSequence line, String date) {
        if (TextUtils.isEmpty(date)) {
            return line;
        }

        if (TextUtils.isEmpty(line)) {
            return date;
        }

        if (line.toString().contains(date)) {
            return line;
        }

        return android.text.TextUtils.concat(line, " • ", date);
    }
}
