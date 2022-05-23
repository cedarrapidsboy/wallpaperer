package com.moosedrive.wallpaperer;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.util.Random;

public class WallpaperWorker extends Worker {

    private final Context context;
    private ImageObject imgObject;
    private final ImageStore store;


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
                            Bitmap bitmap = StorageUtils.resizeBitmapFitXY(width, height, bitmapSource);
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
        return Result.success();
    }

}
