package com.moosedrive.wallpaperer.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.moosedrive.wallpaperer.R;
import com.moosedrive.wallpaperer.utils.PreferenceHelper;
import com.moosedrive.wallpaperer.wallpaper.WallpaperWorker;

import java.util.Date;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()) ||
                Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction())) {

            // Only reschedule if wallpaper changing is active
            if (PreferenceHelper.isActive(context)) {
                // Check if we missed a scheduled wallpaper change
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                long lastQueue = prefs.getLong(context.getString(R.string.preference_worker_last_queue), 0);
                long wallpaperDelay = PreferenceHelper.getWallpaperDelay(context);
                long currentTime = new Date().getTime();

                // If we're past the expected execution time, change wallpaper immediately
                if (lastQueue > 0 && (currentTime - lastQueue) > wallpaperDelay) {
                    WallpaperWorker.changeWallpaperNow(context, null);
                } else {
                    // Otherwise, reschedule normally
                    WallpaperWorker.scheduleRandomWallpaper(context);
                }
            }
        }
    }
}
