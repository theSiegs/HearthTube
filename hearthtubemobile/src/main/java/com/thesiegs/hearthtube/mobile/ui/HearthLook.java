package com.thesiegs.hearthtube.mobile.ui;

import android.app.Activity;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.utils.DailyBackground;
import com.liskovsoft.smartyoutubetv2.common.utils.HearthAccent;
import com.liskovsoft.smartyoutubetv2.common.utils.HearthProfile;

import io.reactivex.disposables.Disposable;

/**
 * HearthTube's look on a phone or tablet, the same one the TV app has (the Hearth launcher's): the daily wallpaper
 * (Hearth's own, or Bing's picture of the day) under a shade that is darker at the top and bottom, dark "glass" pills
 * with the shown one in white, and Hearth's accent color for the buttons. Colors and shapes follow the TV's
 * hearth_*.xml drawables.
 */
public final class HearthLook {
    /** The TV's glass: dark, a little see-through, with a faint outline */
    public static final int GLASS = 0xB3141418;
    public static final int GLASS_OUTLINE = 0x24FFFFFF;
    /** Text over a picture is readable by its shadow, not by darkening the picture */
    private static final int TEXT_SHADOW = 0x99000000;

    private HearthLook() {
    }

    public static int accent(Context context) {
        return HearthAccent.resolve(context);
    }

    public static int dp(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Black or white, whichever reads on this color */
    public static int onColor(int color) {
        double luminance = (0.299 * ((color >> 16) & 0xFF) + 0.587 * ((color >> 8) & 0xFF) + 0.114 * (color & 0xFF)) / 255;
        return luminance > 0.6 ? Color.BLACK : Color.WHITE;
    }

    public static void textShadow(TextView text) {
        text.setShadowLayer(dp(text.getContext(), 2), 0, dp(text.getContext(), 1), TEXT_SHADOW);
    }

    private static GradientDrawable pill(Context context, int fill, int strokeColor, float strokeDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(context, 100));
        shape.setColor(fill);

        if (strokeDp > 0) {
            shape.setStroke(Math.max(1, dp(context, strokeDp)), strokeColor);
        }

        return shape;
    }

    /** Android's default focus highlight is a square that doesn't follow a pill's shape */
    private static void noFocusSquare(View view) {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            view.setDefaultFocusHighlightEnabled(false);
        }
    }

    /** A choice in a strip (the TV's chips): dark glass, and the shown one white */
    public static void chip(TextView view, boolean shown) {
        Context context = view.getContext();
        noFocusSquare(view);
        view.setBackground(shown ? pill(context, Color.WHITE, 0, 0) : pill(context, GLASS, GLASS_OUTLINE, 1));
        view.setTextColor(shown ? Color.BLACK : Color.WHITE);
    }

    /** The floating bottom bar or side rail: one dark glass capsule with a faint outline */
    public static void glassBar(View view) {
        // More solid than a chip: videos scroll under the bar and their titles must not show through its labels
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xF0141418);
        shape.setCornerRadius(dp(view.getContext(), 32));
        shape.setStroke(Math.max(1, dp(view.getContext(), 1)), GLASS_OUTLINE);
        view.setBackground(shape);
    }

    /** One destination on that bar: the shown one is a white capsule with dark icon and text, the others sit on the glass */
    public static void navItem(View item, boolean shown) {
        noFocusSquare(item);
        item.setBackground(shown ? pill(item.getContext(), Color.WHITE, 0, 0) : null);
        item.setSelected(shown);
    }

    /** A channel's circle (Subscriptions): a ring in the accent when it is the one shown, a faint one otherwise */
    public static void circleRing(View ring, boolean shown) {
        Context context = ring.getContext();
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(Color.TRANSPARENT);

        if (shown) {
            shape.setStroke(dp(context, 3), accent(context));
        } else {
            shape.setStroke(Math.max(1, dp(context, 1)), GLASS_OUTLINE);
        }

        ring.setBackground(shape);
    }

    /** A top button (Search, Settings): filled with Hearth's accent */
    public static void accentButton(TextView view) {
        Context context = view.getContext();
        noFocusSquare(view);
        int accent = accent(context);
        view.setBackground(pill(context, accent, 0, 0));
        view.setTextColor(onColor(accent));
    }

    /** A button on a page of its own ("Sign in"): dark glass with a ring in the accent */
    public static void glassButton(TextView view) {
        Context context = view.getContext();
        noFocusSquare(view);
        view.setBackground(pill(context, GLASS, accent(context), 2));
        view.setTextColor(Color.WHITE);
    }

    /** The search box, like Hearth's (search_entry.dart): #EE1E2026, 30dp corners, a 2dp accent border while typing */
    public static void searchBox(TextView view) {
        Context context = view.getContext();
        GradientDrawable idle = new GradientDrawable();
        idle.setColor(0xEE1E2026);
        idle.setCornerRadius(dp(context, 30));
        GradientDrawable typing = new GradientDrawable();
        typing.setColor(0xEE1E2026);
        typing.setCornerRadius(dp(context, 30));
        typing.setStroke(dp(context, 2), accent(context));

        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, typing);
        states.addState(new int[] {}, idle);
        view.setBackground(states);
    }

    /** A row in a menu: Hearth's panel surface (#0F0F0F) a little see-through, with its faint outline and rounded corners */
    public static void panelRow(View view) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xE60F0F0F);
        shape.setCornerRadius(dp(view.getContext(), 16));
        shape.setStroke(Math.max(1, dp(view.getContext(), 1)), 0x14FFFFFF);
        view.setBackground(shape);
    }

    /** Checks, radio buttons and switches in the accent color */
    public static void accentTint(android.widget.CompoundButton button) {
        int accent = accent(button.getContext());
        int[][] states = {{android.R.attr.state_checked}, {}};
        // Off is grey, so a switch reads at a glance
        android.content.res.ColorStateList tint = new android.content.res.ColorStateList(states, new int[] {accent, 0xFFB0B0B0});
        button.setButtonTintList(tint);

        if (button instanceof android.widget.Switch) {
            ((android.widget.Switch) button).setThumbTintList(tint);
            ((android.widget.Switch) button).setTrackTintList(new android.content.res.ColorStateList(states,
                    new int[] {(accent & 0x00FFFFFF) | 0x66000000, 0x66808080}));
        }
    }

    /** The TV's empty-page card: black at 55%, 20dp corners */
    public static void emptyCard(View view) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0x8C000000);
        shape.setCornerRadius(dp(view.getContext(), 20));
        view.setBackground(shape);
    }

    /** The "Likely AI" pill: dark glass, solid enough to read on any thumbnail */
    public static void aiLabel(TextView view) {
        Context context = view.getContext();
        view.setBackground(pill(context, 0xD9141418, 0x33FFFFFF, 1));
        view.setTextColor(Color.WHITE);
    }

    /**
     * The screen's wallpaper, behind everything: loaded once per day (DailyBackground keeps it), shown again when Hearth
     * changes its own. A screen that doesn't have the picture yet, or has none (plain dark), is black.
     */
    public static final class Backdrop {
        private final Activity mActivity;
        private final BackdropDrawable mDrawable = new BackdropDrawable();
        private final Handler mHandler = new Handler(Looper.getMainLooper());
        private Disposable mLoad;
        private boolean mStarted;
        private final ContentObserver mHearthObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                if (mStarted && !DailyBackground.isShowing(mActivity)) {
                    load();
                }
            }
        };

        public Backdrop(Activity activity) {
            mActivity = activity;
            activity.getWindow().setBackgroundDrawable(mDrawable);
        }

        public void start() {
            mStarted = true;
            HearthProfile.noteLaunch(mActivity);

            for (Uri hearth : HearthProfile.ACTIVE_URIS) {
                try {
                    mActivity.getContentResolver().registerContentObserver(hearth, false, mHearthObserver);
                } catch (Exception e) {
                    // No Hearth
                }
            }

            load();
        }

        public void stop() {
            mStarted = false;
            mActivity.getContentResolver().unregisterContentObserver(mHearthObserver);

            if (mLoad != null) {
                mLoad.dispose();
                mLoad = null;
            }
        }

        private void load() {
            if (mLoad != null) {
                mLoad.dispose();
            }

            mLoad = DailyBackground.load(mActivity, result -> mDrawable.setImage(result.image));
        }
    }

    /**
     * The picture cropped to fill the screen (a phone is tall and the picture is wide), then Hearth's shade over it:
     * darker at the top and bottom so text reads on any picture (hearth_wallpaper_shade.xml).
     */
    static final class BackdropDrawable extends Drawable {
        private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint mShade = new Paint();
        private final Rect mSource = new Rect();
        private Bitmap mImage;

        void setImage(@Nullable Bitmap image) {
            mImage = image;
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            mShade.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                    new int[] {0x59000000, 0x26000000, 0x73000000}, null, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            canvas.drawColor(Color.BLACK);

            if (mImage != null && !bounds.isEmpty()) {
                float scale = Math.max(bounds.width() / (float) mImage.getWidth(), bounds.height() / (float) mImage.getHeight());
                int width = Math.min(mImage.getWidth(), Math.round(bounds.width() / scale));
                int height = Math.min(mImage.getHeight(), Math.round(bounds.height() / scale));
                int left = (mImage.getWidth() - width) / 2;
                int top = (mImage.getHeight() - height) / 2;
                mSource.set(left, top, left + width, top + height);
                canvas.drawBitmap(mImage, mSource, bounds, mPaint);
                canvas.drawRect(bounds, mShade);
            }
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(@Nullable android.graphics.ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }
}
