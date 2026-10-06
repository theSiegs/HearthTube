package com.liskovsoft.smartyoutubetv2.tv.ui.browse.video;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.ListRowPresenter;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.LargeVideoCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;

import java.util.ArrayList;
import java.util.List;

/**
 * HearthTube's browse rows (Home, Music, Ambiance...), the approved concept: a strip of choices under the tabs
 * (one per row: Fireplace, Rain, Ocean...), the focused video's details, and one row of big cards at the bottom,
 * with room for the wallpaper in between. Moving along the strip switches the row.
 */
public class HearthRowsFragment extends VideoRowsFragment {
    private static final int ROW_PADDING_DP = 22;
    private LinearLayout mChips;
    private HorizontalScrollView mChipScroll;
    private View mRows;
    private TextView mTitle;
    private TextView mMeta;
    private TextView mDescription;
    private List<String> mTitles = new ArrayList<>();
    private int mSelected;

    @Override
    protected boolean isStripMode() {
        return true;
    }

    @Override
    protected VideoCardPresenter createCardPresenter() {
        return new LargeVideoCardPresenter();
    }

    @Override
    protected void onRowPresenterCreated(ListRowPresenter presenter) {
        // The strip names the row
        presenter.setHeaderPresenter(null);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mRows = super.onCreateView(inflater, container, savedInstanceState);
        Context context = inflater.getContext();

        StripLayout root = new StripLayout(context);

        // The row of big cards, at the bottom
        int cardHeight = LargeVideoCardPresenter.getLargeCardDimensPx(context).second;
        // The card, plus room for the focus zoom and outline
        int rowHeight = Math.round(cardHeight * 1.12f) + dp(context, 2 * ROW_PADDING_DP);
        FrameLayout.LayoutParams rowsParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                rowHeight, Gravity.BOTTOM);
        rowsParams.bottomMargin = dp(context, 20);
        root.addView(mRows, rowsParams);

        // The strip, under the tabs
        mChipScroll = new HorizontalScrollView(context);
        mChipScroll.setHorizontalScrollBarEnabled(false);
        mChipScroll.setClipToPadding(false);
        mChipScroll.setPadding(dp(context, 56), dp(context, 6), dp(context, 56), dp(context, 6));
        mChips = new LinearLayout(context);
        mChips.setOrientation(LinearLayout.HORIZONTAL);
        mChipScroll.addView(mChips);
        FrameLayout.LayoutParams chipParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        chipParams.topMargin = dp(context, 88);
        root.addView(mChipScroll, chipParams);

        // The focused video's details, above the row
        LinearLayout details = new LinearLayout(context);
        details.setOrientation(LinearLayout.VERTICAL);
        mTitle = text(context, 22, true, 0xFFFFFFFF, 2);
        mMeta = text(context, 15, false, 0xE6FFFFFF, 1);
        mDescription = text(context, 15, false, 0xD1FFFFFF, 2);
        details.addView(mTitle);
        details.addView(mMeta);
        details.addView(mDescription);
        FrameLayout.LayoutParams detailParams = new FrameLayout.LayoutParams(
                Math.round(context.getResources().getDisplayMetrics().widthPixels * 0.5f),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        detailParams.leftMargin = dp(context, 56);
        detailParams.bottomMargin = rowsParams.bottomMargin + rowHeight + dp(context, 4);
        root.addView(details, detailParams);

        showChips();

        root.getViewTreeObserver().addOnGlobalLayoutListener(this::fitRow);

        return root;
    }

    private void fitRow() {
        androidx.leanback.widget.VerticalGridView grid = getVerticalGridView();

        if (grid == null) {
            return;
        }

        // The one row fills its box from the top (leanback would align it lower and crop it)
        int padding = dp(grid.getContext(), ROW_PADDING_DP);
        if (grid.getWindowAlignment() != androidx.leanback.widget.BaseGridView.WINDOW_ALIGN_LOW_EDGE || grid.getPaddingTop() != padding) {
            grid.setWindowAlignment(androidx.leanback.widget.BaseGridView.WINDOW_ALIGN_LOW_EDGE);
            grid.setWindowAlignmentOffset(0);
            grid.setWindowAlignmentOffsetPercent(0);
            grid.setItemAlignmentOffset(0);
            grid.setItemAlignmentOffsetPercent(0);
            grid.setPadding(grid.getPaddingLeft(), padding, grid.getPaddingRight(), padding);
            grid.setClipToPadding(false);
        }

        if (mTitle != null && TextUtils.isEmpty(mTitle.getText()) && getStripAdapter() != null && getStripAdapter().size() > 0
                && getStripAdapter().get(0) instanceof Video) {
            onVideoFocused((Video) getStripAdapter().get(0));
        }
    }

    @Override
    protected void onStripChanged(List<String> titles, int selected) {
        boolean rebuild = !titles.equals(mTitles);
        mTitles = new ArrayList<>(titles);
        mSelected = selected;

        if (mChips == null) {
            return;
        }

        if (rebuild) {
            showChips();
        } else {
            updateSelection();
        }

        // Details for the row's first video until one is focused
        if (getStripAdapter() != null && getStripAdapter().size() > 0 && getStripAdapter().get(0) instanceof Video) {
            onVideoFocused((Video) getStripAdapter().get(0));
        }
    }

    @Override
    protected void onVideoFocused(Video video) {
        if (mTitle == null || video == null) {
            return;
        }

        mTitle.setText(video.getTitle());
        CharSequence meta = video.getSecondTitle();
        mMeta.setText(meta);
        mMeta.setVisibility(TextUtils.isEmpty(meta) ? View.GONE : View.VISIBLE);
        mDescription.setText(video.description);
        mDescription.setVisibility(TextUtils.isEmpty(video.description) ? View.GONE : View.VISIBLE);
    }

    private void showChips() {
        Context context = mChips.getContext();
        View focused = mChips.findFocus();
        mChips.removeAllViews();

        for (int i = 0; i < mTitles.size(); i++) {
            int index = i;
            TextView chip = new TextView(context);
            chip.setText(mTitles.get(i));
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            chip.setTextColor(ContextCompat.getColorStateList(context, R.color.hearth_tab_text));
            chip.setBackgroundResource(R.drawable.hearth_chip_background);
            chip.setSingleLine(true);
            chip.setPadding(dp(context, 16), dp(context, 7), dp(context, 16), dp(context, 7));
            chip.setFocusable(true);
            chip.setClickable(true);
            // Moving along the strip switches the row; OK goes down to it
            chip.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus && index != mSelected) {
                    selectStripRow(index);
                }
            });
            chip.setOnClickListener(v -> focusRow());

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.rightMargin = dp(context, 10);
            mChips.addView(chip, params);
        }

        // One row needs no choosing
        mChipScroll.setVisibility(mTitles.size() > 1 ? View.VISIBLE : View.GONE);
        updateSelection();

        if (focused != null && mSelected < mChips.getChildCount()) {
            mChips.getChildAt(mSelected).requestFocus();
        }
    }

    private void updateSelection() {
        for (int i = 0; i < mChips.getChildCount(); i++) {
            mChips.getChildAt(i).setSelected(i == mSelected);
        }
    }

    private void focusRow() {
        if (mRows != null) {
            mRows.requestFocus();
        }
    }

    private View selectedChip() {
        if (mChipScroll.getVisibility() != View.VISIBLE || mChips.getChildCount() == 0) {
            return null;
        }

        return mChips.getChildAt(Math.min(mSelected, mChips.getChildCount() - 1));
    }

    private static TextView text(Context context, int sp, boolean bold, int color, int maxLines) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setMaxLines(maxLines);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setShadowLayer(8, 0, 2, 0xA6000000);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        view.setPadding(0, 0, 0, dp(context, 4));
        return view;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /**
     * Up from the row goes to the strip (not straight to the tabs); down from the strip goes to the row.
     */
    private class StripLayout extends FrameLayout {
        StripLayout(Context context) {
            super(context);
        }

        @Override
        public View focusSearch(View focused, int direction) {
            boolean inRows = isChild(mRows, focused);
            boolean inChips = isChild(mChips, focused);

            if (direction == View.FOCUS_UP && inRows) {
                View chip = selectedChip();
                if (chip != null) {
                    return chip;
                }
            } else if (direction == View.FOCUS_DOWN && inChips) {
                return mRows;
            } else if ((direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) && inChips) {
                // Stay in the strip; nothing further left: the side menu (BrowseActivity)
                View next = android.view.FocusFinder.getInstance().findNextFocus(mChips, focused, direction);
                return next != null ? next : direction == View.FOCUS_RIGHT ? focused : null;
            }

            return super.focusSearch(focused, direction);
        }

        private boolean isChild(View parent, View view) {
            for (View v = view; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
                if (v == parent) {
                    return true;
                }
            }

            return false;
        }
    }
}
