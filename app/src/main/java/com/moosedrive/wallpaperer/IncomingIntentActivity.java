package com.moosedrive.wallpaperer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider; // Import ViewModelProvider

import com.moosedrive.wallpaperer.data.ImageStore;
import com.moosedrive.wallpaperer.wallpaper.IWallpaperAddedListener;
import com.moosedrive.wallpaperer.wallpaper.WallpaperManager;

import java.util.HashSet;

public class IncomingIntentActivity extends AppCompatActivity implements IWallpaperAddedListener {

    private ImageStore store;
    private ProgressViewModel progressViewModel; // Add ViewModel instance
    private static final String PROGRESS_DIALOG_TAG = "incoming_progress_dialog"; // Unique tag

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // Call super.onCreate() first
        store = ImageStore.getInstance(getApplicationContext());
        store.updateFromPrefs(getApplicationContext());

        // Initialize ViewModel
        // The ViewModel will be scoped to this Activity's lifecycle
        progressViewModel = new ViewModelProvider(this).get(ProgressViewModel.class);

        setContentView(R.layout.activity_incoming_intent); // Content view can be simple or even empty if the activity is mostly for processing

        // Observe the isVisible state to show/hide the dialog fragment
        progressViewModel.getUiState().observe(this, state -> {
            if (state != null) {
                ProgressDialogFragment existingDialog = (ProgressDialogFragment) getSupportFragmentManager().findFragmentByTag(PROGRESS_DIALOG_TAG);
                if (state.isVisible) {
                    if (existingDialog == null) {
                        ProgressDialogFragment dialogFragment = new ProgressDialogFragment();
                        // The dialog will get its state (max, message, progress)
                        // directly from the ViewModel when it's created/shown.
                        dialogFragment.show(getSupportFragmentManager(), PROGRESS_DIALOG_TAG);
                    }
                } else {
                    // If the ViewModel indicates the dialog should not be visible,
                    // and it is currently shown, dismiss it.
                    if (existingDialog != null && existingDialog.isAdded() && existingDialog.getDialog() != null && existingDialog.getDialog().isShowing()) {
                        existingDialog.dismissAllowingStateLoss();
                    }
                }
            }
        });

        processIncomingIntentsAndHandleExit();

    }

    private void processIncomingIntentsAndHandleExit() {
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        boolean launchedFromHistory = (intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY;

        if (!launchedFromHistory && type != null && (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action))) {
            final HashSet<Uri> setUris = new HashSet<>();
            if (intent.getClipData() != null) {
                for (int i = 0; i < intent.getClipData().getItemCount(); i++) {
                    setUris.add(intent.getClipData().getItemAt(i).getUri());
                }
            } else if (intent.getData() != null) { // Use intent.getData() for single SEND action
                setUris.add(intent.getData());
            }

            if (!setUris.isEmpty()) {
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
                            WallpaperManager.getInstance().addWallpapers(IncomingIntentActivity.this, setUris, store);
                            // Don't set result or finish here yet.
                            // The result/finish will be handled in onWallpaperLoadingFinished.
                        }).show();
            } else {
                // No URIs to process, finish.
                setResult(Activity.RESULT_CANCELED);
                finishAfterTransition();
            }
        } else {
            // Not a new SEND intent (e.g., launched from history or different action/type), so just finish.
            // If it's launched from history and a dialog was showing, ViewModel will restore it.
            // However, typical flow for this activity is to process once and exit.
            // If progressViewModel indicates dialog is visible (e.g. after rotation during processing)
            // don't finish immediately, let the loading finish.
            if (progressViewModel.getUiState().getValue() == null || !progressViewModel.getUiState().getValue().isVisible) {
                finishAfterTransition();
            }
        }
    }

    // --- IWallpaperAddedListener Implementation using ProgressViewModel ---

    @Override
    public void onWallpaperLoadingStarted(final int size, final String msg) {
        // This method is called by WallpaperManager, potentially from a background thread.
        // All ViewModel updates (which affect LiveData) must happen on the main thread.
        runOnUiThread(() -> {
            boolean isIndeterminate = (size <= 0);
            String dialogMessage = (msg != null) ? msg : (isIndeterminate ? "Processing..." : "Adding items: 0/" + size);
            progressViewModel.showDialog(size, dialogMessage, isIndeterminate);
        });
    }

    @Override
    public void onWallpaperLoadingIncrement(final int inc) {
        runOnUiThread(() -> {
            ProgressDialogState currentState = progressViewModel.getUiState().getValue();
            if (currentState != null && currentState.isVisible) {
                if (inc < 0) { // Indeterminate signal
                    progressViewModel.updateProgress(currentState.currentProgress, "Processing...", true);
                } else {
                    int newProgress = currentState.currentProgress + inc;
                    String message = "Adding items: " + newProgress + "/" + currentState.maxProgress;
                    if (currentState.isIndeterminate || currentState.maxProgress <= 0) {
                        message = "Processing...";
                    }
                    progressViewModel.updateProgress(newProgress, message, false);
                }
            }
        });
    }

    @Override
    public void onWallpaperLoadingFinished(final int status, final String msg) {
        // This callback could be from a background thread.
        // UI updates (ViewModel, Dialogs, finish()) must be on the main thread.
        runOnUiThread(() -> {
            progressViewModel.hideDialog(); // This will trigger the observer to dismiss the DialogFragment

            store.saveToPrefs(); // Save preferences after loading is done

            if (status != IWallpaperAddedListener.SUCCESS) { // Assuming SUCCESS is a defined constant
                new AlertDialog.Builder(IncomingIntentActivity.this)
                        .setTitle("Error(s) loading images")
                        .setMessage((msg != null) ? msg : "Unknown error.")
                        .setCancelable(false) // Don't let user dismiss error easily if activity should finish
                        .setPositiveButton("Got it", (dialog2, which2) -> {
                            dialog2.dismiss();
                            setResult(Activity.RESULT_CANCELED); // Or a custom result code for error
                            finishAfterTransition();
                        })
                        .show();
            } else {
                setResult(Activity.RESULT_OK);
                finishAfterTransition();
            }
        });
        // Listener is removed in onDestroy
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Crucial to remove the listener to prevent leaks if the activity is destroyed
        // while WallpaperManager is still processing or holds a reference.
        WallpaperManager.getInstance().removeWallpaperAddedListener(this);
    }
}
