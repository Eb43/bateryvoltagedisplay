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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        batteryVoltageManager = new BatteryVoltageManager(this, 1000);

        voltageTextView = findViewById(R.id.voltageTextView);
        minMaxVoltageTextView = findViewById(R.id.minMaxVoltageTextView);
        autostartCheckBox = findViewById(R.id.autostartCheckBox);

        textColorRadioGroup = findViewById(R.id.textColorRadioGroup);
        radioBlack = findViewById(R.id.radioBlack);
        radioWhite = findViewById(R.id.radioWhite);

        voltageChart = findViewById(R.id.voltageChart);
        Button zoomOutButton = findViewById(R.id.zoomOutButton);
        Button zoomInButton = findViewById(R.id.zoomInButton);
        TextView timeSpanLabel = findViewById(R.id.timeSpanLabel);
        UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        boolean isDarkMode = (uiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_YES
                || uiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_AUTO);

        if (isDarkMode) {
            timeSpanLabel.setTextColor(Color.WHITE);
        } else {
            timeSpanLabel.setTextColor(Color.BLACK);
        }


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
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isAutoStartEnabled = prefs.getBoolean(AUTO_START_KEY, false);
        autostartCheckBox.setChecked(isAutoStartEnabled);
        boolean radioChosenBlack = prefs.getBoolean(RADIO_CHOSEN_BLACK_KEY, true);
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
                editor.apply();

                if (isChecked) {
                    showAutostartDialog();
                }
            }
        });

        // Set radio group change listener
        textColorRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                boolean radioChosenBlack = checkedId == R.id.radioBlack;
                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                editor.putBoolean(RADIO_CHOSEN_BLACK_KEY, radioChosenBlack);
                editor.apply();
            }
        });

        Button exitButton = findViewById(R.id.exitButton);
        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopNotificationService();
                finishAndRemoveTask();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
            } else {
                startNotificationService();
            }
        } else {
            startNotificationService();
        }


    }

    private void showAutostartDialog() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Autostart Permission Needed")
                .setMessage("Enable autostart permission to ensure notifications work reliably after reboot.")
                .setPositiveButton("OPEN SETTINGS", new DialogInterface.OnClickListener() {
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
                .setNegativeButton("OK", null)
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

        String minText = "Not recorded";
        String maxText = "Not recorded";

        if (minVoltage != -1) {
            minText = String.valueOf(minVoltage / 1000.0) + " V";
        }

        if (maxVoltage != -1) {
            maxText = String.valueOf(maxVoltage / 1000.0) + " V";
        }

        minMaxVoltageTextView.setText("Min (0%): " + minText + " | Max (100%): " + maxText);
    }



    private Runnable updateVoltageTask = new Runnable() {
        @Override
        public void run() {
            int voltage = batteryVoltageManager.getCurrentBatteryVoltage();

            voltageTextView.setText("" + voltage/1000.0 + " V");
            voltageTextView.setTextColor(colors[colorIndex]);
            colorIndex = (colorIndex + 1) % colors.length;
/*
            batteryVoltageManager.checkAndRecordVoltages();
            updateMinMaxVoltageDisplay();
*/
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
}