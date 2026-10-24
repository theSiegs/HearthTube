package com.liskovsoft.smartyoutubetv2.tv.ui.search.tags;

import android.os.Bundle;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import com.liskovsoft.smartyoutubetv2.tv.presenter.MoreTilePresenter;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.CrashRestorer;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.ShortsCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.OnItemLongPressedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.search.tags.vineyard.SearchTagsFragmentBase;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

public class SearchTagsFragment extends SearchTagsFragmentBase {
    private static final String TAG = SearchTagsFragment.class.getSimpleName();
    private SearchPresenter mSearchPresenter;
    private Map<Integer, VideoGroupObjectAdapter> mSearchGroupAdapters;
    private String mSearchQuery;
    private String mNewQuery;
    private VideoCardPresenter mCardPresenter;
    private ShortsCardPresenter mShortsPresenter;
    private SearchData mSearchData;
    private boolean mIsFragmentCreated;
    private CrashRestorer mCrashRestorer;
    private static final int PREVIEW_COUNT = 10;
    private static final int GRID_COLUMNS = 4;
    private MoreTilePresenter mMorePresenter;
    /** The full row shown as a grid (More), or null */
    private ObjectAdapter mGridSource;
    private final java.util.List<Object> mSavedRows = new java.util.ArrayList<>();
    private final java.util.List<ListRow> mGridRows = new java.util.ArrayList<>();
    private final ObjectAdapter.DataObserver mGridObserver = new ObjectAdapter.DataObserver() {
        @Override
        public void onChanged() {
            fillGrid();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            fillGrid();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(null); // Real restore takes place in the presenter

        if (getContext() == null) {
            throw new IllegalStateException("Can't create SearchTagsFragment: the context is null");
        }

        mCrashRestorer = new CrashRestorer(getContext(), savedInstanceState);
        mIsFragmentCreated = true;
        mSearchPresenter = SearchPresenter.instance(getContext());
        mSearchPresenter.setView(this);
        mCardPresenter = new VideoCardPresenter();
        mShortsPresenter = new ShortsCardPresenter();
        mMorePresenter = new MoreTilePresenter(mCardPresenter);
        mSearchGroupAdapters = new HashMap<>();
        mSearchData = SearchData.instance(getContext());

        setupEventListeners();
        setKeyboardAutoShowEnabled(mSearchData.isKeyboardAutoShowEnabled());
        setKeyboardFixEnabled(mSearchData.isKeyboardFixEnabled());
        setTypingCorrectionDisabled(mSearchData.isTypingCorrectionDisabled());
    }

    private void setupEventListeners() {
        ItemViewLongPressedListener listener = new ItemViewLongPressedListener();
        mCardPresenter.setOnItemViewLongPressedListener(listener);
        mShortsPresenter.setOnItemViewLongPressedListener(listener);
        setSearchTagsLongPressListener(listener);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Called when the activity is paused
        mCrashRestorer.persistVideo(outState, mSearchPresenter.getCurrentVideo());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mSearchPresenter.onViewInitialized();

        // Restore state after crash
        mCrashRestorer.restorePlayback();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSearchPresenter.onViewDestroyed();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!mIsFragmentCreated) {
            mSearchPresenter.onViewResumed();
        }

        mIsFragmentCreated = false;
    }

    @Override
    public void updateSearch(VideoGroup group) {
        if (isComputingLayout(group)) {
            return;
        }

        freeze(true);

        update(group);

        freeze(false);
    }

    @Override
    public void clearSearch() {
        mSearchQuery = null;
        endGrid();

        for (VideoGroupObjectAdapter adapter : mSearchGroupAdapters.values()) {
            adapter.clear();
        }

        mSearchGroupAdapters.clear();

        ObjectAdapter resultsAdapter = getResultsAdapter();

        if (resultsAdapter == null) {
            return;
        }

        int size = resultsAdapter.size();
        int index = 0;

        for (int i = 0; i < size; i++) {
            Object row = resultsAdapter.get(index);
            if (row instanceof ListRow && isResultsAdapter(((ListRow) row).getAdapter())) {
                // Notify about changes (could help with search autofocus)
                detachAdapter(index);
            } else {
                index++;
            }
        }
    }

    @Override
    public void clearSearchTags() {
        clearTags();
    }

    @Override
    public void removeSearchTag(Tag tag) {
        removeTag(tag);
    }

    @Override
    public void setTagsProvider(MediaServiceSearchTagProvider provider) {
        setSearchTagsProvider(provider);
    }

    @Override
    public void startSearch(String searchText) {
        startSearch(searchText, false);
    }

    @Override
    public String getSearchText() {
        return getSearchBarText();
    }

    @Override
    public void startVoiceRecognition() {
        startSearch(null, true);
    }

    @Override
    public boolean onQueryTextChange(String newQuery) {
        loadSearchTags(newQuery);

        // Commit on voice input.
        // Note, that voice detection is far from ideal and may results duplicate search loading.
        if (isVoiceQuery(newQuery)) {
            loadSearchResult(newQuery);
        }

        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        loadSearchResult(query);
        return true;
    }

    @Override
    protected void focusOnResults() {
        if (mSearchData.isFocusOnResultsEnabled() && !TextUtils.isEmpty(mNewQuery)) {
            super.focusOnResults();
            if (getRowsSupportFragment() != null) {
                // Move selection to the videos (second row)
                getRowsSupportFragment().setSelectedPosition(findResultsIndex());
            }
        }
    }

    /**
     * Usually, but not always: first row - tags, second row is video results
     */
    private int findResultsIndex() {
        ObjectAdapter rows = getResultsAdapter();

        int index = -1;

        for (int i = 0; i < rows.size(); i++) {
            Object row = rows.get(i);
            if (row instanceof ListRow && isResultsAdapter(((ListRow) row).getAdapter())) {
                index = i;
                break;
            }
        }

        return index;
    }

    @Override
    protected void onItemViewClicked(Object item) {
        if (item instanceof Video) {
            mSearchPresenter.onVideoItemClicked((Video) item);
        } else if (item instanceof Tag) {
            startSearch(((Tag) item).tag, false);
        } else if (item instanceof MoreTilePresenter.More) {
            showGrid(getClickedRow());
        }
    }

    private boolean isResultsAdapter(ObjectAdapter adapter) {
        return adapter instanceof VideoGroupObjectAdapter || adapter instanceof PreviewAdapter;
    }

    /** Back in the grid goes back to the rows. True: handled. */
    public boolean onBack() {
        if (mGridSource == null) {
            return false;
        }

        endGrid();
        return true;
    }

    private void showGrid(androidx.leanback.widget.Row row) {
        ObjectAdapter rows = getResultsAdapter();

        if (!(row instanceof ListRow) || !(((ListRow) row).getAdapter() instanceof PreviewAdapter)
                || !(rows instanceof ArrayObjectAdapter)) {
            return;
        }

        ArrayObjectAdapter all = (ArrayObjectAdapter) rows;
        mGridSource = ((PreviewAdapter) ((ListRow) row).getAdapter()).mSource;
        mSavedRows.clear();
        mSavedRows.addAll(all.unmodifiableList());
        all.clear();
        mGridRows.clear();
        fillGrid();
        mGridSource.registerObserver(mGridObserver);

        if (getRowsSupportFragment() != null) {
            getRowsSupportFragment().setSelectedPosition(0);
        }
    }

    private void endGrid() {
        if (mGridSource == null) {
            return;
        }

        mGridSource.unregisterObserver(mGridObserver);
        mGridSource = null;
        mGridRows.clear();

        ObjectAdapter rows = getResultsAdapter();

        if (rows instanceof ArrayObjectAdapter) {
            ((ArrayObjectAdapter) rows).clear();
            ((ArrayObjectAdapter) rows).addAll(0, mSavedRows);
        }

        mSavedRows.clear();
    }

    /** Rows of four from the full row; more rows come as the search loads more videos */
    private void fillGrid() {
        ObjectAdapter rows = getResultsAdapter();

        if (mGridSource == null || !(rows instanceof ArrayObjectAdapter)) {
            return;
        }

        for (int i = 0; i < mGridSource.size(); i++) {
            int rowIndex = i / GRID_COLUMNS;

            if (rowIndex >= mGridRows.size()) {
                ListRow gridRow = new ListRow(new ArrayObjectAdapter(mCardPresenter));
                mGridRows.add(gridRow);
                ((ArrayObjectAdapter) rows).add(gridRow);
            }

            ArrayObjectAdapter adapter = (ArrayObjectAdapter) mGridRows.get(rowIndex).getAdapter();
            if (adapter.size() <= i % GRID_COLUMNS) {
                adapter.add(mGridSource.get(i));
            }
        }
    }

    /** The first {@link #PREVIEW_COUNT} videos of a search row, then the More tile. Follows the row as it loads. */
    private final class PreviewAdapter extends ObjectAdapter {
        private final VideoGroupObjectAdapter mSource;

        PreviewAdapter(VideoGroupObjectAdapter source) {
            super(new PresenterSelector() {
                @Override
                public Presenter getPresenter(Object item) {
                    return item instanceof MoreTilePresenter.More ? mMorePresenter : mCardPresenter;
                }

                @Override
                public Presenter[] getPresenters() {
                    return new Presenter[] {mCardPresenter, mMorePresenter};
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
            // A full row, or one with more pages behind it (search loads them as it scrolls): More shows it as a grid
            return size > 0 && (size >= PREVIEW_COUNT || hasMorePages()) ? Math.min(size, PREVIEW_COUNT) + 1 : size;
        }

        @Override
        public Object get(int position) {
            return position < Math.min(mSource.size(), PREVIEW_COUNT) ? mSource.get(position) : MoreTilePresenter.More.INSTANCE;
        }

        private boolean hasMorePages() {
            java.util.List<VideoGroup> groups = mSource.getAllGroups();
            return groups != null && !groups.isEmpty() && groups.get(groups.size() - 1).getNextPageKey() != null;
        }
    }

    @Override
    protected void onItemViewSelected(Object item) {
        if (item instanceof Video) {
            mSearchPresenter.onVideoItemSelected((Video) item);
            checkScrollEnd((Video) item);
        }
    }

    /**
     * Fix: voice search: autofocus on results (mNewQuery is null)
     */
    @Override
    protected void submitQuery(String query) {
        mNewQuery = query;
        super.submitQuery(query);
    }

    @Override
    public void onSearchSettingsClicked() {
        mSearchPresenter.onSearchSettingsClicked();
    }

    private void checkScrollEnd(Video item) {
        VideoGroupObjectAdapter resultsAdapter = getItemResultsAdapter(item);

        if (resultsAdapter == null) {
            return;
        }

        int size = resultsAdapter.size();
        int index = resultsAdapter.indexOf(item);

        if (index > (size - ViewUtil.ROW_SCROLL_CONTINUE_NUM)) {
            mSearchPresenter.onScrollEnd((Video) resultsAdapter.get(size - 1));
        }
    }

    private VideoGroupObjectAdapter getItemResultsAdapter(Video item) {
        for (VideoGroupObjectAdapter adapter : mSearchGroupAdapters.values()) {
            int index = adapter.indexOf(item);

            if (index != -1) {
                return adapter;
            }
        }
        
        return null;
    }

    private void startSearch(String searchText, boolean enableRecognition) {
        mNewQuery = null;

        if (searchText != null) {
            setSearchQuery(searchText, true);
        } else {
            selectAllText();
            loadSearchTags("");
            // Show suggested videos on empty search
            loadSearchResult("");

            if (enableRecognition && isRecognitionAvailable()) {
                startRecognition();
            } else {
                focusOnSearchField();
            }
        }

        if (getRowsSupportFragment() != null) {
            // Move selection to the top
            getRowsSupportFragment().setSelectedPosition(0);
        }
    }

    private boolean isRecognitionAvailable() {
        try {
            return SpeechRecognizer.isRecognitionAvailable(getContext());
        } catch (NullPointerException e) {
            // Attempt to invoke virtual method 'android.content.pm.PackageManager android.content.Context.getPackageManager()' on a null object reference
            return false;
        }
    }

    private void update(VideoGroup group) {
        int action = group.getAction();

        if (action == VideoGroup.ACTION_REPLACE) {
            clearSearch();
        } else if (action == VideoGroup.ACTION_SYNC) {
            VideoGroupObjectAdapter adapter = mSearchGroupAdapters.get(group.getId());
            if (adapter != null) {
                adapter.sync(group);
            }
            return;
        }

        if (group.isEmpty()) {
            return;
        }

        HeaderItem rowHeader = new HeaderItem(group.getTitle());
        int mediaGroupId = group.getId(); // Create unique int from category.

        VideoGroupObjectAdapter existingAdapter = mSearchGroupAdapters.get(mediaGroupId);

        if (existingAdapter == null) {
            VideoGroupObjectAdapter mediaGroupAdapter = new VideoGroupObjectAdapter(group, group.isShorts() ? mShortsPresenter : mCardPresenter);

            mSearchGroupAdapters.put(mediaGroupId, mediaGroupAdapter);
            
            // HearthTube: the first videos of a row and a More tile (as on Home); Shorts rows stay as they are
            attachAdapter(getResultsAdapter().size(), rowHeader,
                    group.isShorts() ? mediaGroupAdapter : new PreviewAdapter(mediaGroupAdapter));
        } else {
            Log.d(TAG, "Continue row %s %s", group.getTitle(), System.currentTimeMillis());

            existingAdapter.add(group); // continue row
        }
    }

    private void loadSearchResult(String searchQuery) {
        // Don't show suggested videos (empty query).
        // They are inaccurate and usually have problems with layout.
        if (!TextUtils.isEmpty(searchQuery) && !searchQuery.equals(mSearchQuery)) {
            mSearchQuery = searchQuery;
            mSearchPresenter.onSearch(searchQuery);
        }
    }

    /**
     * Check that previous query is null
     */
    private boolean isVoiceQuery(String newQuery) {
        if (TextUtils.isEmpty(newQuery)) {
            mNewQuery = null;
            return false;
        }

        boolean isVoice = mNewQuery == null && newQuery.length() > 1;

        mNewQuery = newQuery;

        return isVoice;
    }

    private int findPositionById(int id) {
        if (getResultsAdapter() != null) {
            VideoGroupObjectAdapter needed = mSearchGroupAdapters.get(id);
            for (int i = 0; i < getResultsAdapter().size(); i++) {
                Object row = getResultsAdapter().get(i);

                if (row instanceof ListRow) {
                    // Could be one of those: TagAdapter, VideoGroupObjectAdapter
                    ObjectAdapter adapter = ((ListRow) row).getAdapter();
                    if (adapter instanceof PreviewAdapter) {
                        adapter = ((PreviewAdapter) adapter).mSource;
                    }
                    if (adapter == needed) {
                        return i;
                    }
                }
            }
        }

        return -1;
    }

    private boolean isComputingLayout(VideoGroup group) {
        int action = group.getAction();

        // Attempt to fix: IllegalStateException: Cannot call this method while RecyclerView is computing a layout or scrolling
        if (action == VideoGroup.ACTION_SYNC && getRowsSupportFragment() != null && getRowsSupportFragment().getVerticalGridView() != null) {
            int position = findPositionById(group.getId());
            if (position != -1) {
                RecyclerView.ViewHolder viewHolder = getRowsSupportFragment().getVerticalGridView().findViewHolderForAdapterPosition(position);
                if (viewHolder != null) {
                    Object nestedRecyclerView = Helpers.getField(viewHolder, "mNestedRecyclerView");
                    if (nestedRecyclerView instanceof WeakReference) {
                        Object recyclerView = ((WeakReference<?>) nestedRecyclerView).get();
                        return recyclerView instanceof RecyclerView && ((RecyclerView) recyclerView).isComputingLayout();
                    }
                }
            }
        }

        return false;
    }

    public void onFinish() {
        mSearchPresenter.onFinish();
    }

    @Override
    public void finishReally() {
        LeanbackActivity activity = (LeanbackActivity) getActivity();

        if (activity != null) {
            activity.finishReally();
        }
    }

    private final class ItemViewLongPressedListener implements OnItemLongPressedListener {
        @Override
        public void onItemLongPressed(Presenter.ViewHolder itemViewHolder, Object item) {
            if (item instanceof Video) {
                mSearchPresenter.onVideoItemLongClicked((Video) item);
            } else if (item instanceof Tag) {
                mSearchPresenter.onTagLongClicked((Tag) item);
                //startSearch(((Tag) item).tag, false);
            }
        }
    }
}
