package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.SideMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class BrowseActivity extends LeanbackActivity {
    private static final String TAG = BrowseActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.fragment_main);
        } catch (NoClassDefFoundError e) {
            // Failed resolution of: Landroidx/lifecycle/ViewTreeLifecycleOwner;
            MessageHelpers.showMessage(this, e.getMessage());
        }
    }

    /**
     * HearthTube: Left at the left edge slides the menu in, like the Hearth launcher's.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0 && isAtLeftEdge()) {
            SideMenuPresenter.show(this);
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    /** Nothing further left to move to (the first video of a row, the avatar in the top bar) */
    private boolean isAtLeftEdge() {
        View focused = getCurrentFocus();

        if (focused == null) {
            return false;
        }

        View next = focused.focusSearch(View.FOCUS_LEFT);
        return next == null || next == focused || isInHiddenSidebar(next);
    }

    /** Leanback's sidebar is kept but never shown (BrowseFragment): moving into it means the left edge */
    private static boolean isInHiddenSidebar(View view) {
        for (View v = view; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v.getId() == androidx.leanback.R.id.browse_headers_dock) {
                return true;
            }
        }

        return false;
    }

    /**
     * Back on a tab other than Home goes Home first; Back on Home leaves (Works with Hearth: to Hearth).
     */
    @Override
    public void onBackPressed() {
        BrowsePresenter presenter = BrowsePresenter.instance(this);
        BrowseSection current = presenter.getCurrentSection();

        if (current != null && current.getId() != MediaGroup.TYPE_HOME && presenter.getSections().size() > 1) {
            presenter.selectSection(MediaGroup.TYPE_HOME);
            return;
        }

        super.onBackPressed();
    }

    @Override
    protected void initTheme() {
        int browseThemeResId = MainUIData.instance(this).getColorScheme().browseThemeResId;
        if (browseThemeResId > 0) {
            setTheme(browseThemeResId);
        }
    }
}
