package com.moosedrive.wallpaperer.wallpaper;

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

import com.moosedrive.wallpaperer.R;
import com.moosedrive.wallpaperer.data.ImageObject;
import com.moosedrive.wallpaperer.data.ImageStore;
import com.moosedrive.wallpaperer.utils.PreferenceHelper;
import com.moosedrive.wallpaperer.utils.StorageUtils;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

/**
 * The type Wallpaper worker.
 */
public class WallpaperWorker extends Worker {

    private final Context context;
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

    @NonNull
    @Override
    public Result doWork() {

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
            Uri imgUri;
            if (imgObject == null) {
                int pos = store.getLastWallpaperPos();
                if (store.getLastWallpaperId().equals("")) {
                    //Image wasn't found at its expected position.
                    //Back-up the pointer so whatever slid into place
                    //will be the next paper
                    pos--;
                }
                pos++;
                if (pos >= store.size() || pos < 0) {
                    pos = 0;
                }
                imgObject = store.getImageObject(pos);
            }
            if (imgObject != null) {
                imgUri = imgObject.getUri();
                try (ParcelFileDescriptor pfd = context.
                        getContentResolver().
                        openFileDescriptor(imgUri, "r")) {
                    final Bitmap bitmapSource = BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor());
                    new Thread(() -> {
                        try {
                            boolean crop = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.preference_image_crop), true);
                            Bitmap bitmap = StorageUtils.resizeBitmapCenter(width, height, bitmapSource, crop);
                            WallpaperManager.getInstance(context).setBitmap(bitmap);
                            SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(context).edit();
                            long now = new Date().getTime();
                            prefEdit.putLong(context.getString(R.string.preference_worker_last_change), now);
                            prefEdit.putString(context.getString(R.string.last_wallpaper), imgObject.getId());
                            prefEdit.apply();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }).start();
                } catch (IOException e) {
                    store.delImageObject(imgObject.getId());
                    e.printStackTrace();
                } finally {
                    store.saveToPrefs(context);
                }
            }
        } catch (CancellationException e) {
            //do nothing
        }
        // schedule the next wallpaper change
        if (PreferenceHelper.isActive(context)) {
            scheduleRandomWallpaper(context, false, null);
        }
        return Result.success();
    }
}
