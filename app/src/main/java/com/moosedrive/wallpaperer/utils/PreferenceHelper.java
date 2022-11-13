package com.moosedrive.wallpaperer.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.moosedrive.wallpaperer.R;

/**
 * The type Preference helper.
 */
public class PreferenceHelper {
    private static SharedPreferences sharedPreferences = null;

    private PreferenceHelper(){}

    /**
     * Gets scheduled wallpaper change.
     * This simply adds the known wallpaper delay to the last time a wallpaper was queued
     *
     * @param context the context
     * @return the scheduled wallpaper change in epoch milliseconds
     */
    public static long getScheduledWallpaperChange(Context context) {
        return getLastWallpaperQueue(context) + getWallpaperDelay(context);
    }

    /**
     * The preferred number of columns to show in the recycler view grid layout
     * @param context the context
     * @return columns preference
     */
    public static int getGridLayoutColumns(Context context){
        return Integer.parseInt(getInstance(context).getString(context.getString(R.string.preference_columns), "2"));
    }

    /**
     * Get the show stats preference
     * @param context the context
     * @return true if stats are desired in preferences
     */
    public static boolean showStats(Context context){
        return getInstance(context).getBoolean(context.getString(R.string.preference_card_stats), false);
    }
    /**
     * Gets last wallpaper queue.
     *
     * @param context the context
     * @return the last time wallpaper was queued in epoch milliseconds
     */
    public static long getLastWallpaperQueue(Context context) {
        return getInstance(context).getLong(context.getString(R.string.preference_worker_last_queue), 0);
    }

    /**
     * Gets wallpaper delay, or the desired interval between wallpaper changes.
     *
     * @param context the context
     * @return the wallpaper delay in milliseconds
     */
    public static long getWallpaperDelay(Context context) {
        String delay = getInstance(context).getString(context.getString(R.string.preference_time_delay), "00:15");
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
        return getInstance(context).getBoolean(context.getApplicationContext().getResources().getString(R.string.preference_idle), false);
    }

    private static SharedPreferences getInstance(Context context) {
        if (sharedPreferences == null)
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences;
    }

    /**
     * Indicate whether the wallpaper scheduler is running or not.
     * This method should only be used by the scheduler controls.
     *
     * @param context the context
     * @param bool    true if the scheduler is running
     */
    public static void setActive(Context context, boolean bool) {
        SharedPreferences.Editor prefEdit = getInstance(context).edit();
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
        return getInstance(context).getBoolean("isActive", false);
    }
}
