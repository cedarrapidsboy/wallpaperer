package com.moosedrive.wallpaperer.utils;

import com.moosedrive.wallpaperer.data.ImageObject;

public interface IExportListener {
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
    void onExportStarted(int size, String message);

    /**
     * On wallpaper loading increment.
     *
     * @param inc the inc
     */
    void onExportIncrement(int inc);

    /**
     * On wallpaper loading finished.
     *
     * @param status  the status
     * @param message the message
     */
    void onExportFinished(int status, String message);
}
