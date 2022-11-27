package com.moosedrive.wallpaperer.wallpaper;

/**
 * The interface Wallpaper added listener.
 */
public interface IWallpaperAddedListener {
    /**
     * The constant SUCCESS.
     */
    int SUCCESS = 0;
    /**
     * The constant ERROR.
     */
    int ERROR = 1;

    /**
     * On wallpaper loading started.
     *
     * @param size the size
     */
    void onWallpaperLoadingStarted(int size, String message);

    /**
     * On wallpaper loading increment.
     *
     * @param inc the inc
     */
    void onWallpaperLoadingIncrement(int inc);

    /**
     * On wallpaper loading finished.
     *
     * @param status  the status
     * @param message the message
     */
    void onWallpaperLoadingFinished(int status, String message);

}
