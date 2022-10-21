package com.moosedrive.wallpaperer;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

/**
 * The type Preference helper.
 */
public class PreferenceHelper {
    private static SharedPreferences sharedPreferences = null;

    /**
     * Gets scheduled wallpaper change.
     * This simply adds the known wallpaper delay to the last time a wallpaper was queued
     *
     * @param context the context
     * @return the scheduled wallpaper change in epoch milliseconds
     */
    public static long getScheduledWallpaperChange(Context context) {
        init(context);
        return getLastWallpaperQueue(context) + getWallpaperDelay(context);
    }

    /**
     * Gets last wallpaper queue.
     *
     * @param context the context
     * @return the last time wallpaper was queued in epoch milliseconds
     */
    public static long getLastWallpaperQueue(Context context) {
        init(context);
        return sharedPreferences.getLong(context.getString(R.string.preference_worker_last_queue), 0);
    }

    /**
     * Gets wallpaper delay, or the desired interval between wallpaper changes.
     *
     * @param context the context
     * @return the wallpaper delay in milliseconds
     */
    public static long getWallpaperDelay(Context context) {
        init(context);
        String delay = sharedPreferences.getString(context.getString(R.string.preference_time_delay), "00:15");
        int hours = Integer.parseInt(delay.split(":")[0]);
        int minutes = Integer.parseInt(delay.split(":")[1]);
        return (hours * 60L + minutes) * 60 * 1000L;
    }

    /**
     * Preference indicating if the wallpaper should change while the device is actively used
     *
     * @param context the context
     * @return true if preference indicates that wallpaper should only change during idle condition
     */
    public static boolean idleOnly(Context context) {
        init(context);
        return sharedPreferences.getBoolean(context.getApplicationContext().getResources().getString(R.string.preference_idle), false);
    }

    private static void init(Context context) {
        if (sharedPreferences == null)
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Indicate whether the wallpaper scheduler is running or not.
     * This method should only be used by the scheduler controls.
     *
     * @param context the context
     * @param bool    true if the scheduler is running
     */
    public static void setActive(Context context, boolean bool) {
        init(context);
        SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(context).edit();
        prefEdit.putBoolean("isActive", bool);
        prefEdit.apply();
    }

    /**
     * Indicates if the wallpaper scheduler is running or not.
     *
     * @param context the context
     * @return the boolean
     */
    public static boolean isActive(Context context) {
        init(context);
        return sharedPreferences.getBoolean("isActive", false);
    }
}
