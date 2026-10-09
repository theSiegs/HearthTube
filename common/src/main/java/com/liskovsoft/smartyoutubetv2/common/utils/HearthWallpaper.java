package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.prefs.HearthLinkData;

/**
 * Look and layout > Wallpaper: Match Hearth (its picture, or its gradient; the default), Bing's picture of the day,
 * one of Hearth's gradients, or plain dark.
 */
public final class HearthWallpaper {
    public static final String MATCH_HEARTH = "match";
    public static final String BING = "bing";
    public static final String DARK = "dark";
    public static final String GRADIENT_PREFIX = "gradient:";
    /** Hearth's wallpaper in Hearth (contract 5): "sync:<kind>:<version>" */
    private static final String SYNC_PREFIX = "sync:";
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    /** Hearth's gradients (lib/gradients.dart): id, name, colors, rotation in radians (-1: radial) */
    public static final Object[][] GRADIENTS = {
            {"8bbdc190-ff6c-496e-8033-3c217e78da36", R.string.gradient_great_whale, new int[] {0xFF6991C7, 0xFFA3BDED}, 5.6},
            {"e89f29f3-a0a3-4ee6-a363-5e9df2a124fd", R.string.gradient_vicious_stance, new int[] {0xFF29323C, 0xFF485563}, 1.6},
            {"027e7848-104c-42eb-94ce-d25762d426c1", R.string.gradient_teen_notebook, new int[] {0xFF9795F0, 0xFFFBC8D4}, Math.PI / 2},
            {"8458ae14-7a5a-461d-bb14-154a04a9f6d2", R.string.gradient_old_hat, new int[] {0xFFFCB69F, 0xFFFFECD2}, -1.0},
            {"57801094-a300-4626-8512-ec366d7d9c59", R.string.gradient_burning_spring, new int[] {0xFF71DDA6, 0xFF70B2BC}, -1.0},
            {"34acee0a-788f-41ea-8d3c-3b7c02ea7b52", R.string.gradient_desert_hump, new int[] {0xFFC79081, 0xFFDFA579}, Math.PI / 2},
            {"7d34faa2-104a-49b7-bea5-ad48f4ccbd9c", R.string.gradient_faraway_river, new int[] {0xFF6E45E2, 0xFF88D3CE}, 7.5},
            {"1312c885-af8a-4904-a2cb-f3afa05cdd20", R.string.gradient_saint_petersburg, new int[] {0xFFF5F7FA, 0xFFC3CFE2}, 7.0},
            {"7e1c12aa-3769-4474-957a-e08ef98a93c2", R.string.gradient_african_field, new int[] {0xFFFF6B95, 0xFFFFC796}, 2.3},
            {"b9041b0b-22e3-43a1-a323-3d851f20464d", R.string.gradient_grass_shampoo, new int[] {0xFF39F3BB, 0xFF90F9C4, 0xFFDFFFCD}, 5.5},
    };

    private HearthWallpaper() {
    }

    public static String getChoice(Context context) {
        return HearthLinkData.instance(context).getWallpaper();
    }

    /**
     * A key that changes when the wallpaper would (cache key); null for Bing, which DailyBackground handles.
     */
    @Nullable
    public static String getKey(Context context) {
        String choice = getChoice(context);

        // In Hearth (contract 5), Hearth's wallpaper always: picture, Bing photo or gradient
        HearthProfile synced = getSynced(context);
        if (synced != null) {
            return SYNC_PREFIX + synced.wallpaperKind + ":" + synced.wallpaperVersion;
        }

        if (BING.equals(choice)) {
            return null;
        }

        if (MATCH_HEARTH.equals(choice)) {
            HearthProfile hearth = HearthProfile.queryHearth(context);

            if (hearth == null || hearth.wallpaperKind != null) {
                return null; // no Hearth, or a Hearth that syncs but HearthTube isn't in it: Bing
            }

            return hearth.wallpaperStamp != 0 ? "hearth:" + hearth.wallpaperStamp : "hearth-gradient:" + hearth.gradientUuid;
        }

        return choice;
    }

    /**
     * The wallpaper for a key from {@link #getKey}; null for plain dark. Blocks: call off the main thread.
     */
    @Nullable
    public static Bitmap load(Context context, String key) throws Exception {
        if (DARK.equals(key)) {
            return null;
        }

        if (key.startsWith(SYNC_PREFIX)) {
            return loadSynced(context, key);
        }

        if (key.startsWith("hearth:")) {
            return Glide.with(context)
                    .asBitmap()
                    .load(HearthProfile.WALLPAPER_URI)
                    .signature(new ObjectKey(key))
                    .centerCrop()
                    .submit(WIDTH, HEIGHT)
                    .get();
        }

        String uuid = key.startsWith("hearth-gradient:") ? key.substring("hearth-gradient:".length())
                : key.startsWith(GRADIENT_PREFIX) ? key.substring(GRADIENT_PREFIX.length()) : null;

        Object[] gradient = findGradient(uuid);
        // Hearth's default is Pitch Black
        return gradient != null ? draw((int[]) gradient[2], (double) gradient[3]) : null;
    }

    /**
     * How bright the wallpaper is, 0 (black) .. 1 (white), when it's Hearth's (in Hearth); null when unknown. Text
     * backgrounds darken more on a bright one.
     */
    @Nullable
    public static Double getBrightness(Context context) {
        HearthProfile synced = getSynced(context);

        if (synced == null) {
            return null;
        }

        if (synced.wallpaperBrightness != null) {
            return synced.wallpaperBrightness;
        }

        try {
            return synced.wallpaperGradient != null ? new org.json.JSONObject(synced.wallpaperGradient).getDouble("brightness") : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Hearth answers with its contract 5 wallpaper and HearthTube runs in Hearth; else null */
    @Nullable
    public static HearthProfile getSynced(Context context) {
        HearthProfile hearth = HearthProfile.queryHearth(context);

        if (hearth == null || hearth.contractVersion < 5 || hearth.wallpaperKind == null) {
            return null;
        }

        // In Hearth: opened from it, Hearth is the home app, or Hearth's service handles Home (Hearth takes over the
        // Home button that way while Google TV's launcher stays the system's home): casts and voice open in Hearth too
        boolean inHearth = HearthProfile.isInHearth(context) || Boolean.TRUE.equals(hearth.serviceRunning);
        return inHearth ? hearth : null;
    }

    /** Hearth's picture (or Bing photo), else its gradient, else plain dark */
    @Nullable
    private static Bitmap loadSynced(Context context, String key) {
        if (!key.startsWith(SYNC_PREFIX + "gradient:")) {
            try {
                return Glide.with(context)
                        .asBitmap()
                        .load(HearthProfile.WALLPAPER_URI)
                        .signature(new ObjectKey(key))
                        .centerCrop()
                        .submit(WIDTH, HEIGHT)
                        .get();
            } catch (Exception e) {
                // Hearth just switched to a gradient, or won't open it: its gradient
            }
        }

        HearthProfile hearth = HearthProfile.queryHearth(context);
        return hearth != null ? drawJson(hearth.wallpaperGradient) : null;
    }

    /** Hearth's gradient JSON (wallpaper-sync.md), drawn as Flutter does; null when missing or unreadable */
    @Nullable
    static Bitmap drawJson(@Nullable String json) {
        if (json == null) {
            return null;
        }

        try {
            org.json.JSONObject gradient = new org.json.JSONObject(json);
            org.json.JSONArray colorList = gradient.getJSONArray("colors");
            int[] colors = new int[colorList.length()];
            for (int i = 0; i < colors.length; i++) {
                colors[i] = android.graphics.Color.parseColor(colorList.getString(i));
            }

            if (colors.length == 0) {
                return null;
            }

            float[] stops = null;
            org.json.JSONArray stopList = gradient.optJSONArray("stops");
            if (stopList != null && stopList.length() == colors.length) {
                stops = new float[colors.length];
                for (int i = 0; i < stops.length; i++) {
                    stops[i] = (float) stopList.getDouble(i);
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            String type = gradient.optString("type");

            if (colors.length == 1 || (!"linear".equals(type) && !"radial".equals(type))) {
                canvas.drawColor(colors[0]); // one color, or a kind this Hearth version doesn't know
                return bitmap;
            }

            Shader shader;
            if ("radial".equals(type)) {
                org.json.JSONObject center = gradient.getJSONObject("center");
                shader = new RadialGradient(px(center.getDouble("x"), WIDTH), px(center.getDouble("y"), HEIGHT),
                        (float) (gradient.getDouble("radius") * Math.min(WIDTH, HEIGHT)), colors, stops, Shader.TileMode.CLAMP);
            } else {
                org.json.JSONObject begin = gradient.getJSONObject("begin");
                org.json.JSONObject end = gradient.getJSONObject("end");
                shader = new LinearGradient(px(begin.getDouble("x"), WIDTH), px(begin.getDouble("y"), HEIGHT),
                        px(end.getDouble("x"), WIDTH), px(end.getDouble("y"), HEIGHT), colors, stops, Shader.TileMode.CLAMP);
            }

            Matrix matrix = new Matrix();
            matrix.setRotate((float) Math.toDegrees(gradient.optDouble("rotation", 0)), WIDTH / 2f, HEIGHT / 2f);
            shader.setLocalMatrix(matrix);
            paint.setShader(shader);
            canvas.drawRect(0, 0, WIDTH, HEIGHT, paint);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    /** Flutter's alignment (-1..1) to pixels */
    private static float px(double alignment, int size) {
        return (float) (size / 2.0 + alignment * size / 2.0);
    }

    @Nullable
    private static Object[] findGradient(String uuid) {
        for (Object[] gradient : GRADIENTS) {
            if (gradient[0].equals(uuid)) {
                return gradient;
            }
        }

        return null;
    }

    /** Like Flutter's: linear from left to right turned about the center, or radial from the center */
    private static Bitmap draw(int[] colors, double rotation) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

        if (rotation < 0) {
            paint.setShader(new RadialGradient(WIDTH / 2f, HEIGHT / 2f, HEIGHT / 2f, colors, null, Shader.TileMode.CLAMP));
        } else {
            LinearGradient shader = new LinearGradient(0, HEIGHT / 2f, WIDTH, HEIGHT / 2f, colors, null, Shader.TileMode.CLAMP);
            Matrix matrix = new Matrix();
            matrix.setRotate((float) Math.toDegrees(rotation), WIDTH / 2f, HEIGHT / 2f);
            shader.setLocalMatrix(matrix);
            paint.setShader(shader);
        }

        canvas.drawRect(0, 0, WIDTH, HEIGHT, paint);
        return bitmap;
    }
}
