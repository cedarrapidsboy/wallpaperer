package com.moosedrive.wallpaperer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
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

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import me.zhanghai.android.fastscroll.FastScrollerBuilder;

/**
 * The type Main activity.
 */
public class MainActivity extends AppCompatActivity implements ImageStore.ImageStoreSortListener, ItemClickListener, SharedPreferences.OnSharedPreferenceChangeListener, ImageStore.WallpaperAddedListener, ActivityResultCallback<ActivityResult> {

    RecyclerView rv;
    ImageStore store;
    RVAdapter adapter;
    ConstraintLayout constraintLayout;
    final boolean isloading = false;
    private ActivityResultLauncher<Intent> someActivityResultLauncher;
    private Context context;
    private SwitchMaterial toggler;
    private TimerArc timerArc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);
        context = this;
        store = ImageStore.getInstance();
        store.updateFromPrefs(context);
        store.addSortListener(this);

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
                this);

        //Set onclick listener for the add image(s) button
        View fab = findViewById(R.id.floatingActionButton);
        fab.setOnClickListener(v -> openImageChooser());

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
                if (WorkManager.getInstance(context).getWorkInfosByTag(getString(R.string.work_random_wallpaper_id)).get().size() == 0) {
                    WallpaperWorker.scheduleRandomWallpaper(context);
                }
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
            timerArc.start();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onResume() {
        super.onResume();
        adapter.notifyDataSetChanged();
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


    @SuppressLint("NotifyDataSetChanged")
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
                store.updateFromPrefs(getApplicationContext());
                for (ImageObject obj : store.getImageObjectArray())
                    if (!StorageUtils.fileExists(obj.getUri()))
                        store.delImageObject(obj.getId());
                store.saveToPrefs(getApplicationContext());
                runOnUiThread(() -> adapter.notifyDataSetChanged());
                swipeLayout.setRefreshing(false);
            });

        });
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
                    if (store.size() > 0)
                        itemView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.anim_random_wallpaper));
                    else
                        itemView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.anim_random_wallpaper_bad));
                setSingleWallpaper(null);
                return true;
            case (R.id.sort):
                View sortOption = findViewById(R.id.sort);
                PopupMenu popupMenu = new PopupMenu(context, sortOption);
                popupMenu.getMenuInflater().inflate(R.menu.sort_menu, popupMenu.getMenu());
                popupMenu.setOnMenuItemClickListener(menuItem -> {
                    switch (menuItem.getItemId()) {
                        case (R.id.original):
                            store.setSortCriteria(ImageStore.SORT_DEFAULT);
                            return true;
                        case (R.id.date):
                            store.setSortCriteria(ImageStore.SORT_BY_DATE);
                            return true;
                        case (R.id.name):
                            store.setSortCriteria(ImageStore.SORT_BY_NAME);
                            return true;
                        case (R.id.size):
                            store.setSortCriteria(ImageStore.SORT_BY_SIZE);
                            return true;
                        default:
                            return false;
                    }
                });
                popupMenu.show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
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
        ImageObject img = store.getImageObject(imageId);
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
        if (store.size() == 0) {
            Snackbar.make(constraintLayout, R.string.set_wallpaper_no_images, Snackbar.LENGTH_LONG)
                    .setBackgroundTint(getColor(androidx.cardview.R.color.cardview_dark_background))
                    .setTextColor(getColor(R.color.white))
                    .show();
        } else {
            if (imgObjectId != null && !sourceExists(imgObjectId)) {
                adapter.removeItem(store.getPosition(imgObjectId));
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
        if (store.size() == 0) {
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
                        store.clear();
                        toggler.setChecked(false);
                        StorageUtils.CleanUpOrphans(getBaseContext().getFilesDir().getPath());
                        adapter.notifyDataSetChanged();
                        store.saveToPrefs(context);
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
                store.delImageObject(item.getId());
                adapter.removeItem(position);
                if (store.size() == 0) {
                    toggler.setChecked(false);
                    toggled = true;
                }
                store.saveToPrefs(context);


                Snackbar snackbar = Snackbar
                        .make(constraintLayout, getString(R.string.msg_swipe_item_removed), Snackbar.LENGTH_LONG);
                final boolean fToggled = toggled;
                snackbar.setAction(getString(R.string.snack_action_undo), view -> {

                    store.addImageObject(item, position);
                    store.saveToPrefs(context);
                    adapter.notifyItemInserted(position);
                    if (fToggled)
                        toggler.setChecked(true);
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
        setSingleWallpaper(store.getImageObject(position).getId());
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getResources().getString(R.string.preference_columns)) && rv != null) {
            int width = Math.round(Resources.getSystem().getDisplayMetrics().widthPixels);
            int columns = width / RVAdapter.getCardSize(context);
            rv.setLayoutManager(
                    new GridLayoutManager(context
                            , columns > 0 ? columns : 1));
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
        } else if (key.equals(getString(R.string.last_wallpaper))){
            store.setLastWallpaperId(sharedPreferences.getString(getString(R.string.last_wallpaper), ""), false);
            runOnUiThread(() -> adapter.notifyDataSetChanged());
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
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    int lastAddedPosition = -1;
    @Override
    public void onWallpaperAdded(ImageObject img) {
        int pos = store.getPosition(img.getId());
        runOnUiThread(() -> {
            adapter.notifyItemInserted(store.size() + 1);
            lastAddedPosition = pos;
        });
    }
    final LoadingDialog loadingDialog = new LoadingDialog(this);
    @Override
    public void onWallpaperLoadingStarted(int size) {
        loadingDialog.startLoadingDialog(size);
        lastAddedPosition = -1;
    }

    @Override
    public void onWallpaperLoadingIncrement(int inc) {
        loadingDialog.incrementProgressBy(inc);
    }

    @Override
    public void onWallpaperLoadingFinished(int status, String msg) {
        loadingDialog.dismissDialog();
        store.removeWallpaperAddedListener(this);
        if (status != ImageStore.WallpaperAddedListener.SUCCESS){
            new Handler(Looper.getMainLooper()).post(() -> new AlertDialog.Builder(this)
                    .setTitle("Error(s) loading images")
                    .setMessage((msg != null)?msg:"Unknown error.")
                    .setPositiveButton("Got it", (dialog2, which2) -> dialog2.dismiss())
                    .show());
        }
        if (lastAddedPosition > -1)
            runOnUiThread(() -> rv.scrollToPosition(lastAddedPosition));
    }

    @Override
    public void onActivityResult(ActivityResult result) {
        HashSet<Uri> sources = new HashSet<>();
        if (result.getResultCode() == Activity.RESULT_OK) {
            // There are no request codes
            Intent data = result.getData();
            StorageUtils.CleanUpOrphans(getFilesDir().getAbsolutePath());
            if (data != null) {
                if (data.getData() != null) {
                    //Single select
                    sources.add(data.getData());

                } else if (data.getClipData() != null) {
                    for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                        Uri uri = data.getClipData().getItemAt(i).getUri();
                        sources.add(uri);
                    }
                }
                ImageStore.getInstance().addWallpaperAddedListener(this);
                ImageStore.getInstance().addWallpapers(this, sources);
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onImageStoreSortChanged() {
        runOnUiThread(() -> adapter.notifyDataSetChanged());
        store.saveToPrefs(this);
    }
}