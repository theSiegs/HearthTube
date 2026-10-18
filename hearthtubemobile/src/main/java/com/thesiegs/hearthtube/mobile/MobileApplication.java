package com.thesiegs.hearthtube.mobile;

import android.app.Application;

import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.thesiegs.hearthtube.mobile.browse.MobileBrowseActivity;
import com.thesiegs.hearthtube.mobile.channel.MobileChannelActivity;
import com.thesiegs.hearthtube.mobile.channel.MobileChannelUploadsActivity;
import com.thesiegs.hearthtube.mobile.dialogs.MobileDialogActivity;
import com.thesiegs.hearthtube.mobile.playback.MobilePlaybackActivity;
import com.thesiegs.hearthtube.mobile.search.MobileSearchActivity;
import com.thesiegs.hearthtube.mobile.signin.MobileSignInActivity;

/**
 * HearthTube Mobile: tells the shared presenters which screen shows each view. Views not registered here (yet) have no
 * screen on the phone; {@link ViewManager} logs and carries on.
 */
public class MobileApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // The TV's immersive mode (system bars hidden) is its default; on a phone it hides the status bar and nags
        GeneralData.instance(this).setFullscreenModeEnabled(false);

        ViewManager viewManager = ViewManager.instance(this);
        viewManager.setRoot(MobileBrowseActivity.class);
        viewManager.register(SplashView.class, MobileSplashActivity.class); // no parent: it opens the root
        viewManager.register(BrowseView.class, MobileBrowseActivity.class); // the root: no parent
        viewManager.register(PlaybackView.class, MobilePlaybackActivity.class, MobileBrowseActivity.class);
        viewManager.register(AppDialogView.class, MobileDialogActivity.class, MobileBrowseActivity.class);
        viewManager.register(ChannelView.class, MobileChannelActivity.class, MobileBrowseActivity.class);
        viewManager.register(ChannelUploadsView.class, MobileChannelUploadsActivity.class, MobileBrowseActivity.class);
        viewManager.register(SearchView.class, MobileSearchActivity.class, MobileBrowseActivity.class);
        viewManager.register(SignInView.class, MobileSignInActivity.class, MobileBrowseActivity.class);
    }
}
