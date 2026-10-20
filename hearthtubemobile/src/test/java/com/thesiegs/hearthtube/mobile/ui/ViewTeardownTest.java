package com.thesiegs.hearthtube.mobile.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.Presenter;

import org.junit.Test;

public class ViewTeardownTest {
    /** A presenter that only counts, with the live view a test gives it */
    private static final class CountingPresenter implements Presenter<Object> {
        Object live;
        int destroyed;

        @Override public void setView(Object view) { }
        @Override public Object getView() { return live; }
        @Override public void setContext(Context context) { }
        @Override public Context getContext() { return null; }
        @Override public void onViewInitialized() { }
        @Override public void onViewDestroyed() { destroyed++; }
        @Override public void onViewPaused() { }
        @Override public void onViewResumed() { }
        @Override public void onFinish() { }
    }

    @Test
    public void aScreenThatIsFinishingTellsThePresenterItIsGone() {
        // In onDestroy the presenter's live view is none (the screen is finishing): the old guard never passed here
        CountingPresenter presenter = new CountingPresenter();
        ViewTeardown.destroyed(presenter, new Object());
        assertEquals(1, presenter.destroyed);
    }

    @Test
    public void theScreenThePresenterStillHoldsTellsItOnce() {
        CountingPresenter presenter = new CountingPresenter();
        Object screen = new Object();
        presenter.live = screen;
        ViewTeardown.destroyed(presenter, screen);
        assertEquals(1, presenter.destroyed);
    }

    @Test
    public void anOldScreenDestroyedAfterANewerOneTookOverLeavesThePresenterAlone() {
        CountingPresenter presenter = new CountingPresenter();
        presenter.live = new Object();
        ViewTeardown.destroyed(presenter, new Object());
        assertEquals(0, presenter.destroyed);
    }

    @Test
    public void isOursOnlyRefusesAnotherLiveScreen() {
        Object a = new Object();
        assertTrue(ViewTeardown.isOurs(null, a));
        assertTrue(ViewTeardown.isOurs(a, a));
        assertFalse(ViewTeardown.isOurs(new Object(), a));
    }
}
