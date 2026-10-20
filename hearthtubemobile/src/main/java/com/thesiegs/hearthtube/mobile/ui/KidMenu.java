package com.thesiegs.hearthtube.mobile.ui;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.ParentGate;

/**
 * A card's or a channel's circle's long-press menu. For anyone but a kid it is the TV's menu. On a kid's phone it is what
 * the TV lets a kid do on their own, opening the channel and subscribing or unsubscribing; blocking, playlists and
 * blocked words are under "More" and the parent PIN (blocking asks the PIN again inside, as on the TV).
 */
public final class KidMenu {
    private KidMenu() {
    }

    /** Runs {@code open}, which opens the menu through a shared presenter, so that the menu is a kid's on a kid's phone */
    public static void longPress(Context context, Runnable open) {
        if (!ParentGate.isKidsProfile(context)) {
            open.run();
            return;
        }

        VideoMenuPresenter.limitNextMenuToKids(true);

        try {
            open.run();
        } finally {
            VideoMenuPresenter.limitNextMenuToKids(false);
        }
    }
}
