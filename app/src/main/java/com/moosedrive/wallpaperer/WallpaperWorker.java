package com.moosedrive.wallpaperer;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.util.Date;
import java.util.Random;

public class WallpaperWorker extends Worker {

    private final Context context;
    private ImageObject imgObject;
    private final ImageStore store;
    private final boolean immediate;


    public WallpaperWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
        String imgId = workerParams.getInputData().getString("id");
        immediate = workerParams.getInputData().getBoolean("immediate", false);
        store = ImageStore.getInstance();
        if (store.size() == 0)
            store.updateFromPrefs(context);
        if (imgId != null) {
            imgObject = store.getImageObject(imgId);
        } else {
            imgObject = null;
        }
    }

    @NonNull
    @Override
    public Result doWork() {

        String delay = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.preference_time_delay), "00:15");
        long lastChange = PreferenceManager.getDefaultSharedPreferences(context).getLong("worker_last_change", 0);
        int hours = Integer.parseInt(delay.split(":")[0]);
        int minutes = Integer.parseInt(delay.split(":")[1]);
        int delayMs = Math.max((hours * 60 + minutes) * 60, 15 * 60) * 1000;
        long now = new Date().getTime();
        // Run with the specified periodicity (with 1 minute of slack)
        // Unless called with the immediate flag, the periodic work request is limited to 15+ minutes periodicity
        if (immediate || now - lastChange >= delayMs - 60000) {

            int compatWidth;
            int compatHeight;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                WindowMetrics metrics = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getCurrentWindowMetrics();
                compatWidth = metrics.getBounds().width();
                compatHeight = metrics.getBounds().height();
            } else {
                compatHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
                compatWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
            }
            int height = compatHeight; //(wm.getDesiredMinimumHeight() > 0) ? wm.getDesiredMinimumHeight() : compatHeight;
            int width = compatWidth; //(wm.getDesiredMinimumWidth() > 0) ? wm.getDesiredMinimumWidth() : compatWidth;

            try {
                Uri imgUri = null;
                if (imgObject != null)
                    imgUri = imgObject.getUri();
                else {
                    if (store.size() > 0) {
                        Random rand = new Random();
                        int nextInt = rand.nextInt(store.size());
                        imgObject = store.getImageObject(nextInt);
                        imgUri = imgObject.getUri();
                    }
                }
                if (imgUri != null) {
                    try {
                        ParcelFileDescriptor pfd = context.
                                getContentResolver().
                                openFileDescriptor(imgUri, "r");
                        final Bitmap bitmapSource = BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor());
                        pfd.close();
                        new Thread(() -> {
                            try {
                                boolean crop = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.preference_image_crop), true);
                                Bitmap bitmap = StorageUtils.resizeBitmapCenter(width, height, bitmapSource, crop);
                                WallpaperManager.getInstance(context).setBitmap(bitmap);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }).start();
                    } catch (IOException e) {
                        store.delImageObject(imgObject.getId());
                        //StorageUtils.releasePersistableUriPermission(context, imgObject.getUri());
                        store.saveToPrefs(context);
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                return Result.failure();
            }
            SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(context).edit();
            prefEdit.putLong("worker_last_change", now);
            prefEdit.apply();
        }
        return Result.success();
    }

}
