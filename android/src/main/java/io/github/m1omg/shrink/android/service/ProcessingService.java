package io.github.m1omg.shrink.android.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import io.github.m1omg.shrink.android.JobController;
import io.github.m1omg.shrink.android.MainActivity;
import io.github.m1omg.shrink.android.R;
import io.github.m1omg.shrink.android.ShrinkApp;

/**
 * Keeps Shrink! running while a video is processed in the background and shows
 * the progress as a notification. The work itself is done by {@link JobController}.
 */
public final class ProcessingService extends Service implements JobController.Listener {

    private static final int PROGRESS_ID = 1;
    private static final int DONE_ID = 2;

    private int lastPercent = -1;

    public static void start(Context context) {
        ContextCompat.startForegroundService(context, new Intent(context, ProcessingService.class));
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, ProcessingService.class));
    }

    /** Stops the service and, if the app is in the background, tells the user the result. */
    public static void finished(Context context, boolean success) {
        stop(context);
        if (MainActivity.isInForeground()) {
            return;
        }
        Notification n = new NotificationCompat.Builder(context, ShrinkApp.CHANNEL_PROGRESS)
                .setSmallIcon(success ? R.drawable.ic_check_circle : R.drawable.ic_error)
                .setContentTitle(context.getString(success ? R.string.android_notification_done
                        : R.string.android_notification_failed))
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .build();
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        try {
            nm.notify(DONE_ID, n);
        } catch (SecurityException ignored) {
            // notifications not allowed
        }
    }

    private static PendingIntent openApp(Context context) {
        Intent intent = new Intent(context, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE
                | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ShrinkApp.get().jobs().addListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int type = Build.VERSION.SDK_INT >= 35 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                : ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
        ServiceCompat.startForeground(this, PROGRESS_ID, build(ShrinkApp.get().jobs().percent()), type);
        if (!ShrinkApp.get().jobs().isBusy()) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private Notification build(int percent) {
        return new NotificationCompat.Builder(this, ShrinkApp.CHANNEL_PROGRESS)
                .setSmallIcon(R.drawable.ic_compress)
                .setContentTitle(getString(R.string.android_notification_processing))
                .setContentText(percent > 0 ? percent + " %" : getString(R.string.progress_preparing))
                .setProgress(100, Math.max(0, percent), percent <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(openApp(this))
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    @Override
    public void jobChanged() {
        JobController jobs = ShrinkApp.get().jobs();
        if (!jobs.isBusy()) {
            stopSelf();
            return;
        }
        int p = jobs.percent();
        if (p != lastPercent) {
            lastPercent = p;
            NotificationManager nm = getSystemService(NotificationManager.class);
            try {
                nm.notify(PROGRESS_ID, build(p));
            } catch (SecurityException ignored) {
                // notifications not allowed
            }
        }
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        // Android limits how long background processing may run; stop gracefully.
        stopSelf();
    }

    @Override
    public void onDestroy() {
        ShrinkApp.get().jobs().removeListener(this);
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
