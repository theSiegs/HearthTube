package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION;

import androidx.tvprovider.media.tv.TvContractCompat;
import androidx.tvprovider.media.tv.TvContractCompat.WatchNextPrograms;
import androidx.tvprovider.media.tv.WatchNextProgram;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Puts the video being watched on Android TV's "Watch Next" list, which launchers show as
 * Continue Watching (the Hearth launcher, Google TV). One entry per video; it's updated as the
 * position moves and removed once the video is finished. Selecting it reopens the video in this app,
 * which resumes from its own saved position.
 */
public class WatchNextPublisher {
    private static final String TAG = WatchNextPublisher.class.getSimpleName();
    /** Opened and closed again right away: not worth a Continue Watching entry. */
    private static final long MIN_POSITION_MS = 30_000;
    /** The rest is credits. */
    private static final float FINISHED_FRACTION = 0.95f;
    private static final String[] PROJECTION = {
            WatchNextPrograms._ID, WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, WatchNextPrograms.COLUMN_BROWSABLE};
    // Content provider calls are disk/IPC work: keep them off the player's thread, in order
    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();

    private WatchNextPublisher() {
    }

    public static void publish(Context context, Video video, long positionMs, long durationMs) {
        if (context == null || VERSION.SDK_INT < 26 || video == null || video.videoId == null
                || video.isLive || video.isUpcoming || video.isShorts || durationMs <= 0) {
            return;
        }

        Context appContext = context.getApplicationContext();
        boolean finished = positionMs >= durationMs * FINISHED_FRACTION;

        if (finished) {
            sExecutor.execute(() -> remove(appContext, video.videoId));
        } else if (positionMs >= MIN_POSITION_MS) {
            WatchNextProgram program = createProgram(appContext, video, positionMs, durationMs);
            sExecutor.execute(() -> upsert(appContext, video.videoId, program));
        }
    }

    private static WatchNextProgram createProgram(Context context, Video video, long positionMs, long durationMs) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + video.videoId))
                .setPackage(context.getPackageName());

        WatchNextProgram.Builder builder = new WatchNextProgram.Builder();
        builder.setType(WatchNextPrograms.TYPE_CLIP)
                .setWatchNextType(WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                .setTitle(video.title)
                .setDescription(video.getAuthor())
                .setIntentUri(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)))
                .setInternalProviderId(video.videoId)
                .setContentId(video.videoId)
                .setLastPlaybackPositionMillis((int) positionMs)
                .setDurationMillis((int) durationMs);

        String poster = video.bgImageUrl != null ? video.bgImageUrl : video.cardImageUrl;
        if (poster != null) {
            builder.setPosterArtUri(Uri.parse(poster))
                    .setPosterArtAspectRatio(WatchNextPrograms.ASPECT_RATIO_16_9);
        }

        return builder.build();
    }

    private static void upsert(Context context, String videoId, WatchNextProgram program) {
        try {
            ContentResolver resolver = context.getContentResolver();
            long id = findProgram(resolver, videoId, true);
            ContentValues values = program.toContentValues();

            if (id != -1) {
                resolver.update(TvContractCompat.buildWatchNextProgramUri(id), values, null, null);
            } else {
                resolver.insert(WatchNextPrograms.CONTENT_URI, values);
            }
        } catch (Exception e) {
            // No TV provider (non-TV device) or it refused the row
            Log.e(TAG, "Watch Next update failed: %s", e.getMessage());
        }
    }

    private static void remove(Context context, String videoId) {
        try {
            ContentResolver resolver = context.getContentResolver();
            long id = findProgram(resolver, videoId, false);

            if (id != -1) {
                resolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Watch Next removal failed: %s", e.getMessage());
        }
    }

    /**
     * Our row for this video, or -1. Only this app's rows are visible to it.
     *
     * @param dropHidden a row the user removed from the launcher (browsable = 0) is deleted, so watching again brings it back
     */
    private static long findProgram(ContentResolver resolver, String videoId, boolean dropHidden) {
        try (Cursor cursor = resolver.query(WatchNextPrograms.CONTENT_URI, PROJECTION, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                if (videoId.equals(cursor.getString(1))) {
                    long id = cursor.getLong(0);

                    if (dropHidden && cursor.getInt(2) == 0) {
                        resolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null);
                        return -1;
                    }

                    return id;
                }
            }
        }

        return -1;
    }
}
