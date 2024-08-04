package com.example.timenotification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.graphics.drawable.Icon;
import android.widget.RemoteViews;


public class TimeNotificationService extends Service {

    private static final String CHANNEL_ID = "TimeNotificationChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "TimeNotificationService";

    private Handler handler;
    private Runnable runnable;
    private Notification.Builder notificationBuilder;
    private NotificationManager notificationManager;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: Service created");
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();

        handler = new Handler(Looper.getMainLooper());

        notificationBuilder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Battery Voltage")
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        notificationBuilder.setContentIntent(pendingIntent);

        runnable = new Runnable() {
            @Override
            public void run() {
                updateNotification();
                handler.postDelayed(this, 1000); // Update every second
            }
        };
        handler.post(runnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: Service started");
        startForeground(NOTIFICATION_ID, createNotification("0.0 V"));
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Service destroyed");
        handler.removeCallbacks(runnable);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "createNotificationChannel: Creating notification channel");
            CharSequence name = "Battery Voltage Notification Channel";
            String description = "Channel for displaying battery voltage notifications";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            notificationManager.createNotificationChannel(channel);
            Log.d(TAG, "createNotificationChannel: Notification channel created");
        }
    }

    private Notification createNotification(String voltageText) {
        Log.d(TAG, "createNotification: Creating notification with voltage: " + voltageText);


        RemoteViews notificationLayout = new RemoteViews(getPackageName(), R.layout.notification);
        notificationLayout.setTextViewText(R.id.notification_text, voltageText + " V");

        String shortVoltageText = voltageText.length() > 3 ? voltageText.substring(0, 3) : voltageText;
        Bitmap voltageBitmap = BitmapUtils.textToBitmap(shortVoltageText);
        Icon icon = Icon.createWithBitmap(voltageBitmap);
        notificationBuilder.setSmallIcon(icon);

        notificationBuilder.setCustomContentView(notificationLayout);

        return notificationBuilder.build();
    }

    private void updateNotification() {
        String voltageText = getCurrentBatteryVoltage();  // + " V"
        Log.d(TAG, "updateNotification: Updating notification with voltage: " + voltageText);
        notificationManager.notify(NOTIFICATION_ID, createNotification(voltageText));
    }

    private String getCurrentBatteryVoltage() {
        Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int voltage = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) : -1;
        Log.d(TAG, "getCurrentBatteryVoltage: Current battery voltage is " + voltage);
        return voltage != -1 ? String.valueOf(voltage / 1000.0) : "Unknown";
    }
}