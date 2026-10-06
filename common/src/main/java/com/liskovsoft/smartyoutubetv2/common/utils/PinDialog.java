package com.liskovsoft.smartyoutubetv2.common.utils;

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
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog.OnChange;

/**
 * Full-screen PIN entry in Google TV's style, the same screen as the Hearth launcher's parent PIN:
 * the title on the left, four slots over a round keypad on the right.<br/>
 * The D-pad moves around the keypad and OK presses a key; the remote's number keys type directly.
 * The PIN is checked as soon as the fourth digit is in. Back closes the screen.
 */
public class PinDialog {
    public static final int PIN_LENGTH = 4;
    private static final String[][] KEYPAD = {
            {"1", "2", "3"},
            {"4", "5", "6"},
            {"7", "8", "9"},
            {null, "0", "⌫"}
    };
    private static final String BACKSPACE = "⌫";
    private static final int KEY_SIZE_DP = 56;
    private static final int KEY_MARGIN_DP = 7;
    private static final int SLOT_WIDTH_DP = 48;
    private static final int SLOT_GAP_DP = 16;
    private static final int WRONG_PIN_CLEAR_MS = 400;
    private final Context mContext;
    private final OnChange mOnChange;
    private final StringBuilder mEntered = new StringBuilder();
    private final View[] mDots = new View[PIN_LENGTH];
    private final View[] mUnderlines = new View[PIN_LENGTH];
    private Dialog mDialog;
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
        new PinDialog(context, onChange).showInt(dialogTitle, message, onDismiss);
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

    /**
     * @return the "1" key, which gets the initial focus
     */
    private View createKeypad(ViewGroup container) {
        View firstKey = null;

        for (String[] row : KEYPAD) {
            LinearLayout rowView = new LinearLayout(mContext);
            rowView.setOrientation(LinearLayout.HORIZONTAL);

            for (String label : row) {
                View key = label == null ? new View(mContext) : createKey(label);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(KEY_SIZE_DP), dp(KEY_SIZE_DP));
                params.setMargins(dp(KEY_MARGIN_DP), dp(KEY_MARGIN_DP), dp(KEY_MARGIN_DP), dp(KEY_MARGIN_DP));
                rowView.addView(key, params);

                if (firstKey == null && label != null) {
                    firstKey = key;
                }
            }

            container.addView(rowView);
        }

        return firstKey;
    }

    private View createKey(String label) {
        View key;

        if (BACKSPACE.equals(label)) {
            ImageView icon = new ImageView(mContext);
            icon.setImageResource(R.drawable.ic_pin_backspace);
            icon.setScaleType(ImageView.ScaleType.CENTER);
            ImageViewCompat.setImageTintList(icon, ContextCompat.getColorStateList(mContext, R.color.pin_key_text));
            icon.setContentDescription(label);
            icon.setOnClickListener(v -> onBackspace());
            key = icon;
        } else {
            TextView digit = new TextView(mContext);
            digit.setText(label);
            digit.setGravity(Gravity.CENTER);
            digit.setTextSize(22);
            digit.setTextColor(ContextCompat.getColorStateList(mContext, R.color.pin_key_text));
            digit.setOnClickListener(v -> onDigit(label));
            key = digit;
        }

        key.setBackgroundResource(R.drawable.pin_key_background);
        key.setFocusable(true);
        key.setClickable(true);

        return key;
    }

    private boolean onKey(int keyCode, KeyEvent event) {
        int digit = toDigit(keyCode);
        boolean handled = digit != -1 || keyCode == KeyEvent.KEYCODE_DEL;

        // Swallow both down and up of the keys we own, act on down only
        if (!handled || event.getAction() != KeyEvent.ACTION_DOWN) {
            return handled;
        }

        if (digit != -1) {
            onDigit(String.valueOf(digit));
        } else {
            onBackspace();
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
