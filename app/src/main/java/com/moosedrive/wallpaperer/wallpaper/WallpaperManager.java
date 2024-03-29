package com.moosedrive.wallpaperer.wallpaper;

import android.content.Context;
import android.net.Uri;
import android.os.StatFs;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;

import com.moosedrive.wallpaperer.R;
import com.moosedrive.wallpaperer.data.ImageObject;
import com.moosedrive.wallpaperer.data.ImageStore;
import com.moosedrive.wallpaperer.utils.BackgroundExecutor;
import com.moosedrive.wallpaperer.utils.StorageUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public class WallpaperManager {
    /**
     * The constant MINIMUM_REQUIRED_FREE_SPACE.
     */
    public static final long MINIMUM_REQUIRED_FREE_SPACE = 734003200L;
    private static WallpaperManager singleton;
    private final Set<IWallpaperAddedListener> wallpaperAddedListeners = new HashSet<>();
    private final Set<IWallpaperSetListener> wallpaperSetListeners = new HashSet<>();
    /**
     * The Loading done signal.
     */
    private CountDownLatch loadingDoneSignal;
    /**
     * The Loading errors.
     */
    private HashSet<String> loadingErrors = new HashSet<>();

    private WallpaperManager() {
    }

    public static WallpaperManager getInstance() {
        if (singleton == null)
            singleton = new WallpaperManager();
        return singleton;
    }

    /**
     * Add wallpaper added listener.
     *
     * @param wal the wal
     */
    public void addWallpaperAddedListener(IWallpaperAddedListener wal) {
        wallpaperAddedListeners.add(wal);
    }
    public void addWallpaperSetListener(IWallpaperSetListener wal) {
        wallpaperSetListeners.add(wal);
    }
    /**
     * Remove wallpaper added listener.
     *
     * @param wal the wal
     */
    public void removeWallpaperAddedListener(IWallpaperAddedListener wal) {
        wallpaperAddedListeners.remove(wal);
    }
    public void removeWallpaperSetListener(IWallpaperSetListener wal) {
        wallpaperSetListeners.remove(wal);
    }

    /**
     * Sets single wallpaper as soon as possible. Sets next wallpaper if object id == null
     *
     * @param imgObjectId the img object id
     */
    public void setSingleWallpaper(Context context, String imgObjectId) {
        ImageStore store = ImageStore.getInstance(context.getApplicationContext());
        if (store.size() == 0) {
            wallpaperSetListeners.forEach(IWallpaperSetListener::onWallpaperSetEmpty);
        } else {
            if (imgObjectId != null && !StorageUtils.sourceExists(context, store.getImageObject(imgObjectId).getUri())) {
                wallpaperSetListeners.forEach(listener -> listener.onWallpaperSetNotFound(imgObjectId));
            } else {
                WallpaperWorker.changeWallpaperNow(context, imgObjectId);
                wallpaperSetListeners.forEach(IWallpaperSetListener::onWallpaperSetSuccess);
            }
        }
    }
    /**
     * Add wallpapers from list of URI's.
     * Loading dialog is displayed and progress bar updated as wallpapers are added.
     *
     * @param context the context
     * @param sources the sources
     * @param store the ImageStore
     */
    public synchronized void addWallpapers(Context context, @NonNull HashSet<Uri> sources, ImageStore store) {
        boolean recompress = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getResources().getString(R.string.preference_recompress), false);
        loadingErrors = new HashSet<>();
        if (sources.size() > 0) {
            loadingDoneSignal = new CountDownLatch(sources.size());
            for (IWallpaperAddedListener wal : wallpaperAddedListeners)
                wal.onWallpaperLoadingStarted(sources.size(), null);
            for (Uri uri : sources) {
                Thread t = new Thread(() -> {
                    File fImageStorageFolder = StorageUtils.getStorageFolder(context);
                    StatFs stats = new StatFs(fImageStorageFolder.getAbsolutePath());
                    long bytesAvailable = stats.getAvailableBlocksLong() * stats.getBlockSizeLong();
                    if (!fImageStorageFolder.exists() && !fImageStorageFolder.mkdirs())
                        loadingErrors.add(context.getString(R.string.loading_error_cannot_mkdir));
                    else if (bytesAvailable < MINIMUM_REQUIRED_FREE_SPACE)
                        loadingErrors.add(context.getString(R.string.loading_error_precheck_low_space));
                    else {
                        String hash = StorageUtils.getHash(context, uri);
                        if (hash == null)
                            hash = UUID.randomUUID().toString();
                        if (store.getImageObject(hash) == null) {
                            // Get file modification date from file attributes (if available, 0 otherwise)
                            String name = StorageUtils.getFileAttrib(uri, DocumentsContract.Document.COLUMN_DISPLAY_NAME, context);
                            String type = context.getContentResolver().getType(uri);
                            long creationDate = StorageUtils.getCreationDate(context, uri);
                            if (type.startsWith("image/")) {
                                try {
                                    String uuid = StorageUtils.getRandomAlphaNumeric(4);
                                    String filename = name + "_" + uuid;
                                    long size = Long.parseLong(StorageUtils.getFileAttrib(uri, DocumentsContract.Document.COLUMN_SIZE, context));
                                    Uri uCopiedFile = StorageUtils.saveBitmap(context, uri, size, fImageStorageFolder.getPath(), filename, recompress);
                                    if (recompress) type = "image/webp";
                                    size = StorageUtils.getFileSize(uCopiedFile);
                                    try {
                                        // The current date/time, used as creation date/time if all other methods of getting the file's date/time fail
                                        Date dNow = new Date();
                                        ImageObject img = new ImageObject(uCopiedFile, hash, filename, size, type, dNow, (creationDate > 0) ? new Date(creationDate) : dNow);
                                        img.generateThumbnail(context);
                                        img.setColor(img.getColorFromBitmap(context));
                                        store.addImageObject(img);
                                    } catch (NoSuchAlgorithmException | IOException e) {
                                        e.printStackTrace();
                                    }
                                } catch (FileNotFoundException e) {
                                    loadingErrors.add(context.getString(R.string.loading_error_fnf));
                                } catch (IOException e) {
                                    loadingErrors.add(context.getString(R.string.loading_error_out_of_space));
                                }
                            } else {
                                loadingErrors.add(context.getString(R.string.loading_error_not_an_image));
                            }
                        }
                    }
                    for (IWallpaperAddedListener wal : wallpaperAddedListeners)
                        wal.onWallpaperLoadingIncrement(1);
                    loadingDoneSignal.countDown();
                });
                BackgroundExecutor.getExecutor().execute(t);
            }
            // UI work that waits for the image loading to complete
            BackgroundExecutor.getExecutor().execute(() -> {
                try {
                    loadingDoneSignal.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    StringBuilder sb = new StringBuilder();
                    for (String str : loadingErrors) {
                        sb.append(str);
                        sb.append(System.getProperty("line.separator"));
                    }
                    for (IWallpaperAddedListener wal : wallpaperAddedListeners)
                        wal.onWallpaperLoadingFinished(((loadingErrors.size()) == 0) ? IWallpaperAddedListener.SUCCESS : IWallpaperAddedListener.ERROR, sb.toString());
                }
            });
        }
    }


}