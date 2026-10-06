package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

public class RemoteControlService extends Service {
    private static final String TAG = RemoteControlService.class.getSimpleName();
    private static final int NOTIFICATION_ID = RemoteControlService.class.hashCode();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: %s", Helpers.toString(intent));

        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // https://stackoverflow.com/questions/46445265/android-8-0-java-lang-illegalstateexception-not-allowed-to-start-service-inten
        // NOTE: it's impossible to hide notification on Android 9 and above
        // https://stackoverflow.com/questions/10962418/how-to-startforeground-without-showing-notification
        try {
            startForeground(NOTIFICATION_ID, createNotification());
        } catch (Exception e) {
            // NullPointerException: Attempt to read from field 'int com.android.server.am.UidRecord.curProcState' on a null object reference
            // ForegroundServiceStartNotAllowedException: Service.startForeground() not allowed due to mAllowStartForeground false (Android 14)
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: %s", Helpers.toString(intent));

        PlaybackPresenter.instance(getApplicationContext()); // init RemoteControlListener
        StreamReminderService.instance(getApplicationContext()).startStop(); // init reminder service

        return START_STICKY;
    }

    /**
     * HearthTube: Android insists on a notification while this service runs (phone casting turned on).
     * Keep it out of the way: its own channel at minimum importance (no sound, no pop-up, collapsed at the
     * bottom of notification lists) instead of the app's shared high-importance channel.
     */
    private Notification createNotification() {
        String channelId = getPackageName() + ".remote_control";

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(
                    channelId, getString(R.string.remote_control_notification_channel), NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0,
                new Intent(getApplicationContext(), ViewManager.instance(getApplicationContext()).getRootActivity()), flags);

        return new NotificationCompat.Builder(getApplicationContext(), channelId)
                .setSmallIcon(getApplicationInfo().icon)
                .setContentTitle(getString(R.string.remote_control_notification))
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .build();
    }
}
