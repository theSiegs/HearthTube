package com.liskovsoft.smartyoutubetv2.tv.ui.browse.video;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
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

import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.HearthSections;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.HearthChannelMenu;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
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
 * with room for the wallpaper in between. Moving along the strip switches the row. In Subscriptions the strip is the
 * channels' circles, and long press on one opens the channel's menu ({@link HearthChannelMenu}).
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
    private View mScrim;
    /** Hearth changed something, maybe its wallpaper: the shade follows how bright the new one is */
    private final android.database.ContentObserver mHearthObserver =
            new android.database.ContentObserver(new android.os.Handler(android.os.Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            updateScrim();
        }
    };
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
        mScrim.setVisibility(View.GONE);
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
        mScrim.setVisibility(View.VISIBLE);
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
    public void onStart() {
        super.onStart();

        // The wallpaper may have changed while away (another profile's, Hearth's night picture): so may its brightness
        updateScrim();

        Context context = getContext();
        for (android.net.Uri hearth : com.liskovsoft.smartyoutubetv2.common.utils.HearthProfile.ACTIVE_URIS) {
            try {
                context.getContentResolver().registerContentObserver(hearth, false, mHearthObserver);
            } catch (Exception e) {
                // No Hearth
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (getContext() != null) {
            getContext().getContentResolver().unregisterContentObserver(mHearthObserver);
        }
    }

    /**
     * In Hearth: the shade lighter on a dark wallpaper, full on a bright one (Hearth's own scrim does the same), and
     * full when the brightness isn't known
     */
    private void updateScrim() {
        Context context = getContext();

        if (mScrim == null || context == null) {
            return;
        }

        Double brightness = com.liskovsoft.smartyoutubetv2.common.utils.HearthWallpaper.getBrightness(context);
        mScrim.setAlpha(brightness != null ? (float) (0.55 + 0.45 * Math.max(0, Math.min(1, brightness))) : 1f);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mRows = super.onCreateView(inflater, container, savedInstanceState);
        Context context = inflater.getContext();

        StripLayout root = new StripLayout(context);

        // A shade over the lower part of the wallpaper, under the details and the row
        View scrim = new View(context);
        scrim.setBackgroundResource(R.drawable.hearth_details_scrim);
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(context.getResources().getDisplayMetrics().heightPixels * 0.78f), Gravity.BOTTOM));
        mScrim = scrim;
        updateScrim();

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
        mTitle = text(context, 18, true, 0xFFFFFFFF, 2);
        mMeta = text(context, 13, false, 0xE6FFFFFF, 1);
        mDescription = text(context, 13, false, 0xD1FFFFFF, 2);
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

        // The one row sits in its box below the top padding, so the focus zoom fits (leanback would align it lower and crop it)
        int padding = dp(grid.getContext(), ROW_PADDING_DP);
        if (grid.getWindowAlignment() != androidx.leanback.widget.BaseGridView.WINDOW_ALIGN_LOW_EDGE || grid.getPaddingTop() != padding || grid.getWindowAlignmentOffset() != padding) {
            grid.setWindowAlignment(androidx.leanback.widget.BaseGridView.WINDOW_ALIGN_LOW_EDGE);
            grid.setWindowAlignmentOffset(padding);
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
        // Rows come in one after another while the section loads: their chips are added after the others, so a
        // chip that has the focus keeps it
        boolean appended = rebuild && mChips != null && mChips.getChildCount() == mTitles.size()
                && startsWith(titles, mTitles) && startsWith(icons, mIcons) && hasIcons(icons) == hasIcons();
        // A row went (a channel unsubscribed from its circle): only its chip goes, so the others keep the focus
        int removed = rebuild && !appended && mChips != null && mChips.getChildCount() == mTitles.size()
                ? removedIndex(mTitles, titles, mIcons, icons) : -1;
        int shown = mTitles.size();
        mTitles = new ArrayList<>(titles);
        mIcons = new ArrayList<>(icons);
        mSelected = selected;

        if (mChips == null) {
            return;
        }

        if (appended) {
            boolean circles = hasIcons();
            for (int i = shown; i < mTitles.size(); i++) {
                addChip(mChips.getContext(), i, circles);
            }
            mChipScroll.setVisibility(mTitles.size() > 1 ? View.VISIBLE : View.GONE);
            updateSelection();
        } else if (removed != -1) {
            boolean focused = mChips.hasFocus();
            mChips.removeViewAt(removed);
            if (mTitles.size() <= 1 && focused) {
                // Only All is left, and it needs no choosing: the strip goes, the focus goes down to the videos
                focusRow();
            }
            mChipScroll.setVisibility(mTitles.size() > 1 ? View.VISIBLE : View.GONE);
            updateSelection();
        } else if (rebuild) {
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
        CharSequence meta = com.liskovsoft.smartyoutubetv2.common.utils.AiSlopList.withLabel(getContext(), video, video.getSecondTitle());
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
            addChip(context, i, circles);
        }

        // One row needs no choosing
        mChipScroll.setVisibility(mTitles.size() > 1 ? View.VISIBLE : View.GONE);
        updateSelection();

        if (focused != null && mSelected < mChips.getChildCount()) {
            mChips.getChildAt(mSelected).requestFocus();
        }
    }

    private void addChip(Context context, int index, boolean circles) {
        View chip = circles ? createCircle(context, mTitles.get(index), mIcons.get(index)) : createChip(context, mTitles.get(index));
        chip.setFocusable(true);
        chip.setClickable(true);
        // Moving along the strip switches the row; OK goes down to it. (Its place in the strip, not the one it was
        // added at: a channel's circle can go.)
        chip.setOnFocusChangeListener((v, hasFocus) -> {
            int place = mChips.indexOfChild(v);
            if (hasFocus && place != -1 && place != mSelected) {
                selectStripRow(place);
            }
        });
        chip.setOnClickListener(v -> focusRow());

        if (circles && mIcons.get(index) != null) {
            // A channel's circle: long press (or the remote's menu key) opens its menu, like a card's
            boolean longPressDisabled = GeneralData.instance(context).isOkButtonLongPressDisabled();
            chip.setOnLongClickListener(v -> !longPressDisabled && showChannelMenu(v));
            chip.setOnKeyListener((v, keyCode, event) -> {
                if (KeyHelpers.isMenuKey(keyCode) && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    showChannelMenu(v);
                }
                return false;
            });
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(context, circles ? 20 : 10);
        mChips.addView(chip, params);
    }

    /** The channel's menu (Open channel, Unsubscribe, Block, Pin): true when there's one, so the press is used up */
    private boolean showChannelMenu(View chip) {
        ListRow row = getStripRow(mChips.indexOfChild(chip));
        return HearthChannelMenu.show(getContext(), getTitledGroup(row), channel -> onChannelGone(row, channel));
    }

    /**
     * Unsubscribed or blocked: the circle goes and the focus moves to the one after it (else the one before), whose
     * videos show below; the channel's videos leave All too, as they would on the next load.
     */
    private void onChannelGone(ListRow row, Video channel) {
        if (mChips == null || row == null) {
            return;
        }

        int index = -1;
        for (int i = 0; getStripRow(i) != null; i++) {
            if (getStripRow(i) == row) {
                index = i;
            } else if (getStripRow(i).getAdapter() instanceof VideoGroupObjectAdapter) {
                removeChannelVideos((VideoGroupObjectAdapter) getStripRow(i).getAdapter(), channel);
            }
        }

        if (index == -1) {
            return;
        }

        // Focus first, while the circles are still in place: no moment without focus, no jump to the top
        View gone = index < mChips.getChildCount() ? mChips.getChildAt(index) : null;
        if (gone != null && gone.hasFocus()) {
            int next = index + 1 < mChips.getChildCount() ? index + 1 : index - 1;
            if (next >= 0) {
                mChips.getChildAt(next).requestFocus();
            }
        }

        removeStripRow(row);
        mPreviews.remove(row);
    }

    /**
     * Unsubscribed from a video's menu (not the circle's): when the video's channel has a circle, the same as from the
     * circle ({@link #onChannelGone}): the circle goes, the focus stays sensible, its videos leave All.
     */
    @Override
    protected void removeAuthor(VideoGroup group) {
        Video video = group.isEmpty() ? null : group.getVideos().get(0);
        List<String> ids = new ArrayList<>();
        List<String> titles = new ArrayList<>();

        for (int i = 0; video != null && getStripRow(i) != null; i++) {
            HearthSections.TitledGroup titled = getTitledGroup(getStripRow(i));
            boolean circle = titled != null && titled.getChannel() != null;
            ids.add(circle ? titled.getUploadsChannelId() : null);
            titles.add(circle ? titled.getChannel().getTitle() : null);
        }

        int index = video != null ? HearthSections.indexOfChannel(ids, titles, video.channelId, video.getAuthor()) : -1;

        if (index == -1) {
            super.removeAuthor(group);
            return;
        }

        ListRow row = getStripRow(index);
        boolean inRow = mRows != null && mRows.hasFocus();

        Video channel = new Video();
        channel.channelId = ids.get(index) != null ? ids.get(index) : video.channelId;
        channel.title = titles.get(index);
        onChannelGone(row, channel);

        // The card that had the focus went with its row: the focus goes to the row now on show
        if (inRow && (mRows == null || !mRows.hasFocus())) {
            focusRow();
        }
    }

    private static void removeChannelVideos(VideoGroupObjectAdapter adapter, Video channel) {
        List<Video> videos = new ArrayList<>();

        for (Video video : adapter.getAll()) {
            if (HearthSections.isSameChannel(channel.channelId, channel.title, video.channelId, video.getAuthor())) {
                videos.add(video);
            }
        }

        if (!videos.isEmpty()) {
            adapter.remove(VideoGroup.from(videos));
        }
    }

    /** Where one item went from the lists (the old ones less one), else -1 */
    private static int removedIndex(List<String> oldTitles, List<String> titles, List<String> oldIcons, List<String> icons) {
        if (titles.size() != oldTitles.size() - 1 || icons.size() != oldIcons.size() - 1 || oldTitles.size() != oldIcons.size()) {
            return -1;
        }

        for (int i = 0; i < oldTitles.size(); i++) {
            List<String> lessTitles = new ArrayList<>(oldTitles);
            List<String> lessIcons = new ArrayList<>(oldIcons);
            lessTitles.remove(i);
            lessIcons.remove(i);

            if (lessTitles.equals(titles) && lessIcons.equals(icons)) {
                return i;
            }
        }

        return -1;
    }

    private boolean hasIcons() {
        return hasIcons(mIcons);
    }

    private static boolean hasIcons(List<String> icons) {
        for (String icon : icons) {
            if (icon != null) {
                return true;
            }
        }

        return false;
    }

    private static boolean startsWith(List<String> list, List<String> start) {
        return list.size() >= start.size() && list.subList(0, start.size()).equals(start);
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
        float density = context.getResources().getDisplayMetrics().density;
        view.setShadowLayer(6 * density, 0, 2 * density, 0xCC000000);
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
