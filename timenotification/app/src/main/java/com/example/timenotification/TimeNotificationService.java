package com.example.timenotification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
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
import android.content.SharedPreferences;
import androidx.core.app.NotificationCompat;
import android.app.UiModeManager;

public class TimeNotificationService extends Service {
    private BatteryVoltageManager batteryVoltageManager;
    private static final String CHANNEL_ID = "TimeNotificationChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "TimeNotificationService";

    private Handler handler;
    private Runnable runnable;
    private Notification.Builder notificationBuilder;
    private NotificationManager notificationManager;


    private static final String PREFS_NAME = "MyPrefs";
    private static final String RADIO_CHOSEN_BLACK_KEY = "RadioChosenBlack";
    private int TEXT_COLOR;



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

        batteryVoltageManager = new BatteryVoltageManager(this, 5000);

        // Load the saved text color preference
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean radioChosenBlack = prefs.getBoolean(RADIO_CHOSEN_BLACK_KEY, true);
        TEXT_COLOR = radioChosenBlack ? Color.BLACK : Color.WHITE;

        //handler = new Handler(Looper.getMainLooper());

        notificationBuilder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Battery Voltage")
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        notificationBuilder.setContentIntent(pendingIntent);

        // Register receiver for battery voltage updates
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, filter);


        /*
        runnable = new Runnable() {
            @Override
            public void run() {
                updateNotification();
                handler.postDelayed(this, 5000); // Update every X seconds
            }
        };
        handler.post(runnable);
        */
    }

    private final android.content.BroadcastReceiver batteryReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            if (voltage != -1) {
                batteryVoltageManager.checkAndRecordVoltages();
                String voltageText = String.valueOf(voltage / 1000.0);
                Log.d(TAG, "BatteryReceiver: Voltage changed -> " + voltageText);
                notificationManager.notify(NOTIFICATION_ID, createNotification(voltageText));
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: Service started");


        // Read latest sticky battery intent immediately (returns null only in rare cases)
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, filter);
        String initialVoltageText = "0.0 V"; // default fallback (no unit)

        if (batteryStatus != null) {
            int voltageMv = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            if (voltageMv != -1) {
                // Same formatting you already use elsewhere: volts as double
                initialVoltageText = String.valueOf(voltageMv / 1000.0);
            }
        }

        startForeground(NOTIFICATION_ID, createNotification(initialVoltageText));
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Service destroyed");
        //handler.removeCallbacks(runnable);
        try {
            unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Receiver already unregistered");
        }
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

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isRadioChosenBlack = prefs.getBoolean(RADIO_CHOSEN_BLACK_KEY, true);
        int TEXT_COLOR = isRadioChosenBlack ? Color.BLACK : Color.WHITE;

        // Get min and max voltages from shared preferences
        int minVoltage = batteryVoltageManager.getMinVoltage();
        int maxVoltage = batteryVoltageManager.getMaxVoltage();

        String minText = "---";
        String maxText = "---";

        if (minVoltage != -1) {
            minText = String.valueOf(minVoltage / 1000.0) + " V";
        }

        if (maxVoltage != -1) {
            maxText = String.valueOf(maxVoltage / 1000.0) + " V";
        }

        // Build the expanded notification text with min/max values
        String expandedText = null;

        if (minText.equals("---") || maxText.equals("---")) {
            expandedText = "\uD83D\uDD0B Battery Voltage: " + voltageText + " V";
        } else {
            expandedText = "\uD83D\uDD0B Battery Voltage: " + voltageText + " V\n" +
                    "Min (0%): " + minText + " | Max (100%): " + maxText;
        }




        UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        boolean isDarkMode = (uiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_YES
                || uiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_AUTO);


        RemoteViews notificationExpandedLayout = new RemoteViews(getPackageName(), R.layout.notification_expanded);
        notificationExpandedLayout.setTextViewText(R.id.notification_text_expanded, expandedText);
        if (isDarkMode) notificationExpandedLayout.setTextColor(R.id.notification_text_expanded, Color.WHITE);

        RemoteViews notificationLayout = new RemoteViews(getPackageName(), R.layout.notification);
        notificationLayout.setTextViewText(R.id.notification_text, "\uD83D\uDD0B Battery Voltage: "+ voltageText + " V ");
        if (isDarkMode) notificationLayout.setTextColor(R.id.notification_text, Color.WHITE);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String shortVoltageText = voltageText.length() > 4 ? voltageText.substring(0, 4) : voltageText;
        Bitmap voltageBitmap = BitmapUtils.textToBitmap(shortVoltageText, TEXT_COLOR);
        Icon icon = Icon.createWithBitmap(voltageBitmap);

        return new Notification.Builder(this, CHANNEL_ID)
                .setCustomContentView(notificationLayout)
                .setSmallIcon(icon)
                .setContentIntent(pendingIntent)
                .setPriority(Notification.PRIORITY_LOW)
                .setCustomBigContentView(notificationExpandedLayout)
                .build();


        /*
        RemoteViews notificationLayout = new RemoteViews(getPackageName(), R.layout.notification);
        notificationLayout.setTextViewText(R.id.notification_text, voltageText + " V");

        String shortVoltageText = voltageText.length() > 3 ? voltageText.substring(0, 3) : voltageText;
        Bitmap voltageBitmap = BitmapUtils.textToBitmap(shortVoltageText);
        Icon icon = Icon.createWithBitmap(voltageBitmap);
        notificationBuilder.setSmallIcon(icon);

        notificationBuilder.setCustomContentView(notificationLayout);

        return notificationBuilder.build();

         */
    }

    private void updateNotification() {
        String voltageText = getCurrentBatteryVoltage();  // + " V"
        batteryVoltageManager.checkAndRecordVoltages();
        Log.d(TAG, "updateNotification: Updating notification with voltage: " + voltageText);
        notificationManager.notify(NOTIFICATION_ID, createNotification(voltageText));
    }

    private String getCurrentBatteryVoltage() {
        String voltageText = batteryVoltageManager.getCurrentBatteryVoltageAsString();
        Log.d(TAG, "getCurrentBatteryVoltage: Current battery voltage is " + voltageText);
        return voltageText;
    }
}