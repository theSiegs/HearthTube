package com.thesiegs.hearthtube.mobile.search;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.thesiegs.hearthtube.mobile.R;
import com.thesiegs.hearthtube.mobile.ui.HearthActivity;
import com.thesiegs.hearthtube.mobile.ui.HearthLook;
import com.thesiegs.hearthtube.mobile.ui.KidGate;
import com.thesiegs.hearthtube.mobile.ui.SubPillStrip;
import com.thesiegs.hearthtube.mobile.ui.VideoGrid;
import com.thesiegs.hearthtube.mobile.ui.ViewTeardown;

/**
 * Search on the phone: a field with the keyboard's search key, the results below in the same grid as everywhere else.
 * The shared {@link SearchPresenter} runs the search and applies the kids filters (blocked words, no Shorts, AI
 * channels) to what comes back. Suggestions as you type, and voice, come later (a kid's phone has no microphone permission).
 */
public class MobileSearchActivity extends HearthActivity implements SearchView {
    private SearchPresenter mPresenter;
    private EditText mField;
    private VideoGrid mGrid;

    /** Opens the search screen; the presenter starts it (and reopens a search left open) */
    public static void start(Context context) {
        SearchPresenter.instance(context).startSearch(null);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        mField = new EditText(this);
        HearthLook.searchBox(mField);
        mField.setHint(R.string.search_hint);
        mField.setHintTextColor(Color.GRAY);
        mField.setTextColor(Color.WHITE);
        mField.setSingleLine(true);
        mField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        mField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        mField.setPadding(pad * 2, pad, pad * 2, pad);
        mField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search(mField.getText().toString());
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fieldParams.setMargins(pad, pad, pad, pad / 2);
        root.addView(mField, fieldParams);

        mGrid = new VideoGrid(this, new VideoGrid.Listener() {
            @Override
            public void onClick(Video video) {
                mPresenter.onVideoItemClicked(video);
            }

            @Override
            public void onLongClick(Video video) {
                KidGate.parentOnly(MobileSearchActivity.this, () -> mPresenter.onVideoItemLongClicked(video));
            }

            @Override
            public void onScrollEnd(Video last) {
                mPresenter.onScrollEnd(last);
            }
        });
        // A search with several shelves (videos, playlists, channels) has pills to pick one, as on the home screen
        root.addView(new SubPillStrip(this, mGrid, null), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(mGrid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        mPresenter = SearchPresenter.instance(this);
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    @Override
    protected void initDpi() {
    }

    @Override
    protected void initTheme() {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        ViewTeardown.destroyed(mPresenter, this);
    }

    /** Leaving by Back also ends the presenter's search, so the next one starts empty */
    @Override
    public void finish() {
        mPresenter.onFinish();
        super.finish();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mGrid.onConfigurationChanged();
    }

    private void search(String text) {
        String query = text != null ? text.trim() : "";

        if (query.isEmpty()) {
            return;
        }

        hideKeyboard();

        if (KidGate.isClosed(this)) { // nothing YouTube lists signed out is shown to a kid
            mGrid.clear();
            mGrid.showMessage(getString(R.string.gated_title));
            return;
        }

        mPresenter.onSearch(query);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        if (imm != null) {
            imm.hideSoftInputFromWindow(mField.getWindowToken(), 0);
        }
    }

    // SearchView

    @Override
    public void updateSearch(VideoGroup group) {
        mGrid.update(group);
    }

    @Override
    public void clearSearch() {
        mGrid.clear();
    }

    @Override
    public void clearSearchTags() {
    }

    @Override
    public void removeSearchTag(com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag tag) {
    }

    @Override
    public void setTagsProvider(com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider provider) {
    }

    @Override
    public void showProgressBar(boolean show) {
        mGrid.showProgress(show);
    }

    /** The presenter opened the screen with a search to run (a link, a repeat), or none: then the keyboard is ready */
    @Override
    public void startSearch(String searchText) {
        if (searchText != null && !searchText.isEmpty()) {
            mField.setText(searchText);
            mField.setSelection(searchText.length());
            search(searchText);
        } else {
            mField.requestFocus();
            Utils.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(mField, 0);
                }
            }, 300);
        }
    }

    @Override
    public String getSearchText() {
        return mField.getText().toString();
    }

    /** Voice search needs the microphone, which a kid's phone doesn't get */
    @Override
    public void startVoiceRecognition() {
        startSearch(null);
    }

    @Override
    public void finishReally() {
        super.finish();
    }
}
