package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.ParentBlockData;
import com.liskovsoft.smartyoutubetv2.common.prefs.ParentBlockData.Item;
import com.liskovsoft.smartyoutubetv2.common.prefs.WatchLogData;
import com.liskovsoft.smartyoutubetv2.common.prefs.WatchLogData.Entry;
import com.liskovsoft.smartyoutubetv2.common.utils.ParentGate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.TimeZone;

/**
 * HearthTube: what only a parent sees in a kids profile, behind the parent PIN: the log of videos started. (Hearth and
 * Home Assistant are the way to read it without sitting at the kid's TV; this is the one that needs nothing else.)
 */
public final class ParentOptionsPresenter {
    private static final String TAG = ParentOptionsPresenter.class.getSimpleName();
    /** The newest lines a panel lists; the files have all of them */
    private static final int LIST_MAX = 100;

    private ParentOptionsPresenter() {
    }

    /** In the card menu of a kids profile; a parent profile has no use for it */
    public static void appendMenuButton(Context context, AppDialogPresenter dialog) {
        if (context == null || !ParentGate.isKidsProfile(context)) {
            return;
        }

        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.parent_options),
                option -> ParentGate.run(context, () -> show(context))));
    }

    private static void show(Context context) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);

        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.watch_log) + " (" + WatchLogData.instance(context).size() + ")",
                option -> showLog(context)));
        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.watch_log_save),
                option -> saveLog(context)));

        dialog.showDialog(context.getString(R.string.parent_options));
    }

    private static void showLog(Context context) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        List<Entry> entries = WatchLogData.instance(context).getEntries();

        if (entries.isEmpty()) {
            dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.watch_log_empty)));
        }

        for (int i = entries.size() - 1, shown = 0; i >= 0 && shown < LIST_MAX; i--, shown++) {
            Entry entry = entries.get(i);
            // A parent acts on what the log shows: block the video or its channel (locked for the kid)
            dialog.appendSingleButton(UiOptionItem.from(WatchLogData.lineOf(entry), option -> showBlock(context, entry)));
        }

        dialog.showDialog(context.getString(R.string.watch_log));
    }

    private static void showBlock(Context context, Entry entry) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        ParentBlockData blocks = ParentBlockData.instance(context);
        String title = entry.title.isEmpty() ? entry.videoId : entry.title;

        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.parent_block_video), option -> {
            blocks.addLocal(Item.VIDEO, entry.videoId, title);
            MessageHelpers.showMessage(context, R.string.parent_blocked_done);
            dialog.closeDialog();
        }));

        // Entries from before the channel was kept have none to block
        if (!entry.channelId.isEmpty()) {
            dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.parent_block_channel), option -> {
                blocks.addLocal(Item.CHANNEL, entry.channelId, entry.channel);
                MessageHelpers.showMessage(context, R.string.parent_blocked_done);
                dialog.closeDialog();
            }));
        }

        dialog.showDialog(title);
    }

    /** Two files a person can open and an AI agent can read (watch-log.txt, watch-log.json), in the app's own files folder */
    private static void saveLog(Context context) {
        List<Entry> entries = WatchLogData.instance(context).getEntries();
        File dir = new File(context.getExternalFilesDir(null), "watch-log");

        try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("Can't make " + dir);
            }

            write(new File(dir, "watch-log.txt"), WatchLogData.toText(entries, TimeZone.getDefault()));
            write(new File(dir, "watch-log.json"), WatchLogData.toJson(entries));
            MessageHelpers.showLongMessage(context, context.getString(R.string.watch_log_saved, dir.getAbsolutePath()));
        } catch (IOException e) {
            Log.e(TAG, "Saving the watch log failed: %s", e.getMessage());
            MessageHelpers.showMessage(context, R.string.watch_log_save_failed);
        }
    }

    private static void write(File file, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
