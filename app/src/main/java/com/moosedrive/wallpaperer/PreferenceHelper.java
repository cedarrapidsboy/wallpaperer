package com.moosedrive.wallpaperer;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public class PreferenceHelper {
    private static SharedPreferences sharedPreferences = null;

    public static long getScheduledWallpaperChange(Context context) {
        init(context);
        return getLastWallpaperChange(context) + getWallpaperDelay(context);
    }

    public static long getLastWallpaperChange(Context context) {
        init(context);
        return sharedPreferences.getLong(context.getString(R.string.preference_worker_last_queue), 0);
    }

    public static long getWallpaperDelay(Context context) {
        init(context);
        String delay = sharedPreferences.getString(context.getString(R.string.preference_time_delay), "00:15");
        int hours = Integer.parseInt(delay.split(":")[0]);
        int minutes = Integer.parseInt(delay.split(":")[1]);
        return Math.max((hours * 60 + minutes) * 60, 15 * 60) * 1000L;
    }

    public static boolean idleOnly(Context context) {
        init(context);
        return sharedPreferences.getBoolean(context.getApplicationContext().getResources().getString(R.string.preference_idle), false);
    }

    private static void init(Context context) {
        if (sharedPreferences == null)
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static void setActive(Context context, boolean bool) {
        init(context);
        SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(context).edit();
        prefEdit.putBoolean("isActive", bool);
        prefEdit.apply();
    }

    public static boolean isActive(Context context) {
        init(context);
        return sharedPreferences.getBoolean("isActive", false);
    }
}
