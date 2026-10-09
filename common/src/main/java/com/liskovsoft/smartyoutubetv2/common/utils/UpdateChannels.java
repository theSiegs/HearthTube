package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;

import io.reactivex.disposables.Disposable;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * HearthTube's update channels. Stable releases (GitHub release tag "stable", R.array.update_urls) are always offered;
 * with About > "Include pre-releases" on, pre-releases too (tag "latest", R.array.update_urls_prerelease), whichever
 * is newer. The feeds are in smarttubetv/src/hearthtube/res/values/update_urls.xml; release.ps1 publishes them.
 * <p>
 * SharedModules' update checker takes the first feed that answers, so the choice is made here: each feed is read
 * first, and the checker gets the one offering the highest versionCode. Builds without a pre-release feed (the
 * SmartTube flavors) go straight to the checker, as before.
 */
public final class UpdateChannels {
    private static final String TAG = UpdateChannels.class.getSimpleName();
    private static final String PREFS_NAME = "update_channel";
    private static final String INCLUDE_PRERELEASES = "include_prereleases";
    private static final int MAX_FEED_CHARS = 256 * 1024;

    public interface Callback {
        /** The feeds to hand the update checker; none when nothing is published on the channels yet */
        void onFeeds(String[] feedUrls);
    }

    private UpdateChannels() {
    }

    /** At each start: settles where "Include pre-releases" starts, once (see UpdateFeedPicker) */
    public static void init(Context context) {
        if (context != null && hasPreReleases(context)) {
            isIncludePreReleases(context);
        }
    }

    /** True in builds with a pre-release channel (HearthTube) */
    public static boolean hasPreReleases(Context context) {
        return context.getResources().getStringArray(R.array.update_urls_prerelease).length > 0;
    }

    public static boolean isIncludePreReleases(Context context) {
        SharedPreferences prefs = prefs(context);

        if (!prefs.contains(INCLUDE_PRERELEASES)) {
            boolean include = startingValue(context);
            prefs.edit().putBoolean(INCLUDE_PRERELEASES, include).apply();
            return include;
        }

        return prefs.getBoolean(INCLUDE_PRERELEASES, false);
    }

    public static void setIncludePreReleases(Context context, boolean include) {
        prefs(context).edit().putBoolean(INCLUDE_PRERELEASES, include).apply();
    }

    /**
     * Picks the feed to update from, in the background, and calls back on the main thread: the stable feed and,
     * when included, the pre-release one; the newer wins. When none can be read, all of them go to the checker,
     * which then reports the error.
     */
    public static Disposable pickFeeds(Context context, Callback callback) {
        String[] stable = context.getResources().getStringArray(R.array.update_urls);
        String[] preReleases = isIncludePreReleases(context)
                ? context.getResources().getStringArray(R.array.update_urls_prerelease) : new String[0];
        String[] feeds = new String[stable.length + preReleases.length];
        System.arraycopy(stable, 0, feeds, 0, stable.length);
        System.arraycopy(preReleases, 0, feeds, stable.length, preReleases.length);

        return RxHelper.execute(RxHelper.fromCallable(() -> pick(feeds)), callback::onFeeds,
                error -> callback.onFeeds(feeds));
    }

    private static String[] pick(String[] feeds) {
        int[] versionCodes = new int[feeds.length];

        for (int i = 0; i < feeds.length; i++) {
            versionCodes[i] = read(feeds[i]);
        }

        int best = UpdateFeedPicker.pick(versionCodes);

        if (best != UpdateFeedPicker.NONE) {
            Log.d(TAG, "Updating from %s (versionCode %s)", feeds[best], versionCodes[best]);
            return new String[] {feeds[best]};
        }

        return UpdateFeedPicker.isNothingPublished(versionCodes) ? new String[0] : feeds;
    }

    /** A feed's newest versionCode, or UpdateFeedPicker.MISSING / FAILED */
    private static int read(String url) {
        Request request = new Request.Builder().url(url).get().build();

        try (Response response = client().newCall(request).execute()) {
            ResponseBody body = response.body();

            if (response.code() == 404) {
                return UpdateFeedPicker.MISSING;
            }

            if (!response.isSuccessful() || body == null || body.contentLength() > MAX_FEED_CHARS) {
                Log.e(TAG, "Can't read %s: HTTP %s", url, response.code());
                return UpdateFeedPicker.FAILED;
            }

            return UpdateFeedPicker.newestVersionCode(body.string());
        } catch (Exception e) { // offline, timeout
            Log.e(TAG, "Can't read %s: %s", url, e.getMessage());
            return UpdateFeedPicker.FAILED;
        }
    }

    private static boolean startingValue(Context context) {
        boolean debugBuild = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;

        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return UpdateFeedPicker.defaultIncludePreReleases(debugBuild, info.firstInstallTime, info.lastUpdateTime);
        } catch (Exception e) {
            return debugBuild;
        }
    }

    /** The app's usual OkHttp setup (DNS, TLS, timeouts), without its debug interceptors */
    private static OkHttpClient client() {
        OkHttpClient.Builder builder = OkHttpManager.instance().getClient().newBuilder();
        builder.interceptors().clear();
        builder.networkInterceptors().clear();
        return builder.build();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
