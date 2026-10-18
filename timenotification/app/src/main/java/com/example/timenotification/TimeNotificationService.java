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
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import android.content.res.Configuration;

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

    private static final String ICON_SCALE_KEY = "IconScale";

    private static final String FLOATING_OVERLAY_KEY = "FloatingOverlay";

    private WindowManager windowManager;
    private TextView overlayText;
    private boolean overlayAdded;

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
        boolean radioChosenBlack = prefs.getBoolean(RADIO_CHOSEN_BLACK_KEY, false);
        TEXT_COLOR = radioChosenBlack ? Color.BLACK : Color.WHITE;

        //handler = new Handler(Looper.getMainLooper());

        notificationBuilder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
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
                updateFloatingOverlay(voltageText);
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: Service started");

        //handles immediate enabling and disabling overlay without waiting for another battery-voltage broadcast
        if (intent != null && "UPDATE_OVERLAY".equals(intent.getAction())) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

            if (prefs.getBoolean(FLOATING_OVERLAY_KEY, false)) {
                IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = registerReceiver(null, filter);

                if (batteryStatus != null) {
                    int voltage = batteryStatus.getIntExtra(
                            BatteryManager.EXTRA_VOLTAGE, -1);

                    if (voltage > 0) {
                        updateFloatingOverlay(String.valueOf(voltage / 1000.0));
                    }
                }
            } else {
                removeFloatingOverlay();
            }

            return START_STICKY;
        }


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
        updateFloatingOverlay(initialVoltageText);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        removeFloatingOverlay();
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
            CharSequence name = getString(R.string.notification_channel_name);
            String description = getString(R.string.notification_channel_description);
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
        boolean isRadioChosenBlack = prefs.getBoolean(RADIO_CHOSEN_BLACK_KEY, false);
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
            expandedText = getString(R.string.notification_content_format, voltageText);
        } else {
            expandedText = getString(R.string.notification_expanded_format, voltageText, minText, maxText);
        }




        UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        boolean isDarkMode = (uiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_YES
                || uiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_AUTO);


        RemoteViews notificationExpandedLayout = new RemoteViews(getPackageName(), R.layout.notification_expanded);
        notificationExpandedLayout.setTextViewText(R.id.notification_text_expanded, expandedText);
        if (isDarkMode) notificationExpandedLayout.setTextColor(R.id.notification_text_expanded, Color.WHITE);

        RemoteViews notificationLayout = new RemoteViews(getPackageName(), R.layout.notification);
        notificationLayout.setTextViewText(R.id.notification_text, getString(R.string.notification_content_format, voltageText) + " ");
        if (isDarkMode) notificationLayout.setTextColor(R.id.notification_text, Color.WHITE);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String shortVoltageText = voltageText.length() > 4 ? voltageText.substring(0, 4) : voltageText;
        //Bitmap voltageBitmap = BitmapUtils.textToBitmap(shortVoltageText, TEXT_COLOR);
        int iconScalePercent = prefs.getInt(ICON_SCALE_KEY, 100);
        Bitmap voltageBitmap =
                BitmapUtils.textToBitmap(
                        shortVoltageText,
                        TEXT_COLOR,
                        iconScalePercent / 100f
                );
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

    private void updateFloatingOverlay(String voltageText) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        if (!prefs.getBoolean(FLOATING_OVERLAY_KEY, false)) {
            removeFloatingOverlay();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            removeFloatingOverlay();
            return;
        }

        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }

        if (overlayText == null) {
            overlayText = new TextView(this);
            overlayText.setTextColor(Color.WHITE);
            overlayText.setPadding(12, 8, 12, 8);
            applyOverlayTheme();

            int windowType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    windowType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.TOP | Gravity.END;
            params.x = 12;
            params.y = 120;

            try {
                windowManager.addView(overlayText, params);
                overlayAdded = true;
            } catch (Exception e) {
                Log.e(TAG, "Unable to display floating voltage overlay", e);
                overlayText = null;
                overlayAdded = false;
                return;
            }
        }

        int iconScalePercent = prefs.getInt(ICON_SCALE_KEY, 100);
        overlayText.setTextSize(16 * iconScalePercent / 100f);

        overlayText.setText("\uD83D\uDD0B " + voltageText + " V");
        applyOverlayTheme();
    }

    private void removeFloatingOverlay() {
        if (overlayAdded && windowManager != null && overlayText != null) {
            try {
                windowManager.removeView(overlayText);
            } catch (Exception e) {
                Log.w(TAG, "Unable to remove floating voltage overlay", e);
            }
        }

        overlayText = null;
        overlayAdded = false;
    }

    private void applyOverlayTheme() {
        boolean isDarkMode =
                (getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;

        int backgroundColor = Color.argb(
                153,
                isDarkMode ? 0 : 255,
                isDarkMode ? 0 : 255,
                isDarkMode ? 0 : 255
        );

        int textColor = isDarkMode ? Color.WHITE : Color.BLACK;

        overlayText.setBackgroundColor(backgroundColor);
        overlayText.setTextColor(textColor);
    }
}