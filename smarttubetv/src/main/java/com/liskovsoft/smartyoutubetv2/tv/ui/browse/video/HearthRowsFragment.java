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
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.LargeVideoCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.MoreTilePresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * HearthTube's browse rows (Home, Music, Ambiance...), the approved concept: a strip of choices under the tabs
 * (one per row: Fireplace, Rain, Ocean...), the focused video's details, and one row of big cards at the bottom,
 * with room for the wallpaper in between. Moving along the strip switches the row.
 */
public class HearthRowsFragment extends VideoRowsFragment {
    private static final int ROW_PADDING_DP = 22;
    /** Channel circles in Subscriptions */
    private static final int CIRCLE_DP = 60;
    /** Videos in the big row before the More tile */
    private static final int PREVIEW_COUNT = 10;
    /** The classic grid behind More */
    private static final int GRID_COLUMNS = 4;
    private LinearLayout mChips;
    private HorizontalScrollView mChipScroll;
    private View mRows;
    private TextView mTitle;
    private TextView mMeta;
    private TextView mDescription;
    private List<String> mTitles = new ArrayList<>();
    /** Channel pictures (Subscriptions): circles instead of chips */
    private List<String> mIcons = new ArrayList<>();
    private int mSelected;
    private VideoCardPresenter mLargePresenter;
    private VideoCardPresenter mGridPresenter;
    private final MoreTilePresenter mMorePresenter = new MoreTilePresenter();
    private final Map<ListRow, ListRow> mPreviews = new IdentityHashMap<>();
    private View mDetails;
    private int mRowHeight;
    private int mRowBottomMargin;
    /** The full row shown as a grid (More), or null */
    private ObjectAdapter mGridSource;
    private final List<ListRow> mGridRows = new ArrayList<>();
    private final ObjectAdapter.DataObserver mGridObserver = new ObjectAdapter.DataObserver() {
        @Override
        public void onChanged() {
            fillGrid();
        }
    };

    @Override
    protected boolean isStripMode() {
        return true;
    }

    @Override
    protected VideoCardPresenter createCardPresenter() {
        mLargePresenter = new LargeVideoCardPresenter();
        return mLargePresenter;
    }

    /**
     * The big row: its first videos, then a More tile when there are more.
     */
    @Override
    protected ListRow toDisplayRow(ListRow row) {
        ListRow preview = mPreviews.get(row);

        if (preview == null) {
            preview = new ListRow(row.getHeaderItem(), new PreviewAdapter(row.getAdapter()));
            mPreviews.put(row, preview);
        }

        return preview;
    }

    @Override
    protected ObjectAdapter sourceOf(ObjectAdapter shown) {
        return shown instanceof PreviewAdapter ? ((PreviewAdapter) shown).mSource : shown;
    }

    @Override
    protected boolean onOtherItemClicked(Object item) {
        if (item instanceof MoreTilePresenter.More) {
            showGrid();
            return true;
        }

        return false;
    }

    /**
     * Back in the grid goes back to the big row. True: handled.
     */
    public boolean onBack() {
        if (mGridSource == null) {
            return false;
        }

        hideGrid();
        return true;
    }

    private void showGrid() {
        ListRow row = getStripRow(mSelected);

        if (row == null || mRows == null) {
            return;
        }

        if (mGridPresenter == null) {
            mGridPresenter = new VideoCardPresenter();
            wireCardPresenter(mGridPresenter);
        }

        mGridSource = row.getAdapter();
        mGridRows.clear();
        fillGrid();
        mGridSource.registerObserver(mGridObserver);

        // The grid takes the screen under the tabs
        mChipScroll.setVisibility(View.GONE);
        mDetails.setVisibility(View.GONE);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mRows.getLayoutParams();
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        // First row level with the classic grids (the queue, Watch later)
        params.topMargin = dp(mRows.getContext(), 124);
        params.bottomMargin = 0;
        mRows.setLayoutParams(params);

        showRows(new ArrayList<>(mGridRows));
        mRows.requestFocus();
    }

    private void hideGrid() {
        if (mGridSource != null) {
            mGridSource.unregisterObserver(mGridObserver);
            mGridSource = null;
        }

        mGridRows.clear();
        mChipScroll.setVisibility(mTitles.size() > 1 ? View.VISIBLE : View.GONE);
        mDetails.setVisibility(View.VISIBLE);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mRows.getLayoutParams();
        params.height = mRowHeight;
        params.topMargin = 0;
        params.bottomMargin = mRowBottomMargin;
        mRows.setLayoutParams(params);

        showRows(null);
        mRows.requestFocus();
    }

    /** Rows of four from the full row; more rows come as SmartTube loads more videos */
    private void fillGrid() {
        if (mGridSource == null) {
            return;
        }

        boolean showing = !mGridRows.isEmpty();
        List<ListRow> added = new ArrayList<>();

        for (int i = 0; i < mGridSource.size(); i++) {
            int rowIndex = i / GRID_COLUMNS;

            if (rowIndex >= mGridRows.size()) {
                ListRow gridRow = new ListRow(new ArrayObjectAdapter(mGridPresenter));
                mGridRows.add(gridRow);
                added.add(gridRow);
            }

            ArrayObjectAdapter adapter = (ArrayObjectAdapter) mGridRows.get(rowIndex).getAdapter();
            if (adapter.size() <= i % GRID_COLUMNS) {
                adapter.add(mGridSource.get(i));
            }
        }

        // Already on screen: add the new rows below
        if (showing && !added.isEmpty()) {
            appendRows(added);
        }
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
        mRowHeight = rowHeight;
        FrameLayout.LayoutParams rowsParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                rowHeight, Gravity.BOTTOM);
        rowsParams.bottomMargin = dp(context, 20);
        mRowBottomMargin = rowsParams.bottomMargin;
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
        mDetails = details;

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
    protected void onStripChanged(List<String> titles, List<String> icons, int selected) {
        boolean rebuild = !titles.equals(mTitles) || !icons.equals(mIcons);
        mTitles = new ArrayList<>(titles);
        mIcons = new ArrayList<>(icons);
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

        boolean circles = hasIcons();

        for (int i = 0; i < mTitles.size(); i++) {
            int index = i;
            View chip = circles ? createCircle(context, mTitles.get(i), mIcons.get(i)) : createChip(context, mTitles.get(i));
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
            params.rightMargin = dp(context, circles ? 20 : 10);
            mChips.addView(chip, params);
        }

        // One row needs no choosing
        mChipScroll.setVisibility(mTitles.size() > 1 ? View.VISIBLE : View.GONE);
        updateSelection();

        if (focused != null && mSelected < mChips.getChildCount()) {
            mChips.getChildAt(mSelected).requestFocus();
        }
    }

    private boolean hasIcons() {
        for (String icon : mIcons) {
            if (icon != null) {
                return true;
            }
        }

        return false;
    }

    private TextView createChip(Context context, String title) {
        TextView chip = new TextView(context);
        chip.setText(title);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        chip.setTextColor(ContextCompat.getColorStateList(context, R.color.hearth_tab_text));
        chip.setBackgroundResource(R.drawable.hearth_chip_background);
        chip.setSingleLine(true);
        chip.setPadding(dp(context, 16), dp(context, 7), dp(context, 16), dp(context, 7));
        return chip;
    }

    /**
     * A channel in Subscriptions: its picture in a circle, the name underneath (the concept's channel circles).
     * The ring shows focus (accent) and the channel on show (white). No picture: All.
     */
    private View createCircle(Context context, String title, String iconUrl) {
        LinearLayout circle = new LinearLayout(context);
        circle.setOrientation(LinearLayout.VERTICAL);
        circle.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        // Children follow the focused/selected state, so the ring lights up
        circle.setAddStatesFromChildren(false);

        FrameLayout ring = new FrameLayout(context);
        ring.setBackgroundResource(R.drawable.hearth_circle_ring);
        ring.setDuplicateParentStateEnabled(true);
        int pad = dp(context, 4);
        ring.setPadding(pad, pad, pad, pad);

        android.widget.ImageView image = new android.widget.ImageView(context);
        int size = dp(context, CIRCLE_DP);
        if (iconUrl != null) {
            com.bumptech.glide.Glide.with(context)
                    .load(iconUrl)
                    .apply(com.bumptech.glide.request.RequestOptions.circleCropTransform())
                    .into(image);
        } else {
            image.setBackgroundResource(R.drawable.hearth_circle_all);
            image.setImageResource(R.drawable.ic_hearth_more_grid);
            image.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        }
        ring.addView(image, new FrameLayout.LayoutParams(size, size));
        circle.addView(ring);

        TextView name = new TextView(context);
        name.setText(title);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        name.setTextColor(0xFFFFFFFF);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setMaxWidth(size + 2 * pad + dp(context, 16));
        name.setShadowLayer(dp(context, 4), 0, dp(context, 2), 0x8A000000);
        name.setPadding(0, dp(context, 4), 0, 0);
        circle.addView(name);

        return circle;
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

    /**
     * The first {@link #PREVIEW_COUNT} videos of a row, then the More tile. Follows the row as it loads.
     */
    private class PreviewAdapter extends ObjectAdapter {
        private final ObjectAdapter mSource;

        PreviewAdapter(ObjectAdapter source) {
            super(new PresenterSelector() {
                @Override
                public Presenter getPresenter(Object item) {
                    return item instanceof MoreTilePresenter.More ? mMorePresenter : mLargePresenter;
                }

                @Override
                public Presenter[] getPresenters() {
                    return new Presenter[] {mLargePresenter, mMorePresenter};
                }
            });
            mSource = source;
            mSource.registerObserver(new DataObserver() {
                @Override
                public void onChanged() {
                    notifyChanged();
                }

                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    notifyChanged();
                }

                @Override
                public void onItemRangeRemoved(int positionStart, int itemCount) {
                    notifyChanged();
                }

                @Override
                public void onItemRangeChanged(int positionStart, int itemCount) {
                    notifyChanged();
                }
            });
        }

        @Override
        public int size() {
            int size = mSource.size();
            return size > PREVIEW_COUNT ? PREVIEW_COUNT + 1 : size;
        }

        @Override
        public Object get(int position) {
            return position < PREVIEW_COUNT ? mSource.get(position) : MoreTilePresenter.More.INSTANCE;
        }
    }
}
