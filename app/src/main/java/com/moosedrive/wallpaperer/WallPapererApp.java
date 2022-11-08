package com.moosedrive.wallpaperer;

import android.app.Application;
import android.os.StrictMode;

public class WallPapererApp extends Application {
    public WallPapererApp() {
        if(BuildConfig.DEBUG)
            StrictMode.enableDefaults();
    }
}
