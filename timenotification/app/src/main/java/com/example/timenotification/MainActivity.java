package com.example.timenotification;

import android.Manifest;
import android.app.Activity;
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


public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1;
    private TextView voltageTextView;
    private TextView minMaxVoltageTextView;
    private Handler handler = new Handler();
    private int colorIndex = 0;
    private CheckBox autostartCheckBox;
    private static final String PREFS_NAME = "MyPrefs";
    private static final String AUTO_START_KEY = "AutoStart";
    private static final String MIN_VOLTAGE_KEY = "MinVoltage";
    private static final String MAX_VOLTAGE_KEY = "MaxVoltage";
    private int[] colors = new int[]{
            Color.parseColor("#A52A2A"),  // Brown
            Color.parseColor("#808080"),  // Grey
            Color.parseColor("#0000FF")   // Blue
    };

    private RadioGroup textColorRadioGroup;
    private RadioButton radioBlack;
    private RadioButton radioWhite;
    private static final String RADIO_CHOSEN_BLACK_KEY = "RadioChosenBlack";

    // Variables for tracking max voltage
    private int highestVoltageValue = 0;
    private int stableVoltageCounter = 0;
    private boolean maxVoltageRecorded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        voltageTextView = findViewById(R.id.voltageTextView);
        minMaxVoltageTextView = findViewById(R.id.minMaxVoltageTextView);
        autostartCheckBox = findViewById(R.id.autostartCheckBox);

        textColorRadioGroup = findViewById(R.id.textColorRadioGroup);
        radioBlack = findViewById(R.id.radioBlack);
        radioWhite = findViewById(R.id.radioWhite);

        handler.postDelayed(updateVoltageTask, 15000);

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
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int minVoltage = prefs.getInt(MIN_VOLTAGE_KEY, -1);
        int maxVoltage = prefs.getInt(MAX_VOLTAGE_KEY, -1);

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

    private void checkAndRecordMinVoltage(int batteryPercent, int voltage) {
        if (batteryPercent <= 1) {  // Battery is at 1% or less
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int currentMinVoltage = prefs.getInt(MIN_VOLTAGE_KEY, -1);

            // Only record if we don't have a min voltage yet, or if this voltage is lower
            if (currentMinVoltage == -1 || voltage < currentMinVoltage) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(MIN_VOLTAGE_KEY, voltage);
                editor.apply();
                updateMinMaxVoltageDisplay();
                Log.d(TAG, "Recorded new min voltage: " + voltage);
            }
        }
    }

    private void checkAndRecordMaxVoltage(int batteryPercent, int voltage) {
        if (batteryPercent >= 100) {  // Battery is at 100%

            if (voltage > highestVoltageValue) {
                // New higher voltage found
                highestVoltageValue = voltage;
                stableVoltageCounter = 0;  // Reset counter
                maxVoltageRecorded = false;
                Log.d(TAG, "New highest voltage: " + voltage);
            } else if (voltage == highestVoltageValue) {
                // Voltage is stable at the highest value
                stableVoltageCounter = stableVoltageCounter + 1;

                // Check every 500ms, so 120 counts = 60 seconds
                if (stableVoltageCounter >= 120 && maxVoltageRecorded == false) {
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt(MAX_VOLTAGE_KEY, highestVoltageValue);
                    editor.apply();
                    maxVoltageRecorded = true;
                    updateMinMaxVoltageDisplay();
                    Log.d(TAG, "Recorded max voltage after 60 seconds stable: " + highestVoltageValue);
                }
            }
        } else {
            // Battery is not at 100%, reset tracking
            highestVoltageValue = 0;
            stableVoltageCounter = 0;
            maxVoltageRecorded = false;
        }
    }

    private Runnable updateVoltageTask = new Runnable() {
        @Override
        public void run() {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = registerReceiver(null, ifilter);
            int voltage = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) : -1;
            int batteryPercent = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;

            voltageTextView.setText("" + voltage/1000.0 + " V");
            voltageTextView.setTextColor(colors[colorIndex]);
            colorIndex = (colorIndex + 1) % colors.length;

            // Check and record min/max voltages
            if (voltage != -1 && batteryPercent != -1) {
                checkAndRecordMinVoltage(batteryPercent, voltage);
                checkAndRecordMaxVoltage(batteryPercent, voltage);
            }

            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
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