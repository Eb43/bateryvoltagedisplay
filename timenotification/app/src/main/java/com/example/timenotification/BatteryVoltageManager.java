package com.example.timenotification;

        import android.content.Context;
        import android.content.Intent;
        import android.content.IntentFilter;
        import android.content.SharedPreferences;
        import android.os.BatteryManager;
        import android.util.Log;

public class BatteryVoltageManager {
    private static final String TAG = "BatteryVoltageManager";
    private static final String PREFS_NAME = "MyPrefs";
    private static final String MIN_VOLTAGE_KEY = "MinVoltage";
    private static final String MAX_VOLTAGE_KEY = "MaxVoltage";

    private Context context;
    private int highestVoltageValue = 0;
    private int stableVoltageCounter = 0;
    private boolean maxVoltageRecorded = false;

    public BatteryVoltageManager(Context context) {
        this.context = context;
    }

    public int getCurrentBatteryVoltage() {
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) : -1;
    }

    public int getCurrentBatteryPercent() {
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
    }

    public String getCurrentBatteryVoltageAsString() {
        int voltage = getCurrentBatteryVoltage();
        return voltage != -1 ? String.valueOf(voltage / 1000.0) : "Unknown";
    }

    public void checkAndRecordVoltages() {
        int voltage = getCurrentBatteryVoltage();
        int batteryPercent = getCurrentBatteryPercent();

        if (voltage != -1 && batteryPercent != -1) {
            checkAndRecordMinVoltage(batteryPercent, voltage);
            checkAndRecordMaxVoltage(batteryPercent, voltage);
        }
    }

    private void checkAndRecordMinVoltage(int batteryPercent, int voltage) {
        if (batteryPercent <= 1) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            int currentMinVoltage = prefs.getInt(MIN_VOLTAGE_KEY, -1);

            if (currentMinVoltage == -1 || voltage < currentMinVoltage) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(MIN_VOLTAGE_KEY, voltage);
                editor.apply();
                Log.d(TAG, "Recorded new min voltage: " + voltage);
            }
        }
    }

    private void checkAndRecordMaxVoltage(int batteryPercent, int voltage) {
        if (batteryPercent >= 100) {
            if (voltage > highestVoltageValue) {
                highestVoltageValue = voltage;
                stableVoltageCounter = 0;
                maxVoltageRecorded = false;
                Log.d(TAG, "New highest voltage: " + voltage);
            } else if (voltage == highestVoltageValue) {
                stableVoltageCounter++;

                if (stableVoltageCounter >= 120 && !maxVoltageRecorded) {
                    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt(MAX_VOLTAGE_KEY, highestVoltageValue);
                    editor.apply();
                    maxVoltageRecorded = true;
                    Log.d(TAG, "Recorded max voltage after 60 seconds stable: " + highestVoltageValue);
                }
            }
        } else {
            highestVoltageValue = 0;
            stableVoltageCounter = 0;
            maxVoltageRecorded = false;
        }
    }

    public int getMinVoltage() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(MIN_VOLTAGE_KEY, -1);
    }

    public int getMaxVoltage() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(MAX_VOLTAGE_KEY, -1);
    }
}