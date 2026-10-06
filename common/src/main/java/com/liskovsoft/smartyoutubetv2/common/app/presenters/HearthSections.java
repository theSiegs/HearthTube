package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;

/**
 * HearthTube's own sections, beside SmartTube's: Ambiance and Podcasts (rows of YouTube searches), Library (history,
 * then playlists) and Watch later. Their ids sit between SmartTube's MediaGroup types and SidebarService.RESERVED_ID (100).
 * Also {@link #getQueued}: the queue shows what's waiting to play, not everything played this session.
 */
public final class HearthSections {
    public static final int TYPE_AMBIANCE = 91;
    public static final int TYPE_LIBRARY = 92;
    public static final int TYPE_WATCH_LATER = 93;
    public static final int TYPE_PODCASTS = 94;

    /** Search, row title. Long videos first in the search terms: ambiance runs for hours. */
    private static final int[][] AMBIANCE = {
            {R.string.ambiance_fireplace_query, R.string.ambiance_fireplace},
            {R.string.ambiance_rain_query, R.string.ambiance_rain},
            {R.string.ambiance_ocean_query, R.string.ambiance_ocean},
            {R.string.ambiance_forest_query, R.string.ambiance_forest},
            {R.string.ambiance_snow_query, R.string.ambiance_snow},
            {R.string.ambiance_aquarium_query, R.string.ambiance_aquarium},
            {R.string.ambiance_cafe_query, R.string.ambiance_cafe},
    };
    private static final int[][] PODCASTS = {
            {R.string.podcasts_popular_query, R.string.podcasts_popular},
            {R.string.podcasts_comedy_query, R.string.podcasts_comedy},
            {R.string.podcasts_news_query, R.string.podcasts_news},
            {R.string.podcasts_science_query, R.string.podcasts_science},
            {R.string.podcasts_sports_query, R.string.podcasts_sports},
    };

    /** Channels with a circle of their own in Subscriptions (the most recent posters); all of them are under All */
    private static final int SUBSCRIPTION_CHANNELS = 15;

    private HearthSections() {
    }

    /**
     * Called from BrowsePresenter.initSectionMapping
     */
    static void addSections(Context context, Map<Integer, BrowseSection> sections) {
        // Subscriptions: channel circles above one big row (All, then a row per channel)
        sections.put(MediaGroup.TYPE_SUBSCRIPTIONS, new BrowseSection(MediaGroup.TYPE_SUBSCRIPTIONS,
                context.getString(R.string.header_subscriptions), BrowseSection.TYPE_ROW, R.drawable.icon_subscriptions, true));
        // SmartTube names Live with its all-caps video badge
        sections.put(MediaGroup.TYPE_LIVE, new BrowseSection(MediaGroup.TYPE_LIVE, context.getString(R.string.header_live),
                BrowseSection.TYPE_ROW, R.drawable.icon_live));
        sections.put(TYPE_AMBIANCE, new BrowseSection(TYPE_AMBIANCE, context.getString(R.string.header_ambiance),
                BrowseSection.TYPE_ROW, R.drawable.icon_music));
        sections.put(TYPE_LIBRARY, new BrowseSection(TYPE_LIBRARY, context.getString(R.string.header_library),
                BrowseSection.TYPE_ROW, R.drawable.icon_history, true));
        sections.put(TYPE_WATCH_LATER, new BrowseSection(TYPE_WATCH_LATER, context.getString(R.string.header_watch_later),
                BrowseSection.TYPE_GRID, R.drawable.icon_playlist, true));
        sections.put(TYPE_PODCASTS, new BrowseSection(TYPE_PODCASTS, context.getString(R.string.header_podcasts),
                BrowseSection.TYPE_ROW, R.drawable.icon_music));
    }

    /**
     * Called from BrowsePresenter.initRowAndGridMapping
     */
    static void addMappings(Context context, ContentService content, Map<Integer, Observable<List<MediaGroup>>> rows,
                            Map<Integer, Observable<MediaGroup>> grids) {
        rows.put(MediaGroup.TYPE_SUBSCRIPTIONS, subscriptionRows(context, content));
        rows.put(TYPE_AMBIANCE, searchRows(context, content, AMBIANCE));
        rows.put(TYPE_PODCASTS, searchRows(context, content, PODCASTS));
        rows.put(TYPE_LIBRARY, Observable.concat(
                content.getHistoryObserve().map(Collections::singletonList).onErrorReturnItem(Collections.emptyList()),
                content.getPlaylistRowsObserve()));

        Video watchLater = new Video();
        watchLater.playlistId = "WL";
        watchLater.title = context.getString(R.string.header_watch_later);
        grids.put(TYPE_WATCH_LATER, Observable.defer(() -> ChannelUploadsPresenter.instance(context).obtainUploadsObservable(watchLater)));
    }

    /**
     * Videos added with "Add to queue" that haven't played yet, next one first.
     */
    public static List<Video> getQueued() {
        Video current = Playlist.instance().getCurrent();
        List<Video> queued = new ArrayList<>();

        // Wherever they sit in the playlist: only starting to play (or Remove from queue) takes them off
        for (Video video : Playlist.instance().getAll()) {
            if (com.liskovsoft.smartyoutubetv2.common.app.models.data.HearthQueue.isWaiting(video) && !video.equals(current)) {
                queued.add(video);
            }
        }

        return queued;
    }

    /**
     * All (the subscriptions feed), then the uploads of the channels with the newest videos, one row each, carrying
     * the channel's picture for its circle.
     */
    private static Observable<List<MediaGroup>> subscriptionRows(Context context, ContentService content) {
        String all = context.getString(R.string.subscriptions_all);

        return content.getSubscriptionsObserve().flatMap(feed -> {
            Observable<MediaGroup> allRow = Observable.just(new TitledGroup(feed, all, null));

            Observable<MediaGroup> channels = content.getSubscribedChannelsByNewContentObserve()
                    .flatMap(group -> {
                        List<Observable<MediaGroup>> uploads = new ArrayList<>();

                        for (MediaItem channel : newestFirst(group.getMediaItems(), feed.getMediaItems())) {
                            uploads.add(content.getGroupObserve(channel)
                                    .map(uploaded -> (MediaGroup) new TitledGroup(uploaded, channel.getTitle(), channel.getCardImageUrl()))
                                    .onErrorResumeNext(Observable.empty()));
                        }

                        // In order, each row as soon as it's in
                        return Observable.concatEager(uploads);
                    })
                    .onErrorResumeNext(Observable.empty());

            return Observable.concat(allRow, channels);
        }).map(Collections::singletonList);
    }

    /**
     * The channels in the order of their newest video in the feed (matched by channel id, else by name), then the
     * rest in YouTube's order, up to {@link #SUBSCRIPTION_CHANNELS}.
     */
    private static List<MediaItem> newestFirst(@Nullable List<MediaItem> channels, @Nullable List<MediaItem> feed) {
        List<MediaItem> result = new ArrayList<>();

        if (channels == null) {
            return result;
        }

        if (feed != null) {
            for (MediaItem video : feed) {
                if (result.size() >= SUBSCRIPTION_CHANNELS || video == null) {
                    break;
                }

                for (MediaItem channel : channels) {
                    if (channel != null && !result.contains(channel) && isSameChannel(channel, video)) {
                        result.add(channel);
                        break;
                    }
                }
            }
        }


        for (MediaItem channel : channels) {
            if (result.size() >= SUBSCRIPTION_CHANNELS) {
                break;
            }

            if (channel != null && !result.contains(channel)) {
                result.add(channel);
            }
        }

        return result;
    }

    private static boolean isSameChannel(MediaItem channel, MediaItem video) {
        if (channel.getChannelId() != null && video.getChannelId() != null) {
            return channel.getChannelId().equals(video.getChannelId());
        }

        // The channel list has no ids; the feed's author reads "Jam In The Van • @JamintheVan"
        String author = video.getAuthor();
        if (channel.getTitle() == null || author == null) {
            return false;
        }

        int dot = author.indexOf(" • ");
        return channel.getTitle().trim().equalsIgnoreCase((dot != -1 ? author.substring(0, dot) : author).trim());
    }

    /**
     * One row per search: the first shelf of results, under our own title.
     */
    private static Observable<List<MediaGroup>> searchRows(Context context, ContentService content, int[][] searches) {
        List<Observable<MediaGroup>> rows = new ArrayList<>();

        for (int[] search : searches) {
            String title = context.getString(search[1]);
            rows.add(content.getSearchObserve(context.getString(search[0]))
                    .map(groups -> firstNonEmpty(groups))
                    .map(group -> (MediaGroup) new TitledGroup(group, title, null))
                    .onErrorResumeNext(Observable.empty()));
        }

        // In order, each row as soon as it's in
        return Observable.concatEager(rows).map(Collections::singletonList);
    }

    private static MediaGroup firstNonEmpty(List<MediaGroup> groups) throws Exception {
        if (groups != null) {
            for (MediaGroup group : groups) {
                if (group != null && !group.isEmpty()) {
                    return group;
                }
            }
        }

        throw new Exception("No results");
    }

    /**
     * A search shelf under our title. No continuation: the first page (about 20 videos) is plenty for a row,
     * and continuing needs YouTube's own group class.
     */
    public static class TitledGroup implements MediaGroup {
        private final MediaGroup mGroup;
        private final String mTitle;
        @Nullable
        private final String mIconUrl;

        TitledGroup(MediaGroup group, String title, @Nullable String iconUrl) {
            mGroup = group;
            mTitle = title;
            mIconUrl = iconUrl;
        }

        /** A channel's picture, for its circle in the strip; null for a plain chip */
        @Nullable
        public String getIconUrl() {
            return mIconUrl;
        }

        @Override
        public int getType() {
            return mGroup.getType();
        }

        @Nullable
        @Override
        public List<MediaItem> getMediaItems() {
            return mGroup.getMediaItems();
        }

        @Override
        public String getTitle() {
            return mTitle;
        }

        @Override
        public String getChannelId() {
            return null;
        }

        @Override
        public String getParams() {
            return null;
        }

        @Override
        public String getReloadPageKey() {
            return null;
        }

        @Override
        public String getNextPageKey() {
            return null;
        }

        @Override
        public String getChannelUrl() {
            return null;
        }

        @Override
        public boolean isEmpty() {
            return mGroup.isEmpty();
        }
    }
}
