package com.moosedrive.wallpaperer.data;

import android.net.Uri;

import java.util.ArrayList;

public class ImportData {
    private static ImportData thisInstance = null;
    public final ArrayList<Uri> importSources = new ArrayList<>();
    private ImportData(){}

    public static ImportData getInstance(){
        if (thisInstance == null){
            thisInstance = new ImportData();
        }
        return thisInstance;
    }
}
