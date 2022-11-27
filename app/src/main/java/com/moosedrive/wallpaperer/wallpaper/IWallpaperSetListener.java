package com.moosedrive.wallpaperer.wallpaper;

public interface IWallpaperSetListener {

    void onWallpaperSetNotFound(String id);

    void onWallpaperSetEmpty();

    void onWallpaperSetSuccess();
}
