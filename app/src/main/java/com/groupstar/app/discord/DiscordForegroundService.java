package com.groupstar.app.discord;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.groupstar.app.R;
import com.groupstar.app.ui.MainActivity;

/**
 * Foreground Service — يخلي الصوت يشتغل بالخلفية وما يتقطع
 */
public class DiscordForegroundService extends Service {

    private static final String CHANNEL_ID = "gs_audio";
    private static final int NOTIF_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification());
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(
            CHANNEL_ID, "GroupStar Audio", NotificationManager.IMPORTANCE_LOW
        );
        ch.setDescription("Discord audio running");
        ch.setSound(null, null);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GroupStar")
            .setContentText("Discord running...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build();
    }
}
