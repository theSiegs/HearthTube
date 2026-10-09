package com.liskovsoft.smartyoutubetv2.common.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog.OnChange;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Full-screen PIN entry in Google TV's style, the same screen as the Hearth launcher's parent PIN:
 * the title on the left, four slots over a shuffled "row pad" on the right.<br/>
 * Four pill rows of three places hold the ten digits and two empty places, shuffled every time the pad opens.
 * Up/Down moves between rows (and down to the backspace pill); Left, OK and Right enter the focused row's first,
 * middle and last digit. Someone watching only learns each digit was one of three, a different three each time, so
 * a parent can enter the PIN in front of the kids. Held keys don't repeat a digit; the remote's number keys type
 * directly. The PIN is checked as soon as the fourth digit is in. Back closes the screen.
 */
public class PinDialog {
    public static final int PIN_LENGTH = 4;
    private static final int ROWS = 4;
    private static final int PER_ROW = 3;
    private static final int PLACE_WIDTH_DP = 48;
    private static final int PLACE_HEIGHT_DP = 44;
    private static final int ROW_GAP_DP = 12;
    private static final int SLOT_WIDTH_DP = 48;
    private static final int SLOT_GAP_DP = 16;
    private static final int WRONG_PIN_CLEAR_MS = 400;
    private static final int WINDOW_TRIES = 10;
    private static final long WINDOW_RETRY_MS = 500;
    private final Context mContext;
    private final OnChange mOnChange;
    private final StringBuilder mEntered = new StringBuilder();
    private final View[] mDots = new View[PIN_LENGTH];
    private final View[] mUnderlines = new View[PIN_LENGTH];
    private Dialog mDialog;
    /** ROWS x PER_ROW places, each a digit or null (empty) */
    private final String[][] mLayout = shuffled();
    private final View[] mRows = new View[ROWS];
    private View mBackspace;
    private TextView mLabel;
    private boolean mError;

    /**
     * @param onChange return true to accept the PIN and close the dialog, false to reject it (slots are cleared)
     */
    public static void show(Context context, String dialogTitle, OnChange onChange) {
        show(context, dialogTitle, null, onChange, null);
    }

    public static void show(Context context, String dialogTitle, String message, OnChange onChange) {
        show(context, dialogTitle, message, onChange, null);
    }

    public static void show(Context context, String dialogTitle, String message, OnChange onChange, Runnable onDismiss) {
        showWhenOnScreen(context, dialogTitle, message, onChange, onDismiss, 0);
    }

    /**
     * A dialog needs an activity's window. Presenters can hand over the application context (asked for while
     * between screens, e.g. sign-in at the start of a fresh kid's copy, where the PIN comes first): then use the
     * activity in front, waiting a little for one to come up.
     */
    private static void showWhenOnScreen(Context context, String dialogTitle, String message, OnChange onChange,
                                         Runnable onDismiss, int tries) {
        Context target = context instanceof Activity ? context : MotherActivity.getFrontActivity();

        if (target == null && tries < WINDOW_TRIES) {
            Utils.postDelayed(() -> showWhenOnScreen(context, dialogTitle, message, onChange, onDismiss, tries + 1),
                    WINDOW_RETRY_MS);
            return;
        }

        new PinDialog(target != null ? target : context, onChange).showInt(dialogTitle, message, onDismiss);
    }

    private PinDialog(Context context, OnChange onChange) {
        mContext = context;
        mOnChange = onChange;
    }

    private void showInt(String dialogTitle, String message, Runnable onDismiss) {
        View contentView = LayoutInflater.from(mContext).inflate(R.layout.pin_dialog, null);

        ((TextView) contentView.findViewById(R.id.pin_title)).setText(dialogTitle);
        TextView messageView = contentView.findViewById(R.id.pin_message);
        messageView.setText(message);
        messageView.setVisibility(message != null ? View.VISIBLE : View.GONE);
        mLabel = contentView.findViewById(R.id.pin_label);

        createSlots(contentView.findViewById(R.id.pin_slots));
        View firstKey = createKeypad(contentView.findViewById(R.id.pin_keypad));

        mDialog = new Dialog(mContext, R.style.PinDialog);
        mDialog.setContentView(contentView);
        mDialog.setOnKeyListener((dialog, keyCode, event) -> onKey(keyCode, event));

        if (onDismiss != null) {
            mDialog.setOnDismissListener(dialog -> onDismiss.run());
        }

        updateSlots();
        firstKey.requestFocus();

        try {
            mDialog.show();
        } catch (RuntimeException e) {
            // BadTokenException: Unable to add window -- token null is not for an application
            e.printStackTrace();
            MessageHelpers.showMessage(mContext, e.getMessage());
        }
    }

    private void createSlots(ViewGroup container) {
        for (int i = 0; i < PIN_LENGTH; i++) {
            LinearLayout slot = new LinearLayout(mContext);
            slot.setOrientation(LinearLayout.VERTICAL);
            slot.setGravity(Gravity.CENTER_HORIZONTAL);

            FrameLayout dotArea = new FrameLayout(mContext);
            View dot = new View(mContext);
            dot.setBackgroundResource(R.drawable.pin_dot);
            dotArea.addView(dot, new FrameLayout.LayoutParams(dp(10), dp(10), Gravity.CENTER));
            slot.addView(dotArea, new LinearLayout.LayoutParams(dp(SLOT_WIDTH_DP), dp(28)));

            View underline = new View(mContext);
            slot.addView(underline, new LinearLayout.LayoutParams(dp(SLOT_WIDTH_DP), dp(3)));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(SLOT_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT);
            params.rightMargin = dp(SLOT_GAP_DP);
            container.addView(slot, params);

            mDots[i] = dot;
            mUnderlines[i] = underline;
        }
    }

    private static String[][] shuffled() {
        List<String> places = new ArrayList<>();
        for (int digit = 0; digit <= 9; digit++) {
            places.add(String.valueOf(digit));
        }
        places.add(null);
        places.add(null);
        Collections.shuffle(places, new SecureRandom());

        String[][] layout = new String[ROWS][PER_ROW];
        for (int i = 0; i < places.size(); i++) {
            layout[i / PER_ROW][i % PER_ROW] = places.get(i);
        }
        return layout;
    }

    /**
     * @return the first row, which gets the initial focus
     */
    private View createKeypad(ViewGroup container) {
        if (container instanceof LinearLayout) {
            ((LinearLayout) container).setGravity(Gravity.CENTER_HORIZONTAL);
        }

        for (int row = 0; row < ROWS; row++) {
            LinearLayout rowView = new LinearLayout(mContext);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            rowView.setGravity(Gravity.CENTER_VERTICAL);
            rowView.setBackgroundResource(R.drawable.pin_row_background);
            rowView.setPadding(dp(10), dp(6), dp(10), dp(6));
            rowView.setFocusable(true);
            rowView.setFocusableInTouchMode(true);
            rowView.setTag(row);

            // The chevrons say which button enters which digit; they don't change with the digit pressed
            rowView.addView(createChevron(R.drawable.ic_pin_chevron_left));
            for (int place = 0; place < PER_ROW; place++) {
                rowView.addView(createPlace(row, place), new LinearLayout.LayoutParams(dp(PLACE_WIDTH_DP), dp(PLACE_HEIGHT_DP)));
            }
            rowView.addView(createChevron(R.drawable.ic_pin_chevron_right));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = row == 0 ? 0 : dp(ROW_GAP_DP);
            container.addView(rowView, params);
            mRows[row] = rowView;
        }

        ImageView backspace = new ImageView(mContext);
        backspace.setImageResource(R.drawable.ic_pin_backspace);
        backspace.setScaleType(ImageView.ScaleType.CENTER);
        backspace.setDuplicateParentStateEnabled(false);
        ImageViewCompat.setImageTintList(backspace, ContextCompat.getColorStateList(mContext, R.color.pin_key_text));
        backspace.setBackgroundResource(R.drawable.pin_row_background);
        backspace.setContentDescription("⌫");
        backspace.setFocusable(true);
        backspace.setFocusableInTouchMode(true);
        backspace.setClickable(true);
        backspace.setOnClickListener(v -> onBackspace());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(72), dp(PLACE_HEIGHT_DP));
        params.topMargin = dp(ROW_GAP_DP + 8);
        container.addView(backspace, params);
        mBackspace = backspace;

        return mRows[0];
    }

    private View createChevron(int iconResId) {
        ImageView chevron = new ImageView(mContext);
        chevron.setImageResource(iconResId);
        chevron.setDuplicateParentStateEnabled(true);
        ImageViewCompat.setImageTintList(chevron, ContextCompat.getColorStateList(mContext, R.color.pin_chevron));
        return chevron;
    }

    private View createPlace(int row, int place) {
        TextView digit = new TextView(mContext);
        String label = mLayout[row][place];
        digit.setText(label != null ? label : "");
        digit.setGravity(Gravity.CENTER);
        digit.setTextSize(24);
        digit.setTextColor(ContextCompat.getColorStateList(mContext, R.color.pin_key_text));
        digit.setDuplicateParentStateEnabled(true);
        // Touch: tap the digit itself
        digit.setOnClickListener(v -> enter(row, place));
        return digit;
    }

    private void enter(int row, int place) {
        String digit = mLayout[row][place];
        if (digit != null) {
            onDigit(digit);
        }
    }

    private static boolean isOk(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A;
    }

    private static boolean isArrow(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private boolean onKey(int keyCode, KeyEvent event) {
        int digit = toDigit(keyCode);

        if (digit != -1 || keyCode == KeyEvent.KEYCODE_DEL) {
            // Swallow both down and up of the keys we own, act on down only
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (digit != -1) {
                    onDigit(String.valueOf(digit));
                } else {
                    onBackspace();
                }
            }
            return true;
        }

        if (!isOk(keyCode) && !isArrow(keyCode)) {
            return false; // Back and the rest
        }

        // The pad keeps the selection: OK and the arrows never reach the views below
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return true;
        }

        boolean move = keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
        if (event.getRepeatCount() > 0 && !move) {
            return true; // a held OK or arrow doesn't enter the same digit again and again
        }

        View focused = mDialog.getCurrentFocus();

        if (focused == mBackspace) {
            if (isOk(keyCode)) {
                onBackspace();
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                mRows[ROWS - 1].requestFocus();
            }
            return true;
        }

        int row = focused != null && focused.getTag() instanceof Integer ? (Integer) focused.getTag() : -1;

        if (row == -1) {
            mRows[0].requestFocus();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            enter(row, 0);
        } else if (isOk(keyCode)) {
            enter(row, 1);
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            enter(row, 2);
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (row > 0) {
                mRows[row - 1].requestFocus();
            }
        } else {
            (row < ROWS - 1 ? mRows[row + 1] : mBackspace).requestFocus();
        }

        return true;
    }

    private static int toDigit(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return keyCode - KeyEvent.KEYCODE_0;
        }

        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            return keyCode - KeyEvent.KEYCODE_NUMPAD_0;
        }

        return -1;
    }

    private void onDigit(String digit) {
        if (mEntered.length() >= PIN_LENGTH) {
            return; // a wrong PIN is still on screen, about to clear
        }

        mEntered.append(digit);
        mError = false;
        updateSlots();

        if (mEntered.length() < PIN_LENGTH) {
            return;
        }

        if (mOnChange.onChange(mEntered.toString())) {
            mDialog.dismiss();
            return;
        }

        mError = true;
        updateSlots();
        Utils.postDelayed(() -> {
            mEntered.setLength(0);
            updateSlots();
        }, WRONG_PIN_CLEAR_MS);
    }

    private void onBackspace() {
        if (mEntered.length() > 0 && mEntered.length() < PIN_LENGTH) {
            mEntered.setLength(mEntered.length() - 1);
            updateSlots();
        }
    }

    private void updateSlots() {
        for (int i = 0; i < PIN_LENGTH; i++) {
            mDots[i].setVisibility(i < mEntered.length() ? View.VISIBLE : View.INVISIBLE);
            mUnderlines[i].setBackgroundColor(ContextCompat.getColor(mContext,
                    i == mEntered.length() ? R.color.pin_text : R.color.pin_slot));
        }

        mLabel.setText(mError ? R.string.pin_wrong : R.string.pin_label);
        mLabel.setTextColor(ContextCompat.getColor(mContext, mError ? R.color.pin_error : R.color.pin_text_dim));
    }

    private int dp(int value) {
        return Math.round(value * mContext.getResources().getDisplayMetrics().density);
    }
}
