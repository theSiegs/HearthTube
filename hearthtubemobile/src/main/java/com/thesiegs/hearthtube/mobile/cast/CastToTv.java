package com.thesiegs.hearthtube.mobile.cast;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.ParentGate;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.lounge.sender.LoungeSender;
import com.thesiegs.hearthtube.mobile.R;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HearthTube: sends the video playing on the phone to a TV, at the same spot, by YouTube's "Link with TV code" (no
 * Google account, no searching the network: a TV is paired once by the code it shows, then listed here). In kids mode
 * the list (pairing and casting) is behind the parent PIN, Glint's on a phone with Glint, and only HearthTube TVs can be
 * paired, where HearthTube's kids filters and Hearth's time apply; another YouTube screen would play with none of them.
 */
public final class CastToTv {
    private static final String TAG = CastToTv.class.getSimpleName();
    private static final String PREFS = "hearthtube_cast_tvs";
    private static final String KEY_TVS = "tvs";
    private static final String KEY_SENDER_ID = "sender_id";
    /** What a HearthTube TV calls itself when it shows its code: "<device> (HearthTube)" */
    private static final String HEARTHTUBE_SCREEN = "(HearthTube)";
    private static final ExecutorService sWorker = Executors.newSingleThreadExecutor();

    /** What to cast: the video and its position now, and how to pause it here once the TV has it */
    public interface Source {
        @Nullable String videoId();

        long positionMs();

        void pauseHere();
    }

    private CastToTv() {
    }

    /** The Cast button: in kids mode a parent's PIN first */
    public static void open(Activity activity, Source source) {
        if (ParentGate.isKidsProfile(activity)) {
            ParentGate.run(activity, () -> showPicker(activity, source));
        } else {
            showPicker(activity, source);
        }
    }

    private static void showPicker(Activity activity, Source source) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(activity);

        for (LoungeSender.Screen tv : tvs(activity)) {
            dialog.appendSingleButton(UiOptionItem.from(name(activity, tv), option -> {
                dialog.closeDialog();
                cast(activity, tv, source);
            }));
        }

        dialog.appendSingleButton(UiOptionItem.from(activity.getString(R.string.cast_pair_tv), option -> {
            dialog.closeDialog();
            Utils.postDelayed(() -> askCode(activity, source), 300);
        }));

        if (!tvs(activity).isEmpty()) {
            dialog.appendSingleButton(UiOptionItem.from(activity.getString(R.string.cast_forget_tvs), option -> {
                prefs(activity).edit().remove(KEY_TVS).apply();
                dialog.closeDialog();
                MessageHelpers.showMessage(activity, R.string.cast_tvs_forgotten);
            }));
        }

        dialog.showDialog(activity.getString(R.string.cast_to_tv));
    }

    private static void askCode(Activity activity, Source source) {
        SimpleEditDialog.show(activity, activity.getString(R.string.cast_pair_tv), activity.getString(R.string.cast_code_hint), "",
                code -> {
                    pair(activity, code, source);
                    return true;
                });
    }

    private static void pair(Activity activity, String code, Source source) {
        Context app = activity.getApplicationContext();
        boolean kids = ParentGate.isKidsProfile(activity);

        sWorker.execute(() -> {
            try {
                LoungeSender.Screen tv = sender(app).pair(code);

                // A kid's phone pairs only with HearthTube TVs, where the kids filters and the time limit apply
                if (kids && (tv.name == null || !tv.name.contains(HEARTHTUBE_SCREEN))) {
                    Utils.post(() -> MessageHelpers.showLongMessage(app, R.string.cast_kids_hearthtube_only));
                    return;
                }

                addTv(app, tv);
                Utils.post(() -> cast(activity, tv, source));
            } catch (Exception e) {
                Log.e(TAG, "Pairing failed: %s", e.getMessage());
                Utils.post(() -> MessageHelpers.showLongMessage(app, R.string.cast_pair_failed));
            }
        });
    }

    private static void cast(Activity activity, LoungeSender.Screen tv, Source source) {
        Context app = activity.getApplicationContext();
        String videoId = source.videoId();
        long positionMs = source.positionMs();

        if (videoId == null) {
            return;
        }

        sWorker.execute(() -> {
            try {
                play(app, tv, videoId, positionMs);
                Utils.post(() -> {
                    source.pauseHere();
                    MessageHelpers.showMessage(app, app.getString(R.string.cast_playing_on, name(app, tv)));
                });
            } catch (Exception e) {
                Log.e(TAG, "Cast failed: %s", e.getMessage());
                Utils.post(() -> MessageHelpers.showLongMessage(app, app.getString(R.string.cast_failed, name(app, tv))));
            }
        });
    }

    /** Plays it; a refused token is renewed once (they last days), and the renewed one kept */
    private static void play(Context context, LoungeSender.Screen tv, String videoId, long positionMs) throws Exception {
        LoungeSender sender = sender(context);

        try {
            sender.play(tv, videoId, positionMs);
        } catch (LoungeSender.LoungeException e) {
            String token = sender.renewToken(tv.screenId);

            if (token == null) {
                throw e;
            }

            LoungeSender.Screen renewed = new LoungeSender.Screen(tv.screenId, token, tv.name);
            addTv(context, renewed);
            sender.play(renewed, videoId, positionMs);
        }
    }

    private static LoungeSender sender(Context context) {
        SharedPreferences prefs = prefs(context);
        String id = prefs.getString(KEY_SENDER_ID, null);

        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_SENDER_ID, id).apply();
        }

        return new LoungeSender(id, context.getString(R.string.cast_sender_name, Build.MODEL));
    }

    private static String name(Context context, LoungeSender.Screen tv) {
        return tv.name != null && !tv.name.isEmpty() ? tv.name : context.getString(R.string.cast_a_tv);
    }

    /** Paired TVs, kept as screenId|token|name lines (the newest pairing replaces one with the same screen) */
    private static List<LoungeSender.Screen> tvs(Context context) {
        List<LoungeSender.Screen> tvs = new ArrayList<>();
        String saved = prefs(context).getString(KEY_TVS, "");

        for (String line : saved.split("\n")) {
            String[] parts = line.split("\\|", 3);
            if (parts.length == 3 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                tvs.add(new LoungeSender.Screen(parts[0], parts[1], parts[2]));
            }
        }

        return tvs;
    }

    private static synchronized void addTv(Context context, LoungeSender.Screen tv) {
        StringBuilder saved = new StringBuilder();

        for (LoungeSender.Screen other : tvs(context)) {
            if (!other.screenId.equals(tv.screenId)) {
                saved.append(line(other)).append('\n');
            }
        }

        saved.append(line(tv));
        prefs(context).edit().putString(KEY_TVS, saved.toString()).apply();
    }

    private static String line(LoungeSender.Screen tv) {
        String name = tv.name != null ? tv.name.replace('\n', ' ').replace('|', '/') : "";
        return tv.screenId + "|" + tv.loungeToken + "|" + name;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
