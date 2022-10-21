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
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

public class WallpaperWorker extends Worker {

    private final Context context;
    private final ImageStore store;
    private ImageObject imgObject;


    public WallpaperWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
        String imgId = workerParams.getInputData().getString("id");
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

        // Run with the specified periodicity (with 1 minute of slack)
        // Unless called with the immediate flag, the periodic work request is limited to 15+ minutes periodicity


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
        } catch (CancellationException e) {
            //do nothing
        }

        SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(context).edit();
        long now = new Date().getTime();
        prefEdit.putLong(context.getString(R.string.preference_worker_last_change), now);
        prefEdit.apply();
        if (PreferenceHelper.isActive(context))
            scheduleRandomWallpaper(context);
        return Result.success();
    }

    public static void scheduleRandomWallpaper(Context context) {
        scheduleRandomWallpaper(context, false, null);
    }

    public static void changeWallpaperNow(Context context, String imgObjectId) {
        scheduleRandomWallpaper(context, true, imgObjectId);
    }

    public static void scheduleRandomWallpaper(Context context, Boolean runNow, String imgObjectId) {
        Context mContext = context.getApplicationContext();
        boolean bReqIdle = PreferenceHelper.idleOnly(mContext);
        Data.Builder data = new Data.Builder();
        if (imgObjectId != null) {
            data.putString("id", imgObjectId);
        }
        OneTimeWorkRequest.Builder requestBuilder = new OneTimeWorkRequest
                .Builder(WallpaperWorker.class)
                .setInputData(data.build())
                .setConstraints(new Constraints.Builder()
                        .setRequiresDeviceIdle(bReqIdle)
                        .setRequiresBatteryNotLow(true)
                        .build());
        if (!bReqIdle)
            requestBuilder.setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        if (!runNow) {
            requestBuilder.setInitialDelay(PreferenceHelper.getWallpaperDelay(mContext), TimeUnit.MILLISECONDS)
                    .addTag(context.getString(R.string.work_random_wallpaper_id));
            WorkManager.getInstance(context).cancelAllWorkByTag(context.getString(R.string.work_random_wallpaper_id));
        }
        OneTimeWorkRequest saveRequest = requestBuilder.build();
        WorkManager.getInstance(mContext)
                .enqueue(saveRequest);

        if (!runNow) {
            SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
            prefEdit.putLong(mContext.getString(R.string.preference_worker_last_queue), new Date().getTime());
            prefEdit.apply();
        }
    }
}
