package com.moosedrive.wallpaperer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.moosedrive.wallpaperer.data.ImageObject;
import com.moosedrive.wallpaperer.data.ImageStore;
import com.moosedrive.wallpaperer.wallpaper.WallpaperManager;

import java.util.HashSet;

public class IncomingIntentActivity extends AppCompatActivity implements WallpaperManager.IWallpaperAddedListener {

    private ImageStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        store = ImageStore.getInstance(getApplicationContext());
        store.updateFromPrefs(getApplicationContext());
        setContentView(R.layout.activity_incoming_intent);
        processIncomingIntentsAndExit();
        super.onCreate(savedInstanceState);
    }

    private void processIncomingIntentsAndExit() {
        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        //Intents can affect previous activities in history. Not easy to reset an intent. So, detect a history launch instead.
        boolean launchedFromHistory = (getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY;
        if (!launchedFromHistory && type != null && (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action))) {
            HashSet<Uri> setUris = new HashSet<>();
            if (intent.getClipData() != null) {
                for (int i = 0; i < intent.getClipData().getItemCount(); i++) {
                    setUris.add(intent.getClipData().getItemAt(i).getUri());
                }
            } else if (intent.getData() != null)
                setUris.add(intent.getData());
            if (setUris.size() > 0) {
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.dialog_title_add_intent))
                        .setMessage(getString(R.string.dialog_msg_add_intent_message))
                        .setCancelable(false)
                        .setNegativeButton(getString(R.string.dialog_button_no), (dialog, which) -> {
                            dialog.dismiss();
                            setResult(Activity.RESULT_CANCELED);
                            finishAfterTransition();
                        })
                        .setPositiveButton(getString(R.string.dialog_button_yes_add_intent), (dialog, which) -> {
                            dialog.dismiss();
                            WallpaperManager.getInstance().addWallpaperAddedListener(this);
                            WallpaperManager.getInstance().addWallpapers(this, setUris, ImageStore.getInstance(getApplicationContext()));
                            setResult(Activity.RESULT_OK);
                        }).show();
            } else {
                setResult(Activity.RESULT_CANCELED);
                finishAfterTransition();
            }
        }
    }

    ProgressDialogFragment loadingDialog;

    @Override
    public void onWallpaperLoadingStarted(int size, String msg) {
        runOnUiThread(() -> {loadingDialog = ProgressDialogFragment.newInstance(size);
        loadingDialog.showNow(getSupportFragmentManager(),"add_progress");});
    }

    @Override
    public void onWallpaperLoadingIncrement(int inc) {
        runOnUiThread(() -> loadingDialog.incrementProgressBy(inc));
    }

    @Override
    public void onWallpaperLoadingFinished(int status, String msg) {
        runOnUiThread(()->{
            if (loadingDialog != null)
                loadingDialog.dismiss();
        });
        WallpaperManager.getInstance().removeWallpaperAddedListener(this);
        store.saveToPrefs();
        if (status != WallpaperManager.IWallpaperAddedListener.SUCCESS) {
            new Handler(Looper.getMainLooper()).post(() -> new AlertDialog.Builder(this)
                    .setTitle("Error(s) loading images")
                    .setMessage((msg != null) ? msg : "Unknown error.")
                    .setPositiveButton("Got it", (dialog2, which2) -> {
                        dialog2.dismiss();
                        finishAfterTransition();
                    })
                    .show());
        } else {
            finishAfterTransition();
        }
    }

}