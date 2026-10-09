package com.liskovsoft.smartyoutubetv2.tv.ui.browse.dialog;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.BrowseSupportFragment.MainFragmentAdapter;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.CategoryEmptyError;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.SignInError;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.fragments.ErrorSupportFragment;

public class ErrorDialogFragment extends ErrorSupportFragment implements BrowseSupportFragment.MainFragmentAdapterProvider {
    private static final boolean TRANSLUCENT = true;
    private static final int TIMER_DELAY = 1000;
    // Override style value 'lb_error_message_max_lines'
    private static final int NORMAL_MAX_LINES = 7;
    private static final int EXPANDED_MAX_LINES = 15;

    private final Handler mHandler = new Handler();
    private final ErrorFragmentData mDialogData;
    private final MainFragmentAdapter<Fragment> mMainFragmentAdapter =
            new MainFragmentAdapter<Fragment>(this) {
                @Override
                public void setEntranceTransitionState(boolean state) {
                    //setEntranceTransitionState(state);
                }
            };

    public ErrorDialogFragment() {
        // "could not find Fragment constructor" fix
        this(null);
    }

    public ErrorDialogFragment(ErrorFragmentData dialogData) {
        mDialogData = dialogData;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();

        setDialogContent();
    }

    private void setDialogContent() {
        if (mDialogData == null || getActivity() == null) {
            return;
        }

        boolean hearth = com.liskovsoft.smartyoutubetv2.tv.util.CardFocusOutline.get(getActivity()) != 0;

        // HearthTube: no sad cloud in the Hearth look, just the message and a Hearth button
        if (!hearth && (mDialogData instanceof CategoryEmptyError || mDialogData instanceof SignInError)) {
            setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.lb_ic_sad_cloud));
        }

        setMessage(mDialogData.getMessage());

        // HearthTube: the message and its button in a dark card with white text, like Hearth's "nothing to watch"
        TextView message = (TextView) Helpers.getField(this, "mTextView");
        if (hearth && message != null && message.getParent() instanceof View) {
            float density = getResources().getDisplayMetrics().density;
            View card = (View) message.getParent();
            card.setBackgroundResource(R.drawable.hearth_empty_card);
            card.setPadding(Math.round(40 * density), Math.round(28 * density), Math.round(40 * density), Math.round(28 * density));
            message.setTextColor(0xFFFFFFFF);
            message.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
            message.setPadding(0, 0, 0, Math.round(16 * density));
        }

        TextView mTextView = (TextView) Helpers.getField(this, "mTextView");
        ImageView mImageView = (ImageView) Helpers.getField(this, "mImageView");
        if (mTextView != null && mImageView != null) {
            mTextView.setMaxLines(mImageView.getVisibility() == View.GONE ? EXPANDED_MAX_LINES : NORMAL_MAX_LINES);
        }

        if (mDialogData.getActionText() != null) {
            setButtonText(mDialogData.getActionText());
            setButtonClickListener(v -> mDialogData.onAction());

            Button button = (Button) Helpers.getField(this, "mButton");
            if (hearth && button != null) {
                float density = getResources().getDisplayMetrics().density;
                button.setBackgroundResource(R.drawable.hearth_button_background);
                button.setTextColor(ContextCompat.getColorStateList(getActivity(), R.color.hearth_button_text));
                button.setAllCaps(false);
                button.setPadding(Math.round(24 * density), Math.round(8 * density), Math.round(24 * density), Math.round(8 * density));
            }
        } else {
            Button mButton = (Button) Helpers.getField(this, "mButton");

            if (mButton != null) {
                mButton.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public MainFragmentAdapter<Fragment> getMainFragmentAdapter() {
        return mMainFragmentAdapter;
    }
}
