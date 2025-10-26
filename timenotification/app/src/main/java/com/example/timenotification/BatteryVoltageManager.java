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
    private int updateIntervalMs;
    private int requiredStableCounts;

    public BatteryVoltageManager(Context context, int updateIntervalMs) {
        this.context = context;
        this.updateIntervalMs = updateIntervalMs;
        // Calculate required counts for 30 seconds stability
        this.requiredStableCounts = 30000 / updateIntervalMs;

        // Initialize highestVoltageValue with stored max voltage or 0 if absent
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        highestVoltageValue = prefs.getInt(MAX_VOLTAGE_KEY, 0);
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
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus == null) return;

        int voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        int batteryPercent = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);

        if (voltage != -1 && batteryPercent != -1) {
            checkAndRecordMinVoltage(batteryPercent, voltage);
            checkAndRecordMaxVoltage(batteryPercent, voltage);
        }
    }

    private void checkAndRecordMinVoltage(int batteryPercent, int voltage) {
        if (batteryPercent <= 2) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            int currentMinVoltage = prefs.getInt(MIN_VOLTAGE_KEY, -1);

            if (currentMinVoltage == -1 || voltage < currentMinVoltage) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(MIN_VOLTAGE_KEY, voltage);
                editor.commit();
                Log.d(TAG, "Recorded new min voltage: " + voltage);
            }
        }
    }

    private void checkAndRecordMaxVoltage(int batteryPercent, int voltage) {
        if (batteryPercent >= 99) {
            if (voltage > highestVoltageValue) {
                highestVoltageValue = voltage;
                stableVoltageCounter = 0;
                maxVoltageRecorded = false;
                Log.d(TAG, "New highest voltage: " + voltage);
            } else if (voltage <= highestVoltageValue) {
                stableVoltageCounter++;

                if (stableVoltageCounter >= requiredStableCounts && !maxVoltageRecorded) {
                    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    int storedMaxVoltage = prefs.getInt(MAX_VOLTAGE_KEY, -1);

                    if (highestVoltageValue > storedMaxVoltage) {
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putInt(MAX_VOLTAGE_KEY, highestVoltageValue);
                        editor.commit();
                        Log.d(TAG, "Recorded new max voltage: " + highestVoltageValue);
                    } else {
                        Log.d(TAG, "Stored max voltage (" + storedMaxVoltage + ") is higher or equal, not updated.");
                    }

                    maxVoltageRecorded = true;
                    Log.d(TAG, "Recorded max voltage after 60 seconds stable: " + highestVoltageValue);
                }
            }
        } else {
            //highestVoltageValue = 0;
            stableVoltageCounter = 0;
            maxVoltageRecorded = false;
        }
    }

    public int getMinVoltage() {
        // Force reload of SharedPreferences from disk
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .commit(); // This syncs the cache with the file


        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(MIN_VOLTAGE_KEY, -1);
    }

    public int getMaxVoltage() {
        // Force reload of SharedPreferences from disk
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .commit(); // Flush or reload any pending async changes

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(MAX_VOLTAGE_KEY, -1);
    }
}