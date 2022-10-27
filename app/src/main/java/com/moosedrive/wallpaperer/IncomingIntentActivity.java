package com.moosedrive.wallpaperer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.DocumentsContract;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class IncomingIntentActivity extends AppCompatActivity {

    public CountDownLatch loadingDoneSignal;
    public HashSet<String> loadingErrors;
    private ThreadPoolExecutor executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incoming_intent);
        int procs = (Runtime.getRuntime().availableProcessors() < 2)
                ? 1
                : Runtime.getRuntime().availableProcessors() - 1;
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(procs);
        processIncomingIntentsAndExit();
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
                            addWallpapers(setUris);
                            setResult(Activity.RESULT_OK);
                            executor.execute(() -> {
                                if (loadingDoneSignal != null) {
                                    try {
                                        loadingDoneSignal.await();
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    } finally {
                                        dialog.dismiss();
                                        if (loadingErrors != null && loadingErrors.size() > 0) {
                                            StringBuilder sb = new StringBuilder();
                                            for (String str : loadingErrors) {
                                                sb.append(str);
                                                sb.append(System.getProperty("line.separator"));
                                            }
                                            new Handler(Looper.getMainLooper()).post(() -> new AlertDialog.Builder(this)
                                                    .setTitle("Error(s) loading images")
                                                    .setMessage(sb.toString())
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
                            });
                        })
                        .show();
            } else {
                setResult(Activity.RESULT_CANCELED);
                finishAfterTransition();
            }
        }
    }

    /**
     * Add wallpapers from list of URI's.
     * Loading dialog is displayed and progress bar updated as wallpapers are added.
     *
     * @param sources the sources
     */
    public void addWallpapers(HashSet<Uri> sources) {
        ImageStore images = ImageStore.getInstance();
        if (images.size() == 0)
            images.updateFromPrefs(this);
        boolean recompress = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getResources().getString(R.string.preference_recompress), false);
        final LoadingDialog loadingDialog = new LoadingDialog(this);
        loadingErrors = new HashSet<>();
        if (sources.size() > 0) {
            loadingDoneSignal = new CountDownLatch(sources.size());
            loadingDialog.startLoadingDialog(sources.size());
            for (Uri uri : sources) {
                Thread t = new Thread(() -> {
                    File fImageStorageFolder = StorageUtils.getStorageFolder(getBaseContext());
                    StatFs stats = new StatFs(fImageStorageFolder.getAbsolutePath());
                    long bytesAvailable = stats.getAvailableBlocksLong() * stats.getBlockSizeLong();
                    if (!fImageStorageFolder.exists() && !fImageStorageFolder.mkdirs()) {
                        ConstraintLayout constraintLayout = findViewById(R.id.intent_layout);
                        Snackbar.make(constraintLayout, getString(R.string.loading_error_cannot_mkdir), Snackbar.LENGTH_LONG)
                                .setBackgroundTint(getColor(androidx.cardview.R.color.cardview_dark_background))
                                .setTextColor(getColor(R.color.white))
                                .show();
                    } else if (bytesAvailable < MainActivity.MINIMUM_REQUIRED_FREE_SPACE)
                        loadingErrors.add(getString(R.string.loading_error_precheck_low_space));
                    else {
                        String hash = StorageUtils.getHash(this, uri);
                        if (hash == null)
                            hash = UUID.randomUUID().toString();
                        if (images.getImageObject(hash) == null) {
                            String name = StorageUtils.getFileAttrib(uri, DocumentsContract.Document.COLUMN_DISPLAY_NAME, this);
                            String type = StorageUtils.getFileAttrib(uri, DocumentsContract.Document.COLUMN_MIME_TYPE, this);
                            if (type.startsWith("image/")) {
                                try {
                                    String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 4);
                                    long size = Long.parseLong(StorageUtils.getFileAttrib(uri, DocumentsContract.Document.COLUMN_SIZE, this));
                                    Uri uCopiedFile = StorageUtils.saveBitmap(this, uri, size, fImageStorageFolder.getPath(), uuid + "_" + name, recompress);
                                    if (recompress) type = "image/jpeg";
                                    size = StorageUtils.getFileSize(uCopiedFile);
                                    try {
                                        ImageObject img = new ImageObject(uCopiedFile, hash, uuid + "_" + name, size, type, new Date());
                                        img.generateThumbnail(this);
                                        img.setColor(img.getColorFromBitmap(this));
                                        images.addImageObject(img);
                                        images.saveToPrefs(this);
                                    } catch (NoSuchAlgorithmException | IOException e) {
                                        e.printStackTrace();
                                    }
                                } catch (FileNotFoundException e) {
                                    loadingErrors.add(getString(R.string.loading_error_fnf));
                                } catch (IOException e) {
                                    loadingErrors.add(getString(R.string.loading_error_out_of_space));
                                }
                            } else {
                                loadingErrors.add(getString(R.string.loading_error_not_an_image));
                            }
                        }
                    }
                    loadingDialog.incrementProgressBy(1);
                    loadingDoneSignal.countDown();
                });
                executor.execute(t);
            }
            // UI work that waits for the image loading to complete
            executor.execute(() -> {
                try {
                    loadingDoneSignal.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    loadingDialog.dismissDialog();
                }
            });
        }
    }

}