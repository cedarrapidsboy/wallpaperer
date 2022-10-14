package com.moosedrive.wallpaperer;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public class PreferenceHelper {
    private static SharedPreferences sharedPreferences = null;

    public static long getScheduledWallpaperChange(Context context){
        init(context);
        long lastChange = sharedPreferences.getLong("worker_last_change", 0);

        return lastChange + getWallpaperDelay(context);
    }

    public static long getWallpaperDelay(Context context){
        init(context);
        String delay = sharedPreferences.getString(context.getString(R.string.preference_time_delay), "00:15");
        int hours = Integer.parseInt(delay.split(":")[0]);
        int minutes = Integer.parseInt(delay.split(":")[1]);
        return Math.max((hours * 60 + minutes) * 60, 15 * 60) * 1000L;
    }

    private static void init(Context context) {
        if (sharedPreferences == null)
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }
}
