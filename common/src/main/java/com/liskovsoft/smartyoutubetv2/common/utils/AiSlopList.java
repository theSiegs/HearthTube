package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * HearthTube: channels that mainly post AI-made videos, from AiSList (github.com/Override92/AiSList, CC BY-NC 4.0, by
 * its contributors), which lists them by @handle; github.com/theSiegs/aislist-channel-ids publishes the same lists by
 * channel ID. In a Google TV kids profile a video from a channel on either list is left out wherever blocked channels
 * are (every row and grid, search, the player's suggestions, what plays next), and the player refuses one however it
 * got there. Everyone else sees it with a "Likely AI" label on its card, unless Settings › General turns labels off.
 * <p>
 * A copy of both lists ships in the app (fetched when it was built), so a kid is covered from the first start, even
 * offline. Newer copies come from raw.githubusercontent.com once a day at most, and go to the app's own storage; when
 * that fails the copy we have stays. Lists are read off the main thread, once, into sets: a check is a set lookup.
 * What to do with a channel is {@link AiSlopMatcher}.
 */
public final class AiSlopList {
    private static final String TAG = AiSlopList.class.getSimpleName();
    private static final String URL = "https://raw.githubusercontent.com/theSiegs/aislist-channel-ids/main/lists/";
    private static final String BLOCKLIST = "aislist_blocklist_ids.txt";
    private static final String WARNLIST = "aislist_warnlist_ids.txt";
    /** In the app's files, and in its assets (the copy it shipped with) */
    private static final String DIR = "aislist";
    private static final String PREFS = "hearthtube_aislist";
    private static final String LABELS = "labels";
    private static final String DOWNLOADED_MS = "downloaded_ms";
    private static final String TRIED_MS = "tried_ms";
    private static final long DAY_MS = 24 * 60 * 60 * 1_000L;
    /** After a failed download (offline, GitHub down), try again on a start this much later */
    private static final long RETRY_MS = 60 * 60 * 1_000L;
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static volatile AiSlopMatcher sMatcher = AiSlopMatcher.EMPTY;
    private static volatile boolean sKids;
    private static volatile boolean sLabels = true;
    private static volatile boolean sStarted;
    private static boolean sLoaded;
    private static final AtomicBoolean sDownloading = new AtomicBoolean();
    /** Videos left out so far, so a search can tell its results were all from these channels */
    private static final AtomicInteger sHiddenCount = new AtomicInteger();

    private AiSlopList() {
    }

    /**
     * Call on every launch (profile switches always go through the launcher), like KidsShorts.apply. Reads the lists
     * the first time, and fetches new ones when a day has gone by; both off the main thread.
     */
    public static void init(Context context) {
        if (context == null) {
            return;
        }

        Context app = context.getApplicationContext();
        sKids = ParentGate.isKidsProfile(app);
        sLabels = prefs(app).getBoolean(LABELS, true);
        sStarted = true;

        RxHelper.runAsync(() -> {
            load(app);
            downloadIfDue(app);
        });
    }

    /** Rows, grids, search and suggestions (VideoGroup), in a kids profile: leave this one out */
    public static boolean isHidden(Video video) {
        if (video == null || video.isChapter) {
            return false;
        }

        if (decide(video.channelId) != AiSlopMatcher.HIDE) {
            return false;
        }

        sHiddenCount.incrementAndGet();
        return true;
    }

    /** What plays next on its own (KeywordFilter.checkNext), in a kids profile: skip this one */
    public static boolean isHidden(MediaItem item) {
        return item != null && decide(item.getChannelId()) == AiSlopMatcher.HIDE;
    }

    /** The card's "Likely AI" label: anywhere but a kids profile, unless labels are off */
    public static boolean isLabeled(Video video) {
        return video != null && !video.isChapter && decide(video.channelId) == AiSlopMatcher.LABEL;
    }

    /** The focused video's details (Hearth rows): "Likely AI • " in front, when its card has the label */
    public static CharSequence withLabel(Context context, Video video, CharSequence details) {
        if (context == null || !isLabeled(video)) {
            return details;
        }

        String label = context.getString(R.string.ai_label);
        return details == null || details.length() == 0 ? label :
                TextUtils.concat(label, " " + Video.TERTIARY_TEXT_DELIM + " ", details);
    }

    /**
     * Kids profiles: the player refuses a video from one of these channels, however it got here (a link, a cast,
     * autoplay). Checked as the formats arrive and again with the details, by both the channel ID they carry and the
     * video's own.
     */
    public static boolean isBlockedInPlayer(Context context, String channelId, Video video) {
        start(context);
        AiSlopMatcher matcher = sMatcher;

        if (matcher.isEmpty() || !(matcher.isListed(channelId) || (video != null && matcher.isListed(video.channelId)))) {
            return false;
        }

        return ParentGate.isKidsProfile(context);
    }

    /** Videos left out so far; compare two counts to see if any were */
    public static int getHiddenCount() {
        return sHiddenCount.get();
    }

    /**
     * Why a search shows nothing, given {@link #getHiddenCount()} and KeywordFilter's count from before it. Only kids
     * profiles leave these channels out, and they never show Shorts either.
     */
    public static int getEmptySearchMessage(int hiddenBefore, int wordsHiddenBefore) {
        if (getHiddenCount() == hiddenBefore) {
            return KeywordFilter.getEmptySearchMessage(wordsHiddenBefore);
        }

        return KeywordFilter.getHiddenCount() != wordsHiddenBefore ? R.string.search_only_ai_or_blocked : R.string.search_only_ai;
    }

    /** Settings › General: "Likely AI" labels on cards. On by default. */
    public static boolean isLabelsEnabled(Context context) {
        return prefs(context).getBoolean(LABELS, true);
    }

    public static void setLabelsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(LABELS, enabled).apply();
        sLabels = enabled;
    }

    private static int decide(String channelId) {
        if (channelId == null) {
            return AiSlopMatcher.SHOW;
        }

        start(null);
        return sMatcher.decide(channelId, sKids, sLabels);
    }

    /** In case nothing called init() (the app came back straight to a screen other than the splash) */
    private static void start(Context context) {
        if (!sStarted) {
            init(context != null ? context : GlobalPreferences.context());
        }
    }

    private static synchronized void load(Context context) {
        if (sLoaded) {
            return;
        }

        sMatcher = read(context);
        sLoaded = true;
        Log.d(TAG, "Lists read: %s blocklist and %s warnlist channels",
                sMatcher.getBlocklistSize(), sMatcher.getWarnlistSize());
    }

    /** Our downloaded copy of each list, or else the one the app shipped with */
    private static AiSlopMatcher read(Context context) {
        return AiSlopMatcher.of(read(context, BLOCKLIST), read(context, WARNLIST));
    }

    private static Set<String> read(Context context, String name) {
        File file = new File(dir(context), name);

        if (file.isFile()) {
            try (InputStream in = new FileInputStream(file)) {
                Set<String> ids = AiSlopMatcher.parse(new InputStreamReader(in, UTF_8));
                if (ids != null) {
                    return ids;
                }
            } catch (IOException e) {
                Log.e(TAG, "Can't read %s: %s", name, e.getMessage());
            }
        }

        try (InputStream in = context.getAssets().open(DIR + "/" + name)) {
            return AiSlopMatcher.parse(new InputStreamReader(in, UTF_8));
        } catch (IOException e) {
            return null; // a build without the lists
        }
    }

    /** Once a day, or now when there's no downloaded copy yet; not again soon after a failure */
    private static void downloadIfDue(Context context) {
        SharedPreferences prefs = prefs(context);
        long now = System.currentTimeMillis();
        long downloaded = prefs.getLong(DOWNLOADED_MS, 0);
        long tried = prefs.getLong(TRIED_MS, 0);
        boolean haveCopy = new File(dir(context), BLOCKLIST).isFile();

        // A clock that went back counts as due
        boolean due = !haveCopy || now - downloaded >= DAY_MS || downloaded > now;
        boolean waiting = now - tried < RETRY_MS && tried <= now;

        if (!due || (waiting && haveCopy) || !sDownloading.compareAndSet(false, true)) {
            return;
        }

        try {
            prefs.edit().putLong(TRIED_MS, now).apply();
            boolean ok = download(context, BLOCKLIST) & download(context, WARNLIST);

            if (ok) {
                prefs.edit().putLong(DOWNLOADED_MS, now).apply();
            }

            sMatcher = read(context);
            Log.d(TAG, "Lists downloaded (%s): %s blocklist and %s warnlist channels", ok ? "all" : "some",
                    sMatcher.getBlocklistSize(), sMatcher.getWarnlistSize());
        } finally {
            sDownloading.set(false);
        }
    }

    /** Into a temporary file, which replaces our copy only once it's in full and is a list */
    private static boolean download(Context context, String name) {
        File dir = dir(context);
        File temp = new File(dir, name + ".tmp");

        if (!dir.isDirectory() && !dir.mkdirs()) {
            return false;
        }

        Request request = new Request.Builder().url(URL + name).get().build();

        try (Response response = client().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                Log.e(TAG, "Can't download %s: HTTP %s", name, response.code());
                return false;
            }

            try (InputStream in = response.body().byteStream(); OutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[16 * 1024];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
            }

            Set<String> ids;
            try (InputStream in = new FileInputStream(temp)) {
                ids = AiSlopMatcher.parse(new InputStreamReader(in, UTF_8));
            }

            if (ids == null || !temp.renameTo(new File(dir, name))) {
                Log.e(TAG, "Not a list, or can't keep it: %s", name);
                return false;
            }

            return true;
        } catch (Exception e) { // offline, timeout, storage
            Log.e(TAG, "Can't download %s: %s", name, e.getMessage());
            return false;
        } finally {
            if (temp.exists()) {
                temp.delete();
            }
        }
    }

    /**
     * The app's usual OkHttp setup (DNS, TLS, timeouts), without its debug interceptors: they'd copy a whole list
     * into the log.
     */
    private static OkHttpClient client() {
        OkHttpClient.Builder builder = OkHttpManager.instance().getClient().newBuilder();
        builder.interceptors().clear();
        builder.networkInterceptors().clear();
        return builder.build();
    }

    private static File dir(Context context) {
        return new File(context.getFilesDir(), DIR);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
