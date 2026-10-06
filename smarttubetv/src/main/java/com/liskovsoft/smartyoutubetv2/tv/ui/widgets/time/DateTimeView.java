package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.time;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager.TickleListener;
import com.liskovsoft.smartyoutubetv2.common.utils.HearthLook;

import java.util.Date;

/**
 * Note, same view is used inside player and in as global time view
 */
@SuppressLint("AppCompatCustomView")
public class DateTimeView extends TextView implements TickleListener {
    private TickleManager mTickleManager;
    private boolean mIsDateEnabled = true;
    private boolean mIsTimeEnabled = true;

    public DateTimeView(Context context) {
        super(context);
        init();
    }

    public DateTimeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DateTimeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mTickleManager = TickleManager.instance();
        updateListener();
    }

    private void updateListener() {
        if (getVisibility() == View.VISIBLE) {
            mTickleManager.addListener(this);
        } else {
            mTickleManager.removeListener(this);
        }
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);

        updateListener();
    }

    @Override
    public void onTickle() {
        if (getVisibility() == View.VISIBLE) {
            String time;

            if (mIsDateEnabled && !mIsTimeEnabled) {
                // HearthTube: Hearth's date and time formats ("Tue, Oct 6", "2:20 PM")
                time = HearthLook.formatDate(new Date());
            } else if (!mIsDateEnabled && mIsTimeEnabled) {
                time = HearthLook.formatTime(new Date());
            } else {
                // The player's clock: Hearth's formats too
                Date now = new Date();
                time = HearthLook.formatDate(now) + "   " + HearthLook.formatTime(now);
            }

            // https://stackoverflow.com/questions/5437674/what-unicode-characters-represent-time/9454080
            //setText(String.format("⌚ %s", time));
            setText(time);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        // Player has been closed
        mTickleManager.removeListener(this);
    }

    /**
     * Note, same view is used inside player and in as global time view
     */
    public void showDate(boolean show) {
        mIsDateEnabled = show;
    }

    public void showTime(boolean show) {
        mIsTimeEnabled = show;
    }
}
