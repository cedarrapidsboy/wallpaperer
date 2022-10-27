package com.moosedrive.wallpaperer;

public interface WallpaperAddedListener {
    int SUCCESS = 0;
    int ERROR = 1;
    void onWallpaperAdded(ImageObject img);
    void onWallpaperLoadingStarted(int size);
    void onWallpaperLoadingIncrement(int inc);
    void onWallpaperLoadingFinished(int status, String message);
}
