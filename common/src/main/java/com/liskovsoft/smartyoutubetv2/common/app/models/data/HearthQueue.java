package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import java.util.HashSet;
import java.util.Set;

/**
 * The videos the user queued (long-press > Add to playback queue) that haven't started yet: what the Queued tab
 * counts. SmartTube's own Video.fromQueue can't be used for that: the player also sets it on whatever is up next,
 * e.g. an already watched video after Previous.
 */
public final class HearthQueue {
    private static final Set<String> sWaiting = new HashSet<>();

    private HearthQueue() {
    }

    public static synchronized void add(Video video) {
        if (video != null && video.videoId != null) {
            sWaiting.add(video.videoId);
        }
    }

    /** Taken out of the queue, or started playing */
    public static synchronized void remove(Video video) {
        if (video != null && video.videoId != null) {
            sWaiting.remove(video.videoId);
        }
    }

    public static synchronized boolean isWaiting(Video video) {
        return video != null && video.videoId != null && sWaiting.contains(video.videoId);
    }
}
