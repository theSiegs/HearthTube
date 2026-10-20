package com.thesiegs.hearthtube.mobile.browse;

/**
 * When the bars (the top pills and the bottom bar) are out of the way: they slide off as the list goes down, come back
 * as it goes up, and are always there at the top of the list. A tablet has room for them and never hides them. Plain
 * Java, so the state machine is tested apart from Android.
 */
final class ScrollHide {
    private final boolean mEnabled;
    private final int mThreshold;
    private boolean mVisible = true;

    /** {@code threshold}: a scroll smaller than this (pixels) doesn't count, so a finger's jitter doesn't flicker the bars */
    ScrollHide(boolean enabled, int threshold) {
        mEnabled = enabled;
        mThreshold = threshold;
    }

    boolean isVisible() {
        return mVisible;
    }

    /** The list scrolled by dy (positive: down the list). True when the bars have to move. */
    boolean onScrolled(int dy, boolean atTop) {
        if (!mEnabled) {
            return false;
        }

        if (atTop || dy < -mThreshold) {
            return set(true);
        }

        if (dy > mThreshold) {
            return set(false);
        }

        return false;
    }

    /** Another destination or a tap on the one shown: the bars are back */
    boolean show() {
        return set(true);
    }

    private boolean set(boolean visible) {
        if (mVisible == visible) {
            return false;
        }

        mVisible = visible;
        return true;
    }
}
