package com.example.timenotification;

import android.Manifest;
import android.app.Activity;
import android.app.UiModeManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.view.View;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Context;


public class MainActivity extends Activity {
    private BatteryVoltageManager batteryVoltageManager;
    private static final String TAG = "MainActivity";
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1;
    private TextView voltageTextView;
    private TextView minMaxVoltageTextView;
    private Handler handler = new Handler();
    private int colorIndex = 0;
    private CheckBox autostartCheckBox;
    private static final String PREFS_NAME = "MyPrefs";
    private static final String AUTO_START_KEY = "AutoStart";
    private static final String FLOATING_OVERLAY_KEY = "FloatingOverlay";
    private CheckBox floatingOverlayCheckBox;
    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 2;
    private int[] colors = new int[]{
            Color.parseColor("#A52A2A"),  // Brown
            Color.parseColor("#808080"),  // Grey
            Color.parseColor("#0000FF")   // Blue
    };

    private RadioGroup textColorRadioGroup;
    private RadioButton radioBlack;
    private RadioButton radioWhite;
    private static final String RADIO_CHOSEN_BLACK_KEY = "RadioChosenBlack";
    private RollingChartView voltageChart;

    private static final String ICON_SCALE_KEY = "IconScale";
    private TextView iconScaleTextView;
    private Button iconScaleMinusButton;
    private Button iconScalePlusButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        batteryVoltageManager = new BatteryVoltageManager(this, 1000);

        voltageTextView = findViewById(R.id.voltageTextView);
        minMaxVoltageTextView = findViewById(R.id.minMaxVoltageTextView);
        autostartCheckBox = findViewById(R.id.autostartCheckBox);
        floatingOverlayCheckBox = findViewById(R.id.floatingOverlayCheckBox);

        textColorRadioGroup = findViewById(R.id.textColorRadioGroup);
        radioBlack = findViewById(R.id.radioBlack);
        radioWhite = findViewById(R.id.radioWhite);

        voltageChart = findViewById(R.id.voltageChart);
        Button zoomOutButton = findViewById(R.id.zoomOutButton);
        Button zoomInButton = findViewById(R.id.zoomInButton);
        TextView timeSpanLabel = findViewById(R.id.timeSpanLabel);

        iconScaleTextView = findViewById(R.id.iconScaleTextView);
        iconScaleMinusButton = findViewById(R.id.iconScaleMinusButton);
        iconScalePlusButton = findViewById(R.id.iconScalePlusButton);

        UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        boolean isDarkMode = (uiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_YES
                || uiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_AUTO);

        if (isDarkMode) {
            timeSpanLabel.setTextColor(Color.WHITE);
        } else {
            timeSpanLabel.setTextColor(Color.BLACK);
        }



        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        final int[] iconScalePercent = {
                prefs.getInt(ICON_SCALE_KEY, 100)
        };

        iconScaleTextView.setText(iconScalePercent[0] + "%");
        iconScaleMinusButton.setOnClickListener(v -> {
            if (iconScalePercent[0] > 1) {
                iconScalePercent[0]--;

                iconScaleTextView.setText(iconScalePercent[0] + "%");

                SharedPreferences.Editor editor =
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();

                editor.putInt(ICON_SCALE_KEY, iconScalePercent[0]);
                editor.apply();

                Intent intent = new Intent(MainActivity.this, TimeNotificationService.class);
                intent.setAction("UPDATE_NOTIFICATION");
                startService(intent);
            }
        });

        iconScalePlusButton.setOnClickListener(v -> {
            if (iconScalePercent[0] < 300) {
                iconScalePercent[0]++;

                iconScaleTextView.setText(iconScalePercent[0] + "%");

                SharedPreferences.Editor editor =
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();

                editor.putInt(ICON_SCALE_KEY, iconScalePercent[0]);
                editor.apply();

                Intent intent = new Intent(MainActivity.this, TimeNotificationService.class);
                intent.setAction("UPDATE_NOTIFICATION");
                startService(intent);
            }
        });

// Helper to update label
        Runnable updateLabel = new Runnable() {
            @Override
            public void run() {
                int seconds = voltageChart.getTimeSpanSeconds();
                int minutes = seconds / 60;
                if (minutes < 1) {
                    timeSpanLabel.setText("↤ " + seconds + " sec ↦");
                } else {
                    timeSpanLabel.setText("↤ " + minutes +","+(seconds-(minutes*60))+ " min ↦");
                }
            }
        };

// Initial label update
        updateLabel.run();

        zoomOutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int span = voltageChart.getTimeSpanSeconds();
                if (span < 1800) { // limit 30 minutes
                    voltageChart.setTimeSpanSeconds(span + 30);
                    updateLabel.run();
                }
            }
        });

        zoomInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int span = voltageChart.getTimeSpanSeconds();
                if (span > 60) { // limit 1 minute
                    voltageChart.setTimeSpanSeconds(span - 30);
                    updateLabel.run();
                }
            }
        });


        handler.postDelayed(updateVoltageTask, 1000);

        // Set initial state for the checkbox
        boolean isAutoStartEnabled = prefs.getBoolean(AUTO_START_KEY, false);
        autostartCheckBox.setChecked(isAutoStartEnabled);
        boolean radioChosenBlack = prefs.getBoolean(RADIO_CHOSEN_BLACK_KEY, false);
        radioBlack.setChecked(radioChosenBlack);
        radioWhite.setChecked(!radioChosenBlack);

        // Update min/max voltage display
        updateMinMaxVoltageDisplay();

        // Set checkbox change listener
        autostartCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                editor.putBoolean(AUTO_START_KEY, isChecked);
                editor.commit();

                if (isChecked) {
                    showAutostartDialog();
                }
            }
        });


        //Set overlay checkbox listener
        floatingOverlayCheckBox.setChecked(
                prefs.getBoolean(FLOATING_OVERLAY_KEY, false));

        floatingOverlayCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && !android.provider.Settings.canDrawOverlays(this)) {

                // Do not save the enabled state until permission is granted.
                floatingOverlayCheckBox.setChecked(false);

                Intent intent = new Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));

                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
                return;
            }

            prefs.edit().putBoolean(FLOATING_OVERLAY_KEY, isChecked).apply();

            Intent serviceIntent = new Intent(this, TimeNotificationService.class);
            serviceIntent.setAction("UPDATE_OVERLAY");
            startService(serviceIntent);
        });

        // Set radio group change listener
        textColorRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                boolean radioChosenBlack = checkedId == R.id.radioBlack;
                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                editor.putBoolean(RADIO_CHOSEN_BLACK_KEY, radioChosenBlack);
                editor.commit();
            }
        });

        Button exitButton = findViewById(R.id.exitButton);
        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopNotificationService(); // stop your running service
                handler.removeCallbacks(updateVoltageTask); // stop periodic tasks
                finishAndRemoveTask(); // close activity
                System.exit(0); // terminate current process
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkNotificationPermission();
        }


    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            startNotificationService();
            return;
        }

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            startNotificationService();
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("R.string.perm_dialog_title")
                .setMessage(getString(R.string.perm_dialog_message, 20))
                .setPositiveButton(R.string.ok, (d, which) -> requestNotificationPermission())
                .create();

        Handler dialogHandler = new Handler(getMainLooper());

        dialog.setOnDismissListener(d ->
                dialogHandler.removeCallbacksAndMessages(null));

        dialog.show();

        for (int i = 19; i >= 1; i--) {
            final int seconds = i;

            dialogHandler.postDelayed(() -> {
                if (dialog.isShowing()) {
                    dialog.setMessage(getString(R.string.perm_dialog_message, seconds));
                }
            }, (20 - i) * 1000L);
        }

        dialogHandler.postDelayed(() -> {
            if (dialog.isShowing()) {
                dialog.dismiss();
                requestNotificationPermission();
            }
        }, 20000);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST_CODE
            );
        }
    }

    private void showAutostartDialog() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.autostart_dialog_title)
                .setMessage(R.string.autostart_dialog_message)
                .setPositiveButton(R.string.open_settings, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent();
                        String packageName = getPackageName();
                        if (Build.MANUFACTURER.equalsIgnoreCase("xiaomi")) {
                            intent.setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                        } else if (Build.MANUFACTURER.equalsIgnoreCase("oppo")) {
                            intent.setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
                        } else if (Build.MANUFACTURER.equalsIgnoreCase("vivo")) {
                            intent.setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
                        } else if (Build.MANUFACTURER.equalsIgnoreCase("huawei")) {
                            intent.setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"));
                        } else if (Build.MANUFACTURER.equalsIgnoreCase("samsung")) {
                            intent.setComponent(new ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"));
                        } else if (Build.MANUFACTURER.equalsIgnoreCase("oneplus")) {
                            intent.setComponent(new ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"));
                        } else {
                            intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.parse("package:" + packageName));
                        }
                        try {
                            startActivity(intent);
                        } catch (Exception e) {
                            intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.parse("package:" + packageName));
                            startActivity(intent);
                        }
                    }
                })
                .setNegativeButton(R.string.ok, null)
                .setCancelable(true)
                .create();

        dialog.show();

        // Auto-dismiss after 30 seconds
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
            }
        }, 30000); // 30 seconds
    }


    private void startNotificationService() {
        Log.d(TAG, "onCreate: Starting TimeNotificationService");
        Intent intent = new Intent(this, TimeNotificationService.class);
        startForegroundService(intent);
    }

    private void stopNotificationService() {
        Log.d(TAG, "stopNotificationService: Stopping TimeNotificationService");
        Intent intent = new Intent(this, TimeNotificationService.class);
        stopService(intent);
    }

    private void updateMinMaxVoltageDisplay() {
        int minVoltage = batteryVoltageManager.getMinVoltage();
        int maxVoltage = batteryVoltageManager.getMaxVoltage();

        String minText = getString(R.string.not_recorded);
        String maxText = getString(R.string.not_recorded);

        if (minVoltage != -1) {
            minText = String.valueOf(minVoltage / 1000.0) + " V";
        }

        if (maxVoltage != -1) {
            maxText = String.valueOf(maxVoltage / 1000.0) + " V";
        }

        minMaxVoltageTextView.setText(getString(R.string.min_max_format, minText, maxText));
    }



    private Runnable updateVoltageTask = new Runnable() {
        @Override
        public void run() {
            int voltage = batteryVoltageManager.getCurrentBatteryVoltage();

            voltageTextView.setText("\uD83D\uDD0B " + voltage/1000.0 + " V");
            voltageTextView.setTextColor(colors[colorIndex]);
            colorIndex = (colorIndex + 1) % colors.length;

            batteryVoltageManager.checkAndRecordVoltages();
            updateMinMaxVoltageDisplay();

            // update chart
            voltageChart.addValue((float) (voltage/1000.0));

            handler.postDelayed(this, 1000);
        }
    };


    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);

            if (voltage > 0) {
                double voltageInVolts = voltage / 1000.0;
       /*
                voltageTextView.setText(voltageInVolts + " V");
                voltageTextView.setTextColor(colors[colorIndex]);
                colorIndex = (colorIndex + 1) % colors.length;
*/
                batteryVoltageManager.checkAndRecordVoltages();
                updateMinMaxVoltageDisplay();

                voltageChart.addValue((float) voltageInVolts);
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, filter);
        //handler.postDelayed(updateVoltageTask, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(batteryReceiver);
        //handler.removeCallbacks(updateVoltageTask);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException e) {
            // receiver was probably not registered - ignore
        }
        handler.removeCallbacks(updateVoltageTask);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startNotificationService();
            } else {
                Log.w(TAG, "Notification permission denied");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            boolean granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                    || android.provider.Settings.canDrawOverlays(this);

            if (granted) {
                floatingOverlayCheckBox.setChecked(true);
            }
        }
    }
}