package com.moosedrive.wallpaperer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.provider.DocumentsContract;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.WorkManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.ListPreloader;
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader;
import com.bumptech.glide.util.FixedPreloadSizeProvider;
import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.targets.ViewTarget;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.stfalcon.imageviewer.StfalconImageViewer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import me.zhanghai.android.fastscroll.FastScrollerBuilder;

/**
 * The type Main activity.
 */
public class MainActivity extends AppCompatActivity implements ItemClickListener, SharedPreferences.OnSharedPreferenceChangeListener {

    public static final int MINIMUM_REQUIRED_FREE_SPACE = 734003200;
    public CountDownLatch loadingDoneSignal;
    public HashSet<String> loadingErrors;
    RecyclerView rv;
    ImageStore images;
    RVAdapter adapter;
    ConstraintLayout constraintLayout;
    boolean isloading = false;
    private ActivityResultLauncher<Intent> someActivityResultLauncher;
    private Context context;
    private SwitchMaterial toggler;
    private int lastRecordedSize;
    private TimerArc timerArc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);
        context = this;
        images = ImageStore.getInstance();
        images.updateFromPrefs(context);
        lastRecordedSize = images.size();

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

        //The image chooser dialog
        someActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new chooserActivityResult(this,
                        this.getBaseContext()));

        //Set onclick listener for the add image(s) button
        View fab = findViewById(R.id.floatingActionButton);
        fab.setOnClickListener(v -> openImageChooser());

        //Create swipe action for items
        enableSwipeToDeleteAndUndo();
        PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(this);
        boolean firstTime = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(getString(R.string.first_time), true);

        if (firstTime && images.size() == 0) {
            runFirstTimeShowcase();
        }

        timerArc = findViewById(R.id.timerArc);
        if (PreferenceHelper.isActive(context)) {
            try {
                if (WorkManager.getInstance(context).getWorkInfosByTag(getString(R.string.work_random_wallpaper_id)).get().size() == 0){
                    WallpaperWorker.scheduleRandomWallpaper(context);
                }
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
            timerArc.start();
        }
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
                        SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
                        SharedPreferences.Editor edit = prefs.edit();
                        edit.putBoolean(getString(R.string.first_time), false);
                        edit.apply();
                }
            }
        });

    }


    private void setupRecyclerView() {
        rv = findViewById(R.id.rv);
        adapter = new RVAdapter(context);
        int width = Math.round(Resources.getSystem().getDisplayMetrics().widthPixels);
        int height = Math.round(Resources.getSystem().getDisplayMetrics().heightPixels);
        int columns = width / RVAdapter.getCardSize(context);
        int rows = height / RVAdapter.getCardSize(context);
        rv.setLayoutManager(
                new GridLayoutManager(context
                        , columns > 0 ? columns : 1));

        adapter.setHasStableIds(true);
        rv.setAdapter(adapter);
        adapter.setClickListener(this);
        new FastScrollerBuilder(rv).useMd2Style().build();
        ListPreloader.PreloadSizeProvider<ImageObject> sizeProvider = new FixedPreloadSizeProvider<>(RVAdapter.getCardSize(context), RVAdapter.getCardSize(context));
        //Pre-loader loads images into the Glide memory cache while they are still off screen
        RecyclerViewPreloader<ImageObject> preloader = new RecyclerViewPreloader<>(Glide.with(context), adapter, sizeProvider, rows * columns /*maxPreload*/);
        rv.addOnScrollListener(preloader);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menuactions, menu);
        //Special handling for switch control (onOptionsItemSelected doesn't work)
        toggler = menu.findItem(R.id.app_bar_switch).getActionView().findViewById(R.id.switch_control);
        toggler.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && images.size() == 0) {
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
        return super.onCreateOptionsMenu(menu);
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
                startActivity(intent);
                return true;
            case (R.id.menu_about):
                intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                return true;
            case (R.id.next_wallpaper):
                View itemView = findViewById(R.id.next_wallpaper);
                if (itemView != null)
                    if (images.size() > 0)
                        itemView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.anim_random_wallpaper));
                    else
                        itemView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.anim_random_wallpaper_bad));
                setSingleWallpaper(null);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Add wallpapers from list of URI's.
     * Loading dialog is displayed and progress bar updated as wallpapers are added.
     *
     * @param sources the sources
     */
    public void addWallpapers(HashSet<Uri> sources) {
        AppCompatActivity activity = this;
        boolean recompress = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(getResources().getString(R.string.preference_recompress), false);
        final LoadingDialog loadingDialog = new LoadingDialog(this);
        loadingErrors = new HashSet<>();
        if (sources.size() > 0) {
            isloading = true;
            loadingDoneSignal = new CountDownLatch(sources.size());
            loadingDialog.startLoadingDialog(sources.size());
            for (Uri uri : sources) {
                Thread t = new Thread(() -> {
                    File fImageStorageFolder = StorageUtils.getStorageFolder(getBaseContext());
                    StatFs stats = new StatFs(fImageStorageFolder.getAbsolutePath());
                    long bytesAvailable = stats.getAvailableBlocksLong() * stats.getBlockSizeLong();
                    if (!fImageStorageFolder.exists() && !fImageStorageFolder.mkdirs())
                        Snackbar.make(constraintLayout, getString(R.string.loading_error_cannot_mkdir), Snackbar.LENGTH_LONG)
                                .setBackgroundTint(getColor(androidx.cardview.R.color.cardview_dark_background))
                                .setTextColor(getColor(R.color.white))
                                .show();
                    else if (bytesAvailable < MINIMUM_REQUIRED_FREE_SPACE)
                        loadingErrors.add(getString(R.string.loading_error_precheck_low_space));
                    else {
                        String hash = StorageUtils.getHash(context, uri);
                        if (hash == null)
                            hash = UUID.randomUUID().toString();
                        if (images.getImageObject(hash) == null) {
                            String name = StorageUtils.getFileAttrib(uri, DocumentsContract.Document.COLUMN_DISPLAY_NAME, context);
                            String type = StorageUtils.getFileAttrib(uri, DocumentsContract.Document.COLUMN_MIME_TYPE, context);
                            if (type.startsWith("image/")) {
                                try {
                                    String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 4);
                                    long size = Long.parseLong(StorageUtils.getFileAttrib(uri, DocumentsContract.Document.COLUMN_SIZE, context));
                                    Uri uCopiedFile = StorageUtils.saveBitmap(context, uri, size, fImageStorageFolder.getPath(), uuid + "_" + name, recompress);
                                    if (recompress) type = "image/jpeg";
                                    size = StorageUtils.getFileSize(uCopiedFile);
                                    try {
                                        ImageObject img = new ImageObject(uCopiedFile, hash, uuid + "_" + name, size, type, new Date());
                                        img.generateThumbnail(context);
                                        img.setColor(img.getColorFromBitmap(context));
                                        activity.runOnUiThread(() -> adapter.addItem(img));
                                        adapter.saveToPrefs();
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
                BackgroundExecutor.getExecutor().execute(t);
            }
            // UI work that waits for the image loading to complete
            BackgroundExecutor.getExecutor().execute(() -> {
                try {
                    loadingDoneSignal.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    isloading = false;
                    activity.runOnUiThread(() -> rv.scrollToPosition(adapter.getItemCount() - 1));
                    loadingDialog.dismissDialog();
                }
            });
        }
    }

    /**
     * Checks if the storage item for the image exists.
     *
     * @param imageId the image id
     * @return the boolean
     */
    public boolean sourceExists(String imageId) {
        boolean exists = false;
        ImageObject img = images.getImageObject(imageId);
        if (img != null) {
            Uri uri = img.getUri();
            ParcelFileDescriptor pfd;
            try {
                pfd = context.
                        getContentResolver().
                        openFileDescriptor(uri, "r");
                exists = true;
                pfd.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return exists;
    }

    /**
     * Sets single wallpaper as soon as possible.
     *
     * @param imgObjectId the img object id
     */
    public void setSingleWallpaper(String imgObjectId) {
        if (images.size() == 0) {
            Snackbar.make(constraintLayout, R.string.set_wallpaper_no_images, Snackbar.LENGTH_LONG)
                    .setBackgroundTint(getColor(androidx.cardview.R.color.cardview_dark_background))
                    .setTextColor(getColor(R.color.white))
                    .show();
        } else {
            if (imgObjectId != null && !sourceExists(imgObjectId)) {
                adapter.removeItem(images.getPosition(imgObjectId));
                //StorageUtils.releasePersistableUriPermission(getBaseContext(), images.getImageObject(imgObjectId).getUri());
                Toast.makeText(context,
                        R.string.set_wallpaper_missing_image,
                        Toast.LENGTH_SHORT).show();
            } else {
                WallpaperWorker.changeWallpaperNow(context, imgObjectId);
                Toast.makeText(context,
                        getResources().getText(R.string.toast_wallpaper_changing),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Delete all images from view and from storage. Original source (from the add button)
     * is not deleted.
     */
    @SuppressLint("NotifyDataSetChanged")
    public void deleteAll() {
        if (images.size() == 0) {
            Snackbar.make(constraintLayout, R.string.msg_no_images_to_action, Snackbar.LENGTH_LONG)
                    .setBackgroundTint(getColor(androidx.cardview.R.color.cardview_dark_background))
                    .setTextColor(getColor(R.color.white))
                    .show();
        } else {
            new AlertDialog.Builder(context)
                    .setTitle(getString(R.string.dialog_title_delete_all))
                    .setMessage(getString(R.string.dialog_msg_delete_all))
                    .setPositiveButton(getString(R.string.dialog_button_no), (dialog, which) -> dialog.dismiss())
                    .setNegativeButton(getString(R.string.dialog_button_yes_delete_all), (dialog, which) -> {
                        dialog.dismiss();
                        images.clear();
                        toggler.setChecked(false);
                        StorageUtils.CleanUpOrphans(getBaseContext().getFilesDir().getPath());
                        adapter.notifyDataSetChanged();
                        images.saveToPrefs(context);
                    })
                    .show();
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
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        someActivityResultLauncher.launch(Intent.createChooser(intent, getString(R.string.intent_chooser_select_images)));
    }

    private void enableSwipeToDeleteAndUndo() {
        SwipeToDeleteCallback swipeToDeleteCallback = new SwipeToDeleteCallback(this) {
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int i) {


                final int position = viewHolder.getAbsoluteAdapterPosition();
                final ImageObject item = adapter.getData().get(position);
                boolean toggled = false;
                adapter.removeItem(position);
                images.delImageObject(item.getId());
                if (images.size() == 0) {
                    toggler.setChecked(false);
                    toggled = true;
                }
                images.saveToPrefs(context);


                Snackbar snackbar = Snackbar
                        .make(constraintLayout, getString(R.string.msg_swipe_item_removed), Snackbar.LENGTH_LONG);
                final boolean fToggled = toggled;
                snackbar.setAction(getString(R.string.snack_action_undo), view -> {

                    adapter.addItem(item, position);
                    if (fToggled)
                        toggler.setChecked(true);
                    rv.scrollToPosition(position);
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
        };

        ItemTouchHelper itemTouchhelper = new ItemTouchHelper(swipeToDeleteCallback);
        itemTouchhelper.attachToRecyclerView(rv);
    }

    @Override
    public void onSetWpClick(int position) {
        setSingleWallpaper(images.getImageObject(position).getId());
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
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
        } else if (!isloading && key.equals(getString(R.string.preference_card_stats)))
            runOnUiThread(() -> adapter.notifyDataSetChanged());
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
        } else if (!isloading && key.equals("sources")) {
            runOnUiThread(() -> adapter.notifyDataSetChanged());
            // Scroll to the newly added images, but don't scroll on delete
            if (images.size() > lastRecordedSize)
                runOnUiThread(() -> rv.scrollToPosition(adapter.getItemCount() - 1));
            lastRecordedSize = images.size();
        }
    }

    @Override
    public void onImageClick(int pos, View view) {
        new StfalconImageViewer.Builder<>(this, images.getImageObjectArray(), (imageView, image) -> Glide
                .with(context)
                .load(image.getUri())
                .fitCenter()
                .into(imageView))
                .withTransitionFrom((ImageView) view)
                .withStartPosition(pos)
                .show();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }


}