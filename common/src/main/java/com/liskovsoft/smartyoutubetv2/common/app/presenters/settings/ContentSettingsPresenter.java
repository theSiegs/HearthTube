package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.KidsShorts;
import com.liskovsoft.smartyoutubetv2.common.utils.ParentGate;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import java.util.ArrayList;
import java.util.List;

/**
 * Content and safety: what shows up and what is kept out, in one place for the TV app and the phone app: blocked words,
 * the AI-channel label, Shorts, what to hide, and the two services that change what a video shows (SponsorBlock skips,
 * DeArrow's titles and thumbnails). Each opens its own page as before.
 */
public class ContentSettingsPresenter extends BasePresenter<Void> {
    private final MediaServiceData mMediaServiceData;
    private boolean mRestartApp;
    private Runnable mOnRestartNeeded;

    private ContentSettingsPresenter(Context context) {
        super(context);
        mMediaServiceData = MediaServiceData.instance();
    }

    public static ContentSettingsPresenter instance(Context context) {
        return new ContentSettingsPresenter(context);
    }

    /** @param onRestartNeeded what to do when a change needs the screens to open again; null: ask for a restart (the TV) */
    public void show(Runnable onRestartNeeded) {
        mOnRestartNeeded = onRestartNeeded;
        AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());

        BlockedWordsPresenter.appendSettingsButton(getContext(), dialog);
        BlockedWordsPresenter.appendAiLabelsSwitch(getContext(), dialog);
        appendShowShorts(dialog);
        appendHideContent(dialog);
        dialog.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.content_block_provider),
                option -> SponsorBlockSettingsPresenter.instance(getContext()).show()));
        dialog.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.dearrow_provider),
                option -> DeArrowSettingsPresenter.instance(getContext()).show()));

        dialog.showDialog(getContext().getString(R.string.settings_content_and_safety), this::onDialogClosed);
    }

    /** Shorts take effect as the screens open again (a phone) or after a restart (the TV) */
    private void onDialogClosed() {
        if (!mRestartApp) {
            return;
        }

        mRestartApp = false;

        if (mOnRestartNeeded != null) {
            mOnRestartNeeded.run();
        } else {
            MessageHelpers.showLongMessage(getContext(), R.string.msg_restart_app);
        }
    }

    /** Off: no Shorts anywhere. Kids profiles never get them, so it isn't offered there. */
    private void appendShowShorts(AppDialogPresenter dialog) {
        if (ParentGate.isKidsProfile(getContext())) {
            return;
        }

        dialog.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.show_shorts),
                getContext().getString(R.string.show_shorts_hint), option -> {
                    KidsShorts.setShowShortsEnabled(getContext(), option.isSelected());
                    mRestartApp = true;
                }, KidsShorts.isShowShortsEnabled(getContext())));
    }

    private void appendHideContent(AppDialogPresenter dialog) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_mixes),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_MIXES, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_MIXES)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_watched_from_watch_later),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_WATCHED_WATCH_LATER, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_WATCHED_WATCH_LATER)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_watched_from_home),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_WATCHED_HOME, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_WATCHED_HOME)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_watched_from_subscriptions),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_WATCHED_SUBSCRIPTIONS, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_WATCHED_SUBSCRIPTIONS)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_streams),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_STREAMS_SUBSCRIPTIONS, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_STREAMS_SUBSCRIPTIONS)));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_upcoming),
                option -> mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_UPCOMING_SUBSCRIPTIONS, option.isSelected()),
                mMediaServiceData.isContentHidden(MediaServiceData.CONTENT_UPCOMING_SUBSCRIPTIONS)));

        dialog.appendCheckedCategory(getContext().getString(R.string.hide_unwanted_content), options);
    }
}
