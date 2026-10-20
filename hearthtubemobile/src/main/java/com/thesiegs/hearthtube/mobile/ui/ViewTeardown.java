package com.thesiegs.hearthtube.mobile.ui;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.Presenter;

/**
 * Tells a presenter that its screen is gone. In {@code onDestroy} the activity is already finishing, so the presenter's
 * {@code getView()} (which only returns a live screen) is null for it: the TV's {@code getView() == this} guard never
 * passes there, and the presenter would never clean up (stop its requests, give up the sign-in). What the guard was
 * for is a newer screen of the same kind that has taken the presenter over: that one is live, so it is the only case
 * left out.
 */
public final class ViewTeardown {
    private ViewTeardown() {
    }

    public static <T> void destroyed(Presenter<T> presenter, T view) {
        if (presenter != null && isOurs(presenter.getView(), view)) {
            presenter.onViewDestroyed();
        }
    }

    /** The presenter's live screen is none, or this one: nobody newer took it over */
    static boolean isOurs(@Nullable Object liveView, Object destroyed) {
        return liveView == null || liveView == destroyed;
    }
}
