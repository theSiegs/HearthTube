package com.liskovsoft.smartyoutubetv2.common.utils;

import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog.OnChange;

/**
 * Google TV style PIN entry: one slot per digit, operated entirely from the remote.<br/>
 * Up/Down changes the current digit, Right/OK moves to the next slot, Left goes back.<br/>
 * Remote number keys type the digit directly. The PIN is submitted after the last slot.
 */
public class PinDialog {
    public static final int PIN_LENGTH = 4;
    private static final String MASK = "•";
    private final Context mContext;
    private final OnChange mOnChange;
    private final int[] mDigits = new int[PIN_LENGTH];
    private final View[] mSlots = new View[PIN_LENGTH];
    private Dialog mDialog;
    private ViewGroup mDigitsContainer;
    private TextView mErrorView;
    private int mIndex;

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
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View contentView = inflater.inflate(R.layout.pin_dialog, null);

        mDigitsContainer = contentView.findViewById(R.id.pin_digits);
        mErrorView = contentView.findViewById(R.id.pin_error);

        for (int i = 0; i < PIN_LENGTH; i++) {
            View slot = inflater.inflate(R.layout.pin_digit, mDigitsContainer, false);
            // Touch/mouse fallback for devices without a d-pad
            slot.findViewById(R.id.pin_digit_up).setOnClickListener(v -> changeDigit(1));
            slot.findViewById(R.id.pin_digit_down).setOnClickListener(v -> changeDigit(-1));
            slot.findViewById(R.id.pin_digit_value).setOnClickListener(v -> next());
            mDigitsContainer.addView(slot);
            mSlots[i] = slot;
        }

        ((TextView) contentView.findViewById(R.id.pin_title)).setText(dialogTitle);
        showError(message);

        mDialog = new Dialog(mContext, R.style.PinDialog);
        mDialog.setContentView(contentView);

        mDialog.setOnKeyListener((dialog, keyCode, event) -> onKey(keyCode, event));

        if (onDismiss != null) {
            mDialog.setOnDismissListener(dialog -> onDismiss.run());
        }

        reset();

        try {
            mDialog.show();
        } catch (RuntimeException e) {
            // BadTokenException: Unable to add window -- token null is not for an application
            e.printStackTrace();
            MessageHelpers.showMessage(mContext, e.getMessage());
        }
    }

    private boolean onKey(int keyCode, KeyEvent event) {
        int digit = toDigit(keyCode);

        boolean handled = digit != -1;

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_DEL:
                handled = true;
                break;
        }

        // Swallow both down and up of the keys we own, act on down only
        if (!handled || event.getAction() != KeyEvent.ACTION_DOWN) {
            return handled;
        }

        if (digit != -1) {
            mDigits[mIndex] = digit;
            next();
            return true;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                changeDigit(1);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                changeDigit(-1);
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DEL:
                previous();
                break;
            default: // right, center, enter
                next();
                break;
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

    private void changeDigit(int delta) {
        mDigits[mIndex] = (mDigits[mIndex] + delta + 10) % 10;
        updateSlots();
    }

    private void next() {
        if (mIndex < PIN_LENGTH - 1) {
            mIndex++;
            mDigits[mIndex] = 0;
            updateSlots();
        } else {
            submit();
        }
    }

    private void previous() {
        if (mIndex > 0) {
            mIndex--;
            updateSlots();
        }
    }

    private void submit() {
        StringBuilder pin = new StringBuilder();
        for (int digit : mDigits) {
            pin.append(digit);
        }

        if (mOnChange.onChange(pin.toString())) {
            mDialog.dismiss();
        } else {
            showError(mContext.getString(R.string.pin_wrong));
            shake();
            reset();
        }
    }

    private void reset() {
        mIndex = 0;
        mDigits[0] = 0;
        updateSlots();
    }

    private void updateSlots() {
        for (int i = 0; i < PIN_LENGTH; i++) {
            boolean current = i == mIndex;
            TextView value = mSlots[i].findViewById(R.id.pin_digit_value);

            value.setSelected(current);
            value.setText(i < mIndex ? MASK : current ? String.valueOf(mDigits[i]) : "");
            mSlots[i].findViewById(R.id.pin_digit_up).setVisibility(current ? View.VISIBLE : View.INVISIBLE);
            mSlots[i].findViewById(R.id.pin_digit_down).setVisibility(current ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void showError(String message) {
        mErrorView.setText(message);
        mErrorView.setVisibility(message != null ? View.VISIBLE : View.INVISIBLE);
    }

    private void shake() {
        ObjectAnimator.ofFloat(mDigitsContainer, "translationX", 0, 25, -25, 20, -20, 10, -10, 0)
                .setDuration(400)
                .start();
    }
}
