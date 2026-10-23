package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import android.content.Intent;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.ParentGate;
import com.liskovsoft.smartyoutubetv2.common.utils.ReportStore;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.common.utils.VideoReport;

import java.util.ArrayList;
import java.util.List;

/**
 * HearthTube: reports for the community lists. The long-press menu's "Report…" (for everyone, kids too: it changes
 * nothing on screen) is one tap: AI generated, not appropriate for some ages, sponsored, or other, for the video or its
 * channel. A report is saved at that tap; a second panel then offers to say more (what kind of AI, which age), and
 * closing it is fine. Settings › General › Reports lists them (a tap removes one, behind the PIN) and shows or shares
 * the text to send on. What is stored is {@link ReportStore}.
 */
public final class ReportPresenter {
    private static final long PANEL_CLOSE_MS = 300;

    private ReportPresenter() {
    }

    /** The long-press menu, for a video that has an id (or a channel card that has a channel id) */
    public static void appendMenuButton(Context context, AppDialogPresenter dialog, Video video) {
        if (context == null || video == null || video.isChapter || (!video.hasVideo() && video.channelId == null)) {
            return;
        }

        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.report_menu), option -> showReport(context, video)));
    }

    /** Settings › General, under Blocked words */
    public static void appendSettingsButton(Context context, AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(context.getString(R.string.reports),
                option -> ParentGate.run(context, () -> showList(context))));
    }

    private static void showReport(Context context, Video video) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        boolean[] channel = {!video.hasVideo()};
        boolean hasChannel = video.channelId != null && !video.channelId.isEmpty();

        if (video.hasVideo() && hasChannel) {
            List<OptionItem> scope = new ArrayList<>();
            scope.add(UiOptionItem.from(context.getString(R.string.report_this_video), option -> channel[0] = false, true));
            scope.add(UiOptionItem.from(context.getString(R.string.report_this_channel, video.getAuthor() != null ? video.getAuthor() : ""),
                    option -> channel[0] = true, false));
            dialog.appendRadioCategory(context.getString(R.string.report_what), scope);
        }

        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.report_ai),
                option -> send(context, video, channel[0], VideoReport.Kind.AI_GENERATED)));
        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.report_age),
                option -> send(context, video, channel[0], VideoReport.Kind.AGE)));
        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.report_sponsored),
                option -> send(context, video, channel[0], VideoReport.Kind.SPONSORED)));
        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.report_other),
                option -> send(context, video, channel[0], VideoReport.Kind.OTHER)));
        dialog.showDialog(context.getString(R.string.report_menu));
    }

    /** Saves the report now; for the kinds that can say more, the second panel opens after this one closes */
    private static void send(Context context, Video video, boolean channel, VideoReport.Kind kind) {
        VideoReport saved = ReportStore.report(context, video, channel, kind, null, 0);
        AppDialogPresenter.instance(context).closeDialog();

        if (saved == null) {
            MessageHelpers.showMessage(context, R.string.report_failed);
            return;
        }

        MessageHelpers.showMessage(context, R.string.report_saved);

        if (kind != VideoReport.Kind.OTHER) {
            Utils.postDelayed(() -> showMore(context, video, channel, kind), PANEL_CLOSE_MS);
        }
    }

    /** Optional: what kind of AI, which age, which kind of sponsoring. Picking replaces the saved report's detail */
    private static void showMore(Context context, Video video, boolean channel, VideoReport.Kind kind) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        List<OptionItem> options = new ArrayList<>();
        options.add(UiOptionItem.from(context.getString(R.string.report_not_specified),
                option -> ReportStore.report(context, video, channel, kind, null, 0), true));

        if (kind == VideoReport.Kind.AGE) {
            for (int age : VideoReport.AGES) {
                options.add(UiOptionItem.from(context.getString(R.string.report_not_under, age),
                        option -> ReportStore.report(context, video, channel, kind, null, age), false));
            }
        } else {
            String[] tags = kind == VideoReport.Kind.AI_GENERATED ? VideoReport.AI_DETAILS : VideoReport.SPONSORED_DETAILS;

            for (String tag : tags) {
                options.add(UiOptionItem.from(tagLabel(context, tag),
                        option -> ReportStore.report(context, video, channel, kind, tag, 0), false));
            }
        }

        dialog.appendRadioCategory(context.getString(R.string.report_more), options);
        dialog.showDialog(context.getString(R.string.report_saved));
    }

    private static String tagLabel(Context context, String tag) {
        switch (tag) {
            case "video":
                return context.getString(R.string.report_tag_video);
            case "music":
                return context.getString(R.string.report_tag_music);
            case "voiceover":
                return context.getString(R.string.report_tag_voiceover);
            case "images":
                return context.getString(R.string.report_tag_images);
            case "script":
                return context.getString(R.string.report_tag_script);
            case "paid-promotion":
                return context.getString(R.string.report_tag_paid);
            default:
                return context.getString(R.string.report_tag_self);
        }
    }

    /** Each report is a button that removes it; then the text to send on, and a share button */
    private static void showList(Context context) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        List<VideoReport> reports = ReportStore.getReports(context);

        if (!reports.isEmpty()) {
            dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.reports_share), option -> share(context)));
            dialog.appendLongTextCategory(context.getString(R.string.reports_text), UiOptionItem.from(ReportStore.export(context)));
        }

        for (VideoReport report : reports) {
            dialog.appendSingleButton(UiOptionItem.from(describe(context, report),
                    option -> AppDialogUtil.showConfirmationDialog(context, context.getString(R.string.report_remove), () -> {
                        ReportStore.remove(context, report);
                        dialog.closeDialog();
                        Utils.postDelayed(() -> showList(context), PANEL_CLOSE_MS);
                    })));
        }

        dialog.appendSingleButton(UiOptionItem.from(context.getString(reports.isEmpty() ? R.string.reports_empty : R.string.reports_hint)));
        dialog.showDialog(context.getString(R.string.reports));
    }

    private static String describe(Context context, VideoReport report) {
        return report.describe(context.getString(R.string.report_ai), context.getString(R.string.report_not_under),
                context.getString(R.string.report_sponsored), context.getString(R.string.report_other),
                context.getString(R.string.report_channel_word));
    }

    private static void share(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, ReportStore.export(context));
            Intent chooser = Intent.createChooser(intent, context.getString(R.string.reports_share));
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Exception e) { // no app to share to (a TV): the text is on the panel to read
            MessageHelpers.showMessage(context, R.string.reports_no_share);
        }
    }
}
