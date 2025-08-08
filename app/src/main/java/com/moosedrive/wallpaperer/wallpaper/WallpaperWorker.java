package com.moosedrive.wallpaperer.wallpaper;

import android.annotation.SuppressLint;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
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

import com.moosedrive.wallpaperer.R;
import com.moosedrive.wallpaperer.data.ImageObject;
import com.moosedrive.wallpaperer.data.ImageStore;
import com.moosedrive.wallpaperer.utils.PreferenceHelper;
import com.moosedrive.wallpaperer.utils.StorageUtils;

import java.io.IOException;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

/**
 * The type Wallpaper worker.
 */
public class WallpaperWorker extends Worker {

    private static final String TAG = "WallpaperWorker";

    private final ImageStore store;
    private ImageObject imgObject;


    /**
     * Instantiates a new Wallpaper worker.
     * Valid input data:
     * "id" - an ImageObject id
     *
     * @param context      the context
     * @param workerParams the worker params
     */
    public WallpaperWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        String imgId = workerParams.getInputData().getString("id");
        store = ImageStore.getInstance(getApplicationContext());
        if (store.size() == 0)
            store.updateFromPrefs(getApplicationContext());
        if (imgId != null)
            imgObject = store.getImageObject(imgId);
    }

    /**
     * Start the wallpaper scheduler.
     * Will wait the preferred wallpaper delay (from preferences) prior to the first execution.
     *
     * @param context the context
     */
    public static void scheduleRandomWallpaper(Context context) {
        scheduleRandomWallpaper(context, false, null);
    }

    /**
     * Change the wallpaper now.
     * Will reset the wallpaper scheduler timer if it is currently running.
     *
     * @param context     the context
     * @param imgObjectId the img object id
     */
    public static void changeWallpaperNow(Context context, String imgObjectId) {
        scheduleRandomWallpaper(context, true, imgObjectId);
    }

    /**
     * Schedule random wallpaper.
     * Depending on arguments it may change the wallpaper immediately or simply start the scheduler new.
     *
     * @param context     the context
     * @param runNow      change the wallpaper immediately, will neither cancel nor start a schedule (will reset a running schedule)
     * @param imgObjectId the img object id
     */
    private static void scheduleRandomWallpaper(Context context, Boolean runNow, String imgObjectId) {
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
            //We rescheduling the next wallpaper. Be sure to cancel any previous scheduled work in order to avoid duplication
            WorkManager.getInstance(mContext).cancelAllWorkByTag(mContext.getString(R.string.work_random_wallpaper_id));
            //Set the preferred delay for the next scheduled wallpaper change
            requestBuilder.setInitialDelay(PreferenceHelper.getWallpaperDelay(mContext), TimeUnit.MILLISECONDS)
                    .addTag(mContext.getString(R.string.work_random_wallpaper_id));
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

    @SuppressLint("ObsoleteSdkInt")
    @NonNull
    @Override
    public Result doWork() {

        int compatWidth;
        int compatHeight;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            WindowMetrics metrics = ((WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE)).getCurrentWindowMetrics();
            compatWidth = metrics.getBounds().width();
            compatHeight = metrics.getBounds().height();
        } else {
            compatHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
            compatWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        }
        int height = compatHeight; //(wm.getDesiredMinimumHeight() > 0) ? wm.getDesiredMinimumHeight() : compatHeight;
        int width = compatWidth; //(wm.getDesiredMinimumWidth() > 0) ? wm.getDesiredMinimumWidth() : compatWidth;

        try {
            Uri imgUri;
            if (imgObject != null) //worker is setting a specific image (see constructor)
                store.setActive(imgObject.getId());
            else //worker is setting the next image in turn
                imgObject = store.activateNext();
            if (imgObject != null) {
                imgUri = imgObject.getUri();
                try (ParcelFileDescriptor pfd = getApplicationContext().
                        getContentResolver().
                        openFileDescriptor(imgUri, "r")) {
                    final Bitmap bitmapSource = BitmapFactory.decodeFileDescriptor(Objects.requireNonNull(pfd).getFileDescriptor());
                    new Thread(() -> {
                        try {
                            boolean crop = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean(getApplicationContext().getString(R.string.preference_image_crop), true);
                            Bitmap bitmap = StorageUtils.resizeBitmapCenter(width, height, bitmapSource, crop);
                            WallpaperManager.getInstance(getApplicationContext()).setBitmap(bitmap);
                            Log.i(TAG, "Wallpaper set to: " + imgObject.getName());
                        } catch (IOException e) {
                            Log.e(TAG, "Error setting wallpaper: " + imgObject.getName(), e);
                        }
                    }).start();
                } catch (IOException e) {
                    //couldn't open image - remove it from the list
                    Log.e(TAG, "Error opening image file: " + imgUri + ". Removing from store.", e);
                    store.delImageObject(imgObject.getId());
                } finally {
                    store.saveToPrefs();
                }
            }
        } catch (CancellationException e) {
            //do nothing
        }
        // schedule the next wallpaper change
        if (PreferenceHelper.isActive(getApplicationContext())) {
            scheduleRandomWallpaper(getApplicationContext(), false, null);
        }
        return Result.success();
    }
}
