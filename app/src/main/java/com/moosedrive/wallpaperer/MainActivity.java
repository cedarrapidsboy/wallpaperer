package com.moosedrive.wallpaperer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.Data;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.ListPreloader;
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader;
import com.bumptech.glide.util.FixedPreloadSizeProvider;
import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.targets.ViewTarget;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.moosedrive.wallpaperer.data.ImageObject;
import com.moosedrive.wallpaperer.data.ImageStore;
import com.moosedrive.wallpaperer.utils.BackgroundExecutor;
import com.moosedrive.wallpaperer.utils.PreferenceHelper;
import com.moosedrive.wallpaperer.utils.StorageUtils;
import com.moosedrive.wallpaperer.wallpaper.IWallpaperAddedListener;
import com.moosedrive.wallpaperer.wallpaper.IWallpaperSetListener;
import com.moosedrive.wallpaperer.wallpaper.WallpaperManager;
import com.moosedrive.wallpaperer.wallpaper.WallpaperWorker;
import com.stfalcon.imageviewer.StfalconImageViewer;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;

import me.zhanghai.android.fastscroll.FastScrollerBuilder;

/**
 * The type Main activity.
 */
public class MainActivity extends AppCompatActivity
        implements IWallpaperSetListener,
        ImageStore.ImageStoreListener,
        RVAdapter.ItemClickListener,
        SharedPreferences.OnSharedPreferenceChangeListener,
        IWallpaperAddedListener
{

    public static final String CHANNEL_ID = "notifications.wallpaperer";
    final boolean isLoading = false;
    RecyclerView rv;
    ImageStore store;
    RVAdapter adapter;
    ConstraintLayout constraintLayout;
    RecyclerViewPreloader<ImageObject> preloader;
    private ProgressDialogFragment loadingDialog;
    private Context context;
    private SwitchMaterial toggler;
    private TimerArc timerArc;
    private ItemTouchHelper itemDragHelper;
    private ActivityResultLauncher<Intent> settingsResultLauncher;

    private ActivityResultLauncher<PickVisualMediaRequest> pickMultipleMediaLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        createNotificationChannel();
        super.onCreate(savedInstanceState);
        context = this;
        store = ImageStore.getInstance(getApplicationContext());

        setContentView(R.layout.activity_main);
        PreferenceManager.setDefaultValues(context, R.xml.root_preferences, false);

        //Add the toolbar / actionbar
        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);

        //Get layout for snackbars (e.g., item removal notification)
        constraintLayout = findViewById(R.id.constraint_layout);
        //Setup the RecyclerView for all the cards
        setupRecyclerView();
        if (store.size() == 0)
            store.updateFromPrefs(context);
        //Image Chooser
        //registerImageChooser();
        registerPhotoPickerLaunchers();

        settingsResultLauncher = getSettingsResultLauncher();
        //Set onclick listener for the add image(s) button
        View fab = findViewById(R.id.floatingActionButton);
        fab.setOnClickListener(v -> openImageChooser());
        View flb = findViewById(R.id.floatingLocateButton);
        flb.setOnClickListener(v -> {
            if (rv != null && store != null && store.getActivePos() > -1)
                rv.scrollToPosition(store.getActivePos());
        });
        rv.setOnScrollChangeListener((view, i, i1, i2, i3) -> setLocaterButtonVisibility());

        //Create swipe action for items
        enableSwipeToDeleteAndUndo();
        PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(this);
        boolean firstTime = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(getString(R.string.first_time), true);

        if (firstTime && store.size() == 0) {
            runFirstTimeShowcase();
        }

        timerArc = findViewById(R.id.timerArc);
        if (PreferenceHelper.isActive(context)) {
            try {
                if (WorkManager.getInstance(context).getWorkInfosByTag(getString(R.string.work_random_wallpaper_id)).get().isEmpty()) {
                    WallpaperWorker.scheduleRandomWallpaper(context);
                }
            } catch (ExecutionException | InterruptedException e) {
                Log.e("MainActivity", "onCreate: Error scheduling wallpaper change",e);
            }
            timerArc.start();
        }
        setLocaterButtonVisibility();
    }

    private void registerPhotoPickerLaunchers() {

        // Launcher for selecting multiple media items.
        // You can set a max limit for items if desired using PickMultipleVisualMedia(maxItems)
        pickMultipleMediaLauncher = registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(), uris -> {
            if (uris != null && !uris.isEmpty()) {
                Log.d("PhotoPicker", "Multiple Selected URIs: " + uris.size());
                StorageUtils.CleanUpOrphans(getApplicationContext(), getFilesDir().getAbsolutePath()); // Keep if necessary
                HashSet<Uri> sources = new HashSet<>(uris); // Convert List<Uri> to HashSet<Uri>
                WallpaperManager.getInstance().addWallpaperAddedListener(this);
                WallpaperManager.getInstance().addWallpapers(this, sources, ImageStore.getInstance(getApplicationContext()));
            } else {
                Log.d("PhotoPicker", "No media selected (multiple) or operation cancelled");
            }
        });
    }

    private boolean inForeground = false;
    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onResume() {
        super.onResume();
        WallpaperManager.getInstance().addWallpaperSetListener(this);
        runOnUiThread(()->adapter.notifyDataSetChanged());
        inForeground = true;
    }

    private void runFirstTimeShowcase() {
        ShowcaseView sv = new ShowcaseView.Builder(this)
                .setTarget(new ViewTarget(R.id.floatingActionButton, this))
                .setContentTitle(getString(R.string.showcase_add_images))
                .setContentText(getString(R.string.showcase_click_add))
                .build();
        sv.setButtonText(getString(R.string.showcase_button_text));
        sv.setStyle(R.style.CustomShowcaseTheme3);

        RelativeLayout.LayoutParams lps = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lps.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lps.addRule(RelativeLayout.CENTER_HORIZONTAL);
        lps.height = (int) getResources().getDimension(com.intuit.sdp.R.dimen._32sdp);
        lps.width = (int) getResources().getDimension(com.intuit.sdp.R.dimen._64sdp);
        int margin = (int) getResources().getDimension(com.intuit.sdp.R.dimen._16sdp);
        lps.setMargins(margin, margin, margin, margin);
        sv.setButtonPosition(lps);

        sv.overrideButtonClick(new View.OnClickListener() {
            int count = 0;

            @Override
            public void onClick(View v) {
                count++;
                switch (count) {
                    case 1:
                        sv.setTarget(new ViewTarget(toggler));
                        sv.setContentTitle(getString(R.string.showcase_changer));
                        sv.setContentText(getString(R.string.showcase_changer_text));
                        sv.setButtonText(getString(R.string.showcase_button_text));
                        break;
                    case 2:
                        sv.setTarget(new ViewTarget(findViewById(R.id.next_wallpaper)));
                        sv.setContentTitle(getString(R.string.showcase_random));
                        sv.setContentText(getString(R.string.showcase_random_text));
                        sv.setButtonText(getString(R.string.showcase_button_text));
                        break;
                    case 3:
                        sv.setShowcaseX(Resources.getSystem().getDisplayMetrics().widthPixels / 2);
                        sv.setShowcaseY(Resources.getSystem().getDisplayMetrics().heightPixels / 2);
                        sv.setContentTitle(getString(R.string.showcase_manage));
                        sv.setContentText(getString(R.string.showcase_manage_text));
                        sv.setButtonText(getString(R.string.showcase_done));
                        break;


                    default:
                        sv.hide();
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                        SharedPreferences.Editor edit = prefs.edit();
                        edit.putBoolean(getString(R.string.first_time), false);
                        edit.apply();
                }
            }
        });

    }

    @SuppressLint("NotifyDataSetChanged")
    private void setupRecyclerView() {
        rv = findViewById(R.id.rv);
        adapter = new RVAdapter(context, store, PreferenceHelper.getGridLayoutColumns(context));
        int width = Resources.getSystem().getDisplayMetrics().widthPixels;
        int height = Resources.getSystem().getDisplayMetrics().heightPixels;
        int columns = width / RVAdapter.getCardSize(context, PreferenceHelper.getGridLayoutColumns(context));
        int rows = height / RVAdapter.getCardSize(context, PreferenceHelper.getGridLayoutColumns(context));
        rv.setLayoutManager(
                new GridLayoutManager(context
                        , columns > 0 ? columns : 1));

        adapter.setHasStableIds(true);
        rv.setAdapter(adapter);
        store.addListener(this);
        adapter.setClickListener(this);
        SwipeRefreshLayout swipeLayout = findViewById(R.id.swiperefresh);

        swipeLayout.setOnRefreshListener(() -> {
            //clean up missing images
            //clear Glide cache
            //reload from preferences
            BackgroundExecutor.getExecutor().execute(() -> {
                runOnUiThread(() -> {
                    //must be run on main thread
                    final Glide gInstance = Glide.get(this);
                    //must be run on background thread
                    BackgroundExecutor.getExecutor().execute(gInstance::clearDiskCache);
                    //must be run on main thread
                    gInstance.clearMemory();
                });
                for (ImageObject obj : store.getImageObjectArray())
                    if (!StorageUtils.sourceExists(this, obj.getUri()))
                        store.delImageObject(obj.getId());
                //Refresh the whole list
                runOnUiThread(() -> adapter.notifyDataSetChanged());
                //Save after refresh -- otherwise data will be saved onPause()
                store.saveToPrefs();
                swipeLayout.setRefreshing(false);
            });
        });
        new FastScrollerBuilder(rv).useMd2Style().build();
        if (preloader == null) {
            ListPreloader.PreloadSizeProvider<ImageObject> sizeProvider = new FixedPreloadSizeProvider<>(RVAdapter.getCardSize(context, PreferenceHelper.getGridLayoutColumns(context)), RVAdapter.getCardSize(context, PreferenceHelper.getGridLayoutColumns(context)));
            //Pre-loader loads images into the Glide memory cache while they are still off screen
            preloader = new RecyclerViewPreloader<>(Glide.with(context), adapter, sizeProvider, rows * columns /*maxPreload*/);
            rv.addOnScrollListener(preloader);
        }
    }

    /**
     * Sets the visibility of the locate button based on the active image object.
     *
     * This method retrieves the active image object from the ImageStore and checks if it is effectively visible.
     * If the active image is visible, the locate button's visibility is set to View.INVISIBLE; otherwise, it is set to View.VISIBLE.
     */
    private void setLocaterButtonVisibility() {
        int activePosition = store.getActivePos();
        if (activePosition > -1) {
            ImageObject imageObject = store.getImageObject(activePosition);
            if (imageObject != null) {
                boolean isVisible = adapter.isVisible(store.getImageObject(store.getActivePos()));
                View locateButton = findViewById(R.id.floatingLocateButton);
                if (locateButton != null) {
                    locateButton.setVisibility(isVisible ? View.INVISIBLE : View.VISIBLE);
                    updateLocateButtonToActiveImage();
                }
            }
        }
    }
    private Uri lastSetLocateButtonURI = null;

    /**
     * Checks if a view and its ancestors are effectively visible.
     *
     * A view is considered effectively visible if it is not null, its own visibility is
     * set to {@link View#VISIBLE}, and all of its parent views (up the hierarchy)
     * have their own visibility set to {@link View#VISIBLE}.
     *
     * @param view The view to check for effective visibility.
     * @return True if the view and its ancestors are effectively visible, false otherwise.
     */
    public boolean isEffectivelyVisible(View view) {
        if (view == null) {
            return false;
        }

        // Check the view's own visibility
        if (view.getVisibility() != View.VISIBLE) {
            return false;
        }

        // Recursively check parent's visibility
        ViewParent parent = view.getParent();
        if (parent instanceof View) {
            return isEffectivelyVisible((View) parent);
        } else {
            // If no more parents, the view is effectively visible
            return true;
        }
    }
    /**
     * Updates the locate button to display the active image from ImageStore.
     *
     * This method retrieves the active image object from ImageStore and updates the
     * locate button's image if it is effectively visible. If the active image has changed,
     * the button's image is updated; otherwise, no change is made.
     *
     * @see #isEffectivelyVisible(View)
     * @see ImageObject#getThumbUri(Context)
     */
    private void updateLocateButtonToActiveImage() {
        // Get the locate button
        View locateButton = findViewById(R.id.locateButtonImageView);

        if (locateButton != null && isEffectivelyVisible(locateButton)) {
            // Get the active image object from ImageStore
            int activePosition = store.getActivePos();
            if (activePosition >= 0) {
                ImageObject activeImage = store.getImageObject(activePosition);
                if (activeImage != null) {
                    // Update the button's image to the active image
                    // Assuming you're using a FloatingActionButton or ImageView
                   if (locateButton instanceof ImageView) {
                        ImageView fab = (ImageView) locateButton;
                        Uri activeImageURI = activeImage.getThumbUri(getBaseContext());
                        if (!activeImageURI.equals(lastSetLocateButtonURI)) {
                            // If you want to set a specific drawable based on the active image
                            // You might need to customize this part depending on how you store or reference images
                            fab.setImageURI(activeImageURI);
                            lastSetLocateButtonURI=activeImageURI;
                        }// example placeholder
                    }
                }
            }
        }
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_goto).setVisible(!store.getActiveId().isEmpty());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menuactions, menu);
        //Special handling for switch control (onOptionsItemSelected doesn't work)
        try {
            toggler = Objects.requireNonNull(menu.findItem(R.id.app_bar_switch).getActionView()).findViewById(R.id.switch_control);
            toggler.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && store.size() == 0) {
                    toggler.setChecked(false);
                    Snackbar
                            .make(constraintLayout, getString(R.string.toast_add_an_image_toggle), Snackbar.LENGTH_LONG)
                            .setBackgroundTint(getColor(androidx.cardview.R.color.cardview_dark_background))
                            .setTextColor(getColor(R.color.white))
                            .show();
                    processToggle(false);
                } else {
                    processToggle(isChecked);
                }
            });
            //Start changing the wallpaper
            initializeWallpaperToggle();
        } catch (NullPointerException e) {
            Log.e("MainActivity", "onCreateOptionsMenu: NullPointerException: " + e.getMessage(),e);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onPause() {
        store.saveToPrefs();
        inForeground = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        store.removeListener(this);
        rv.removeOnScrollListener(preloader);
        WallpaperManager.getInstance().removeWallpaperSetListener(this);
        PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(this);
        if (itemDragHelper != null)
            itemDragHelper.attachToRecyclerView(null);
        store.saveToPrefs();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        CharSequence name = "wallpaperer_notifications";
        String description = "Notifications sent by the Wallpaperer app.";
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    private ActivityResultLauncher<Intent> getSettingsResultLauncher(){
        return registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getData() != null) {
                        // Create an explicit intent for an Activity in your app
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
                        if (result.getResultCode() == SettingsActivity.IMPORT_RESULT_CODE) {
                            handleImportResult(result.getData(), pendingIntent);
                        } else if (result.getResultCode() == SettingsActivity.EXPORT_RESULT_CODE){

                            Intent exploreIntent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
                            if (exploreIntent.resolveActivityInfo(getPackageManager(), 0) != null)
                                pendingIntent = PendingIntent.getActivity(this, 0, exploreIntent, PendingIntent.FLAG_IMMUTABLE);

                            handleExportResult(result.getData(), pendingIntent);
                        }
                    }
                });
    }

   private void handleExportResult(Intent result, PendingIntent pendingIntent) {


        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.vector_export_24)
                .setContentTitle("Wallpaperer Export")
                .setContentText("Exporting images...")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                // Set the intent that will fire when the user taps the notification
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true);

        Serializable serialUUID = result.getSerializableExtra(SettingsActivity.EXPORT_UUID, Serializable.class);
        if (serialUUID != null) {
            int notificationId = ThreadLocalRandom.current().nextInt(1000);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            WorkManager.getInstance(getApplicationContext())
                    .getWorkInfoByIdLiveData((UUID) serialUUID)
                    .observe(this, workInfo -> {
                        if (workInfo != null) {
                            builder.setProgress(0,0,true);
                            Data progress = workInfo.getProgress();
                            if (progress.getString(StorageUtils.ExportBackupWorker.STATUS_MESSAGE) != null)
                                builder.setContentText(progress.getString(StorageUtils.ExportBackupWorker.STATUS_MESSAGE));
                            if (progress.getInt(StorageUtils.ExportBackupWorker.PROGRESS_MAX, -1) > -1)
                                builder.setProgress(progress.getInt(StorageUtils.ExportBackupWorker.PROGRESS_MAX, 0),
                                        progress.getInt(StorageUtils.ExportBackupWorker.PROGRESS_CURRRENT, 0),
                                        false);
                            if (workInfo.getState().equals(WorkInfo.State.SUCCEEDED)) {
                                int status = workInfo.getOutputData().getInt(StorageUtils.RESULT_CODE, 0);
                                builder.setContentText("Export finished."
                                        + ((status == StorageUtils.EXPORT_NO_IMAGES)?" No images to export.":""))
                                        .setProgress(0,
                                                0,
                                                false);
                            }
                            else if (workInfo.getState().equals(WorkInfo.State.FAILED))
                                builder.setProgress(0,0,false)
                                        .setContentText("Export failed. " + workInfo.getOutputData().getString(StorageUtils.ImportBackupWorker.STATUS_MESSAGE));
                            if (ActivityCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) {
                                notificationManager.notify(notificationId, builder.build());
                            }

                        }
                    });
        }
    }

    private void handleImportResult(Intent result, PendingIntent pendingIntent) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.vector_restore_24)
                .setContentTitle("Wallpaperer Import")
                .setContentText("Importing images...")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                // Set the intent that will fire when the user taps the notification
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true);

        Serializable serialUUID = result.getSerializableExtra(SettingsActivity.IMPORT_UUID, Serializable.class);
        if (serialUUID != null) {
            int notificationId = ThreadLocalRandom.current().nextInt(1000);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            WorkManager.getInstance(getApplicationContext())
                    .getWorkInfoByIdLiveData((UUID) serialUUID)
                    .observe(this, workInfo -> {
                        if (workInfo != null) {
                            builder.setProgress(0,0,true);
                            Data progress = workInfo.getProgress();
                            if (progress.getString(StorageUtils.ImportBackupWorker.STATUS_MESSAGE) != null)
                                builder.setContentText(progress.getString(StorageUtils.ImportBackupWorker.STATUS_MESSAGE));
                            if (progress.getInt(StorageUtils.ImportBackupWorker.PROGRESS_MAX, -1) > -1)
                                builder.setProgress(progress.getInt(StorageUtils.ImportBackupWorker.PROGRESS_MAX, 0),
                                                progress.getInt(StorageUtils.ImportBackupWorker.PROGRESS_CURRRENT, 0),
                                                false);
                            if (workInfo.getState().equals(WorkInfo.State.SUCCEEDED))
                                builder.setContentText("Import finished.")
                                        .setProgress(0,
                                                0,
                                                false);
                            else if (workInfo.getState().equals(WorkInfo.State.FAILED))
                                builder.setProgress(0,0,false)
                                        .setContentText("Import failed. " + workInfo.getOutputData().getString(StorageUtils.ImportBackupWorker.STATUS_MESSAGE));
                            if (ActivityCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) {
                                notificationManager.notify(notificationId, builder.build());
                            }

                        }
                    });
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case (R.id.remove_all):
                deleteAll();
                return true;
            case (R.id.menu_settings):
                intent = new Intent(this, SettingsActivity.class);
                settingsResultLauncher.launch(intent);
                return true;
            case (R.id.menu_about):
                intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                return true;
            case (R.id.next_wallpaper):
                View itemView = findViewById(R.id.next_wallpaper);
                if (itemView != null)
                    if (store.size() > 0)
                        itemView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.anim_random_wallpaper));
                    else
                        itemView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.anim_random_wallpaper_bad));
                WallpaperManager.getInstance().setSingleWallpaper(this, null);
                return true;
            case (R.id.sort):
                View sortOption = findViewById(R.id.sort);
                PopupMenu popupMenu = new PopupMenu(context, sortOption);
                popupMenu.getMenuInflater().inflate(R.menu.sort_menu, popupMenu.getMenu());
                // Disable copy action if the custom list is active -- no need to copy to itself
                popupMenu.getMenu().findItem(R.id.copy_list).setVisible(store.getSortCriteria() != ImageStore.SORT_BY_CUSTOM);
                if (store.getSortCriteria() + 1 < popupMenu.getMenu().size())
                    popupMenu.getMenu().getItem(store.getSortCriteria() + 1).setChecked(true);

                popupMenu.setOnMenuItemClickListener(menuItem -> {
                    switch (menuItem.getItemId()) {
                        case (R.id.custom):
                            store.setSortCriteria(ImageStore.SORT_BY_CUSTOM);
                            enableSwipeToDeleteAndUndo();
                            break;
                        case (R.id.date):
                            store.setSortCriteria(ImageStore.SORT_BY_DATE);
                            enableSwipeToDeleteAndUndo();
                            break;
                        case (R.id.name):
                            store.setSortCriteria(ImageStore.SORT_BY_NAME);
                            enableSwipeToDeleteAndUndo();
                            break;
                        case (R.id.size):
                            store.setSortCriteria(ImageStore.SORT_BY_SIZE);
                            enableSwipeToDeleteAndUndo();
                            break;
                        case (R.id.shuffle):
                            new AlertDialog.Builder(this)
                                    .setTitle(getString(R.string.shuffle_confirmation_title))
                                    .setMessage(getString(R.string.shuffle_confirmation, getString(R.string.custom)))
                                    .setCancelable(false)
                                    .setNegativeButton(getString(R.string.dialog_button_no), (dialog, which) -> {
                                        dialog.dismiss();
                                        setResult(Activity.RESULT_CANCELED);
                                    })
                                    .setPositiveButton(R.string.dialog_button_shuffle_yes, (dialog, which) -> {
                                        store.setSortCriteria(ImageStore.SORT_BY_CUSTOM);
                                        store.shuffle();
                                        enableSwipeToDeleteAndUndo();
                                        dialog.dismiss();
                                        runOnUiThread(() -> rv.scrollToPosition(store.getActivePos()));
                                        setResult(Activity.RESULT_OK);
                                    }).show();
                            break;
                        case (R.id.copy_list):
                            new AlertDialog.Builder(this)
                                    .setTitle("Copy this image order?")
                                    .setMessage(getString(R.string.copy_confirmation, getString(R.string.custom)))
                                    .setCancelable(false)
                                    .setNegativeButton(getString(R.string.dialog_button_no), (dialog, which) -> {
                                        dialog.dismiss();
                                        setResult(Activity.RESULT_CANCELED);
                                    })
                                    .setPositiveButton(R.string.copy_list_yes, (dialog, which) -> {
                                        store.replace(Arrays.asList(store.getImageObjectArray()));
                                        store.setSortCriteria(ImageStore.SORT_BY_CUSTOM);
                                        enableSwipeToDeleteAndUndo();
                                        dialog.dismiss();
                                        runOnUiThread(() -> rv.scrollToPosition(store.getActivePos()));
                                        setResult(Activity.RESULT_OK);
                                    }).show();
                            break;
                        default:
                            return false;
                    }
                    return true;
                });
                popupMenu.show();
                return true;
            case (R.id.menu_goto):
                runOnUiThread(() -> rv.scrollToPosition(store.getActivePos()));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    /**
     * Delete all images from view and from storage. Original source (from the add button)
     * is not deleted.
     */
    @SuppressLint("NotifyDataSetChanged")
    public void deleteAll() {
        if (store.size() == 0) {
            Snackbar.make(constraintLayout, R.string.msg_no_images_to_action, Snackbar.LENGTH_LONG)
                    .setBackgroundTint(getColor(androidx.cardview.R.color.cardview_dark_background))
                    .setTextColor(getColor(R.color.white))
                    .show();
        } else {
            new AlertDialog.Builder(context)
                    .setTitle(getString(R.string.dialog_title_delete_all))
                    .setMessage(getString(R.string.dialog_msg_delete_all))
                    .setNegativeButton(getString(R.string.dialog_button_no), (dialog, which) -> dialog.dismiss())
                    .setPositiveButton(getString(R.string.dialog_button_yes_delete_all), (dialog, which) -> {
                        dialog.dismiss();
                        store.clear(false);
                        toggler.setChecked(false);
                        StorageUtils.CleanUpOrphans(getApplicationContext(), getBaseContext().getFilesDir().getPath());
                        //adapter.notifyDataSetChanged();
                    })
                    .show();
            setLocaterButtonVisibility();
        }
    }

    /**
     * Initialize wallpaper toggle.
     */
    public void initializeWallpaperToggle() {
        if (PreferenceHelper.isActive(context)) {
            if (!toggler.isChecked())
                toggler.setChecked(true);
        } else
            toggler.setChecked(false);

    }

    /**
     * Process toggle.
     *
     * @param isChecked the is checked
     */
    public void processToggle(boolean isChecked) {
        if (isChecked) {
            if (!PreferenceHelper.isActive(context)) {
                WallpaperWorker.scheduleRandomWallpaper(context);
                PreferenceHelper.setActive(context, true);
                Toast.makeText(context,
                        getString(R.string.toast_changer_active),
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            WorkManager.getInstance(context)
                    .cancelAllWorkByTag(context.getString(R.string.work_random_wallpaper_id));
            PreferenceHelper.setActive(context, false);
            timerArc.stop();
        }
    }

    /**
     * Open image chooser.
     */
    public void openImageChooser() {

        // Launch the Photo Picker for multiple image selection.
        // You can choose to launch pickSingleMediaLauncher if you only want single selection.
        // Or provide UI options for the user to pick single vs multiple.
        if (pickMultipleMediaLauncher != null) {
            pickMultipleMediaLauncher.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE) // Or .ImageAndVideo.INSTANCE if you want videos too
                    .build());
        } else {
            Log.e("MainActivity", "pickMultipleMediaLauncher is not initialized.");
            // Fallback or error message if the launcher isn't ready
            Toast.makeText(context, "Error opening image picker.", Toast.LENGTH_SHORT).show();
        }
    }

    private void enableSwipeToDeleteAndUndo() {
        ItemMoveCallback itemMoveCallback = new ItemMoveCallback(rv) {
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                final int position = viewHolder.getAbsoluteAdapterPosition();
                final ImageObject item = adapter.getData().get(position);
                final int refPos = store.getReferencePosition(item.getId());
                boolean toggled = false;
                boolean wasActiveWallpaper = store.getActiveId().equals(item.getId());
                store.delImageObject(item.getId());
                //adapter.removeItem(position);
                if (store.size() == 0) {
                    toggler.setChecked(false);
                    toggled = true;
                }
                invalidateOptionsMenu();
                Snackbar snackbar = Snackbar
                        .make(constraintLayout, getString(R.string.msg_swipe_item_removed), Snackbar.LENGTH_LONG);
                final boolean fToggled = toggled;
                snackbar.setAction(getString(R.string.snack_action_undo), view -> {

                    store.addImageObject(item, refPos);
                    if (wasActiveWallpaper) {
                        store.setActive(item.getId());
                    }
                    //adapter.notifyItemInserted(position);
                    if (fToggled)
                        toggler.setChecked(true);
                    invalidateOptionsMenu();
                });
                snackbar.addCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar snackbar, int event) {
                        if (event != Snackbar.Callback.DISMISS_EVENT_ACTION) {
                            StorageUtils.cleanUpImage(context.getFilesDir().getAbsolutePath(), item);
                        }

                    }

                });

                snackbar.setActionTextColor(Color.YELLOW)
                        .setBackgroundTint(getColor(androidx.cardview.R.color.cardview_dark_background))
                        .setTextColor(getColor(R.color.white))
                        .show();

            }
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                if (store.getSortCriteria() == ImageStore.SORT_BY_CUSTOM) {
                    int fromPosition = viewHolder.getBindingAdapterPosition();
                    int toPosition = target.getBindingAdapterPosition();
                    store.moveImageObject(store.getImageObject(fromPosition), toPosition);
                    return true;
                }
                return false;
            }
        };
        itemDragHelper = new ItemTouchHelper(itemMoveCallback);
        itemDragHelper.attachToRecyclerView(rv);
    }

    @Override
    public void onSetWpClick(int position) {
        invalidateOptionsMenu();
        WallpaperManager.getInstance().setSingleWallpaper(this, store.getImageObject(position).getId());
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key != null) {
            if (key.equals(getResources().getString(R.string.preference_columns)) && rv != null) {
                setupRecyclerView();
            } else if (key.equals(getResources().getString(R.string.preference_idle))) {
                if (sharedPreferences.getBoolean(key, false)) {
                    if (PreferenceHelper.isActive(this)) {
                        //reschedule the job since we cannot change existing constraints
                        WallpaperWorker.scheduleRandomWallpaper(context);
                    }
                }
            } else if (key.equals(getString(R.string.preference_time_delay))) {
                if (PreferenceHelper.isActive(this)) {
                    WallpaperWorker.scheduleRandomWallpaper(context);
                }
            } else if (!isLoading && key.equals(getString(R.string.preference_card_stats)))
                runOnUiThread(() -> adapter.notifyItemRangeChanged(0, store.getImageObjectArray().length));
            else if (key.equals(getString(R.string.preference_worker_last_queue))) {
                if (PreferenceHelper.isActive(context))
                    timerArc.start();
            } else if (key.equals("isActive")) {
                if (PreferenceHelper.isActive(context))
                    timerArc.start();
                else {
                    timerArc.stop();
                    WorkManager.getInstance(context).cancelAllWorkByTag(context.getString(R.string.work_random_wallpaper_id));
                }
            } else if (key.equals(getString(R.string.last_wallpaper))) {
                String id = sharedPreferences.getString(getString(R.string.last_wallpaper), "");
                //Catch any code that is changing the last wallpaper preference
                // without updating the store.
                if (!id.equals(store.getActiveId())) {
                    store.setActive(sharedPreferences.getString(getString(R.string.last_wallpaper), ""));
                    invalidateOptionsMenu();
                }
            }
        }
    }

    @Override
    public void onImageClick(int pos, View view) {
        new StfalconImageViewer.Builder<>(this, store.getImageObjectArray(), (imageView, image) -> Glide
                .with(context)
                .load(image.getUri())
                .fitCenter()
                .into(imageView))
                .withTransitionFrom((ImageView) view)
                .withStartPosition(pos)
                .show();
    }

    @Override
    public void onWallpaperSetNotFound(String id) {
        adapter.removeItem(store.getPosition(id));
        Toast.makeText(context,
                R.string.set_wallpaper_missing_image,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onWallpaperSetEmpty() {
        Snackbar.make(constraintLayout, R.string.set_wallpaper_no_images, Snackbar.LENGTH_LONG)
                .setBackgroundTint(getColor(androidx.cardview.R.color.cardview_dark_background))
                .setTextColor(getColor(R.color.white))
                .show();
    }

    @Override
    public void onWallpaperSetSuccess() {
        Toast.makeText(context,
                getResources().getText(R.string.toast_wallpaper_changing),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onWallpaperLoadingStarted(int size, String message) {
        runOnUiThread(() -> {
            loadingDialog = ProgressDialogFragment.newInstance(size);
            loadingDialog.showNow(getSupportFragmentManager(), "add_progress");
            if (message != null){
                loadingDialog.setMessage(message);
            }
        });
    }

    @Override
    public void onWallpaperLoadingIncrement(int inc) {
        runOnUiThread(() -> {
            if (inc < 0)
                loadingDialog.setIndeterminate(true);
            else {
                loadingDialog.setIndeterminate(false);
                loadingDialog.incrementProgressBy(inc);
            }
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onWallpaperLoadingFinished(int status, String msg) {
        if (loadingDialog != null)
            runOnUiThread(() -> loadingDialog.dismiss());
        WallpaperManager.getInstance().removeWallpaperAddedListener(this);
        store.saveToPrefs();
        invalidateOptionsMenu();
        if (status != IWallpaperAddedListener.SUCCESS) {
            new Handler(Looper.getMainLooper()).post(() -> new AlertDialog.Builder(this)
                    .setTitle("Error(s) loading images")
                    .setMessage((msg != null) ? msg : "Unknown error.")
                    .setPositiveButton("Got it", (dialog2, which2) -> dialog2.dismiss())
                    .show());
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onSortCriteriaChanged(int prevSortCriteria) {
        if (prevSortCriteria != store.getSortCriteria())
            runOnUiThread(() -> adapter.notifyDataSetChanged());
    }

    @Override
    public void onDelete(ImageObject obj, int lastPos) {
        if (inForeground)
            runOnUiThread(() -> adapter.notifyItemRemoved(lastPos));
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onShuffle() {
        runOnUiThread(()->adapter.notifyDataSetChanged());
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onClear() {
        runOnUiThread(()->adapter.notifyDataSetChanged());
    }

    @Override
    public void onMove(int oldPos, int newPos) {
        runOnUiThread(()->adapter.notifyItemMoved(oldPos, newPos));
    }

    @Override
    public void onSetActive(ImageObject activeObj, ImageObject prevObj) {
        runOnUiThread(this::setLocaterButtonVisibility);
        int prevPos = (prevObj != null)?store.getPosition(prevObj.getId()):-1;
            int activePos = (activeObj != null)?store.getPosition(activeObj.getId()):-1;
            if (prevPos > -1)
                runOnUiThread(()->adapter.notifyItemChanged(prevPos));
            if (activePos > -1)
                runOnUiThread(()->adapter.notifyItemChanged(activePos));
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onAdd(ImageObject obj, int pos) {
        // notifyDataSetChanged needed to avoid IOOBE on recyclerview
        //   that happens on rapid adds (due to predictive animation)
        if (inForeground)
            runOnUiThread(()->adapter.notifyDataSetChanged());
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onReplace() {
        if (inForeground)
            runOnUiThread(()->adapter.notifyDataSetChanged());
    }


}