package com.liskovsoft.smartyoutubetv2.common.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;

/**
 * HearthTube: the full-screen page a kid sees in place of HearthTube when the daily YouTube allowance is used up, or
 * YouTube is locked (bedtime, school time, a pause): "Out of YouTube time", why, and, for time used up, to ask an adult
 * for more. Every other HearthTube screen is closed behind it; after a few seconds (or any key) it goes to Hearth's
 * home and closes too. {@link HearthScreenTime} brings it up. Declared in the hearthtube flavor's manifest only.
 */
public class HearthTimeUpActivity extends Activity {
    private static final String TAG = HearthTimeUpActivity.class.getSimpleName();
    private static final String EXTRA_LOCKED = "locked";
    private static final String EXTRA_MESSAGE = "message";
    /** How long it stays before going home */
    private static final long SHOW_MS = 10_000;
    /** Keys held or queued from the screen before don't dismiss it at once */
    private static final long IGNORE_KEYS_MS = 1_500;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mLeave = this::leave;
    private long mShownAtMs;
    private TextView mTitle;
    private TextView mMessage;
    private TextView mAsk;

    /** Brings the page up. False when this build has no such page (SmartTube's own flavors) or it can't start. */
    public static boolean show(Context context, HearthAllowance.Refusal refusal) {
        Intent intent = new Intent(context, HearthTimeUpActivity.class)
                .putExtra(EXTRA_LOCKED, refusal.locked)
                .putExtra(EXTRA_MESSAGE, refusal.message)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            if (context.getPackageManager().resolveActivity(intent, 0) == null) {
                return false;
            }

            context.startActivity(intent);
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, "Can't show the time-up page: %s", e.getMessage());
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildView());
        bind(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        bind(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mShownAtMs = SystemClock.elapsedRealtime();
        // Nothing of HearthTube is left behind it: Back or going home can't land on a HearthTube screen
        closeOtherScreens();
        mHandler.removeCallbacks(mLeave);
        mHandler.postDelayed(mLeave, SHOW_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mLeave);
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Out of sight some other way (Home, another app): it's done, and isn't left waiting in the background
        if (!isFinishing() && !isChangingConfigurations()) {
            finishAndRemoveTask();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (SystemClock.elapsedRealtime() - mShownAtMs >= IGNORE_KEYS_MS) {
            leave();
        }

        // Every key is this page's: nothing behind it reacts
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return true;
    }

    /** To Hearth's home, then gone. In a kids profile's own user there's no home to start: what's underneath shows. */
    private void leave() {
        if (isFinishing()) {
            return;
        }

        mHandler.removeCallbacks(mLeave);
        HearthProfile.goHome(this);
        finishAndRemoveTask();
    }

    private void closeOtherScreens() {
        try {
            ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ComponentName self = new ComponentName(this, HearthTimeUpActivity.class);

            for (ActivityManager.AppTask task : manager.getAppTasks()) {
                Intent base = task.getTaskInfo().baseIntent;

                if (base == null || !self.equals(base.getComponent())) {
                    task.finishAndRemoveTask();
                }
            }
        } catch (RuntimeException e) {
            // The app has already finished its screens (HearthScreenTime): what's left goes to the back on resume
            Log.e(TAG, "Can't close the other screens: %s", e.getMessage());
        }
    }

    private void bind(Intent intent) {
        boolean locked = intent != null && intent.getBooleanExtra(EXTRA_LOCKED, false);
        String message = intent != null ? intent.getStringExtra(EXTRA_MESSAGE) : null;

        mTitle.setText(locked ? R.string.youtube_off_title : R.string.youtube_time_up_title);
        mMessage.setText(message);
        mMessage.setVisibility(message != null && !message.isEmpty() ? View.VISIBLE : View.GONE);
        // Asking for more time helps only when time is used up, not at bedtime
        mAsk.setVisibility(locked ? View.GONE : View.VISIBLE);
    }

    private View buildView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF14202C, 0xFF0B1118}));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(48);
        column.setPadding(pad, pad, pad, pad);

        int accent = Color.WHITE;
        try {
            accent = HearthAccent.resolve(this);
        } catch (RuntimeException e) {
            // Hearth's default stays
        }

        View bar = new View(this);
        GradientDrawable barShape = new GradientDrawable();
        barShape.setColor(accent);
        barShape.setCornerRadius(dp(3));
        bar.setBackground(barShape);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(96), dp(6));
        barParams.bottomMargin = dp(28);
        column.addView(bar, barParams);

        mTitle = text(40, Color.WHITE, true);
        column.addView(mTitle);
        mMessage = text(24, 0xE6FFFFFF, false);
        column.addView(mMessage, margins(dp(20)));
        mAsk = text(24, accent, true);
        mAsk.setText(R.string.youtube_time_up_ask);
        column.addView(mAsk, margins(dp(28)));
        TextView leaving = text(16, 0x99FFFFFF, false);
        leaving.setText(R.string.youtube_time_up_leaving);
        column.addView(leaving, margins(dp(48)));

        root.addView(column, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return root;
    }

    private TextView text(int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        if (bold) {
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return view;
    }

    private static LinearLayout.LayoutParams margins(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = top;
        return params;
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
