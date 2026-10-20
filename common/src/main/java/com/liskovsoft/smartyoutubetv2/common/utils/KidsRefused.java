package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * HearthTube: videos refused to a kid. What's left out should just not be there, without reminders that the profile is
 * restricted: a refused video's card goes from the rows it's in (and stays out for the rest of the session), and the
 * kid is told why only when they opened it themselves by a link or a cast, where silence would look broken.
 * <p>
 * Also YouTube's own say: a kid signed in to their supervised account gets YouTube's refusal (their content level,
 * channels a parent blocked) from the signed-in client, and the player must not then fetch the video signed out.
 * A kid who isn't signed in plays nothing, on the TV as on the phone: only a signed-in answer carries the supervision.
 */
public final class KidsRefused {
    private static final Set<String> sRefused = Collections.synchronizedSet(new HashSet<>());

    private KidsRefused() {
    }

    /** The kid opened it by a link from outside or a cast, not from HearthTube's rows */
    public static boolean isDirect(@Nullable Video video) {
        return video != null && (video.fromLink || video.isRemote);
    }

    /** Refused to a kid earlier this session: leave it out of rows */
    public static boolean isHidden(@Nullable Video video) {
        return video != null && video.videoId != null && sRefused.contains(video.videoId);
    }

    /**
     * YouTube refused the video to the kid's signed-in account: the player only got it signed out (another client), or
     * not at all (18+, private, removed). A kids profile signed in to an account plays only what that account may, and
     * a video that can't play goes quietly too, rather than with YouTube's reason on the screen.
     */
    public static boolean isNotAllowed(Context context, @Nullable MediaItemFormatInfo formatInfo) {
        return formatInfo != null && ParentGate.isKidsProfile(context)
                && isNotAllowed(KidsMode.isMobileBuild(context), isSignedIn(), formatInfo.isAuth(), formatInfo.isUnplayable());
    }

    /** Somebody is signed in to YouTube in this app */
    public static boolean isSignedIn() {
        return MediaServiceManager.instance().getSelectedAccount() != null;
    }

    /**
     * The rule for a kids profile, apart from Android. A kid who isn't signed in plays nothing, on the TV as on the
     * phone: only the signed-in client carries the supervision, and the answer's own "signed in" flag can't be trusted
     * then (the TV client says yes without an account).
     *
     * @param mobileBuild the phone and tablet build (no difference in the rule now, kept for the callers' sake)
     * @param signedIn an account is selected
     * @param answerSignedIn the answer came from a signed-in client
     * @param unplayable YouTube said the video can't play
     */
    static boolean isNotAllowed(boolean mobileBuild, boolean signedIn, boolean answerSignedIn, boolean unplayable) {
        if (!signedIn) {
            return true;
        }

        return !answerSignedIn || unplayable;
    }

    /** What to do with a video a kid's signed-in account didn't get from the signed-in client */
    public enum Recovery {
        /** YouTube refused it to the account: final */
        REFUSE,
        /** The library may have been left on a signed-out client by earlier errors: reset it and ask again, once */
        RESET_AND_RETRY
    }

    /**
     * A refusal because the answer wasn't signed in (not because YouTube said no) may only mean the library is stuck on
     * a signed-out client after some errors. Ask again from the start once; a second refusal for the same video is final.
     *
     * @param unplayable YouTube itself said the video can't play (an answer from the signed-in client, or none)
     * @param alreadyRetried this video already had its one retry
     */
    public static Recovery recoveryFor(boolean unplayable, boolean alreadyRetried) {
        return unplayable || alreadyRetried ? Recovery.REFUSE : Recovery.RESET_AND_RETRY;
    }

    /** Another profile is watching: what was refused to the last one isn't refused to this one */
    public static void clear() {
        sRefused.clear();
    }

    /** The video was refused to a kid: its card leaves the open screens' rows, and it stays out of new ones */
    public static void hide(Context context, @Nullable Video video) {
        if (context == null || video == null || video.videoId == null) {
            return;
        }

        sRefused.add(video.videoId);
        BrowsePresenter.instance(context).hideVideo(video);
        SearchPresenter.instance(context).hideVideo(video);
        ChannelPresenter.instance(context).hideVideo(video);
        ChannelUploadsPresenter.instance(context).hideVideo(video);
    }
}
