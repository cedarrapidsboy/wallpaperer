package com.moosedrive.wallpaperer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.appcompat.app.AlertDialog;

import java.util.HashSet;

public class chooserActivityResult implements ActivityResultCallback<ActivityResult> {
    private final Context baseContext;
    private final MainActivity parentActivity;

    public chooserActivityResult(MainActivity parentActivity, Context context) {
        this.baseContext = context;
        this.parentActivity = parentActivity;
    }

    @Override
    public void onActivityResult(ActivityResult result) {

        HashSet<Uri> sources = new HashSet<>();
        if (result.getResultCode() == Activity.RESULT_OK) {
            // There are no request codes
            Intent data = result.getData();
            StorageUtils.CleanUpOrphans(baseContext.getFilesDir().getAbsolutePath());
            if (data != null) {
                if (data.getData() != null) {
                    //Single select
                    //StorageUtils.takePersistableUriPermission(baseContext, data.getData());
                    sources.add(data.getData());

                } else if (data.getClipData() != null) {
                    for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                        Uri uri = data.getClipData().getItemAt(i).getUri();
                        //StorageUtils.takePersistableUriPermission(baseContext, uri);
                        sources.add(uri);
                    }
                }
                parentActivity.addWallpapers(sources);
                new Thread(() -> {
                    if (parentActivity.loadingDoneSignal != null) {
                        try {
                            parentActivity.loadingDoneSignal.await();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } finally {
                            if (parentActivity.loadingErrors != null && parentActivity.loadingErrors.size() > 0) {
                                    StringBuilder sb = new StringBuilder();
                                    for (String str : parentActivity.loadingErrors) {
                                        sb.append(str);
                                        sb.append(System.getProperty("line.separator"));
                                    }
                                    new Handler(Looper.getMainLooper()).post(() -> new AlertDialog.Builder(parentActivity)
                                            .setTitle("Error(s) loading images")
                                            .setMessage(sb.toString())
                                            .setPositiveButton("Got it", (dialog2, which2) -> dialog2.dismiss())
                                            .show());
                                }
                        }
                    }
                }).start();
            }
        }

    }

}
