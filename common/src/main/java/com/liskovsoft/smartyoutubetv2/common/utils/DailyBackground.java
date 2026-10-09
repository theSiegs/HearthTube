package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.bumptech.glide.Glide;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.sharedutils.rx.RxHelper;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.reactivex.disposables.Disposable;
import okhttp3.Response;

/**
 * Bing "image of the day" as a backdrop, shown in its natural colors.<br/>
 * Readability is handled by the text on top of it (shadows), not by darkening the image.
 */
public class DailyBackground {
    private static final String TAG = DailyBackground.class.getSimpleName();
    private static final String PREFS = "daily_background";
    private static final String KEY_DAY = "day";
    private static final String KEY_URL_BASE = "url_base";
    private static final String KEY_CREDIT = "credit";
    private static final String BING_HOST = "https://www.bing.com";
    private static final String BING_API = BING_HOST + "/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=";
    private static final String BING_SIZE = "_1920x1080.jpg";
    private static final boolean BLUR = false;
    // A blurred image can be tiny, a sharp one needs full HD
    private static final int WIDTH = BLUR ? 480 : 1920;
    private static final int HEIGHT = BLUR ? 270 : 1080;
    private static final int BLUR_RADIUS = 2;
    // Every screen shows the same image, so load it once per day
    private static Result sResult;
    private static String sResultKey;

    public static class Result {
        public final Bitmap image;
        public final String credit;

        private Result(Bitmap image, String credit) {
            this.image = image;
            this.credit = credit;
        }
    }

    public interface Callback {
        void onReady(Result result);
    }

    /**
     * @return dispose it when the screen goes away (null when served from memory). Nothing is delivered on failure, the caller keeps its fallback.
     */
    public static Disposable load(Context context, Callback callback) {
        Context appContext = context.getApplicationContext();
        // HearthTube: Look and layout > Wallpaper picks the source; Bing (a new picture each day) is one of them
        String wallpaper = HearthWallpaper.getKey(appContext);
        String key = wallpaper != null ? wallpaper : getToday();

        if (sResult != null && key.equals(sResultKey)) {
            callback.onReady(sResult);
            return null;
        }

        return RxHelper.execute(
                RxHelper.fromCallable(() -> wallpaper != null ?
                        new Result(HearthWallpaper.load(appContext, wallpaper), null) : loadInt(appContext)),
                result -> {
                    sResult = result;
                    sResultKey = key;
                    callback.onReady(result);
                },
                error -> Log.e(TAG, "Daily background unavailable: %s", error.getMessage()));
    }

    /** The wallpaper for this key is the one already shown (nothing to reload) */
    public static boolean isShowing(Context context) {
        String wallpaper = HearthWallpaper.getKey(context.getApplicationContext());
        String key = wallpaper != null ? wallpaper : getToday();
        return sResult != null && key.equals(sResultKey);
    }

    /**
     * Caption and photographer of today's image, for the About screen. Null until the image has been fetched.
     */
    public static String getCredit(Context context) {
        if (!HearthWallpaper.BING.equals(HearthWallpaper.getChoice(context)) && HearthWallpaper.getKey(context) != null) {
            return null; // not Bing's picture
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return getToday().equals(prefs.getString(KEY_DAY, null)) ? prefs.getString(KEY_CREDIT, null) : null;
    }

    private static String getToday() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    private static Result loadInt(Context context) throws Exception {
        String[] image = getTodaysImage(context);

        Bitmap source = Glide.with(context)
                .asBitmap()
                .load(image[0])
                .centerCrop()
                .submit(WIDTH, HEIGHT)
                .get();

        return new Result(BLUR ? blur(source, BLUR_RADIUS) : source, image[1]);
    }

    /**
     * One Bing request per day, the image itself is kept by Glide's disk cache.
     * @return url and credit line
     */
    private static String[] getTodaysImage(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String today = getToday();

        String urlBase = prefs.getString(KEY_URL_BASE, null);

        if (urlBase != null && today.equals(prefs.getString(KEY_DAY, null))) {
            return new String[] {urlBase + BING_SIZE, prefs.getString(KEY_CREDIT, null)};
        }

        Locale locale = Locale.getDefault();
        String market = locale.getLanguage() + "-" + locale.getCountry(); // unknown markets fall back to worldwide

        String credit;

        try (Response response = OkHttpManager.instance().doGetRequest(BING_API + market)) {
            if (response == null || response.body() == null) {
                throw new IllegalStateException("No response from Bing");
            }

            JSONObject image = new JSONObject(response.body().string()).getJSONArray("images").getJSONObject(0);
            urlBase = BING_HOST + image.getString("urlbase");
            credit = image.optString("copyright", null);
        }

        prefs.edit()
                .putString(KEY_DAY, today)
                .putString(KEY_URL_BASE, urlBase)
                .putString(KEY_CREDIT, credit)
                .apply();

        return new String[] {urlBase + BING_SIZE, credit};
    }

    /**
     * Three box blur passes, close to a gaussian. Fine for a 480x270 image on a TV box.
     */
    private static Bitmap blur(Bitmap source, int radius) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        int[] buffer = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int pass = 0; pass < 3; pass++) {
            boxBlur(pixels, buffer, width, height, radius, true);
            boxBlur(buffer, pixels, width, height, radius, false);
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    private static void boxBlur(int[] in, int[] out, int width, int height, int radius, boolean horizontal) {
        int lines = horizontal ? height : width;
        int length = horizontal ? width : height;

        for (int line = 0; line < lines; line++) {
            for (int pos = 0; pos < length; pos++) {
                int r = 0, g = 0, b = 0, n = 0;

                for (int k = Math.max(0, pos - radius); k <= Math.min(length - 1, pos + radius); k++) {
                    int color = in[horizontal ? line * width + k : k * width + line];
                    r += Color.red(color);
                    g += Color.green(color);
                    b += Color.blue(color);
                    n++;
                }

                out[horizontal ? line * width + pos : pos * width + line] = Color.rgb(r / n, g / n, b / n);
            }
        }
    }
}
