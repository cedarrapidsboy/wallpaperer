package com.moosedrive.wallpaperer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.snackbar.Snackbar;
import com.moosedrive.wallpaperer.data.ImageObject;
import com.moosedrive.wallpaperer.data.ImageStore;
import com.moosedrive.wallpaperer.utils.BackgroundExecutor;
import com.moosedrive.wallpaperer.utils.IExportListener;
import com.moosedrive.wallpaperer.utils.StorageUtils;
import com.moosedrive.wallpaperer.wallpaper.WallpaperManager;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class SettingsActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements IExportListener, WallpaperManager.IWallpaperAddedListener {

        private ActivityResultLauncher<Intent> importChooserResultLauncher;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            registerImportChooser();
        }

        @Override
        public void onResume() {
            super.onResume();
            // Change "button" text to open battery optimization based on current optimization setting
            // The scheduled worker does not fire consistently when optimized
            Preference button = findPreference(getString(R.string.preference_optimization_key));
            PowerManager pm = (PowerManager) requireContext().getSystemService(POWER_SERVICE);
            if (button != null) {
                if (pm.isIgnoringBatteryOptimizations(requireContext().getPackageName())) {
                    button.setSummary(R.string.preference_optimization_summary_good);
                } else {
                    button.setSummary(R.string.preference_optimization_summary);
                }
                button.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    preference.getContext().startActivity(intent);
                    return true;
                });
            }
            button = findPreference(getString(R.string.preference_import_key));
            if (button!=null){
                button.setOnPreferenceClickListener(preference -> {
                    openImportChooser();
                    return true;
                });
            }
            button = findPreference(getString(R.string.preference_export_key));
            if (button != null){
                button.setOnPreferenceClickListener(preference -> {
                    BackgroundExecutor.getExecutor().execute(() -> {
                        try {
                            StorageUtils.makeBackup(ImageStore.getInstance().getReferenceObjects(), this);
                        } catch (IOException e) {
                            onExportFinished(IExportListener.ERROR, "Error exporting images: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                    return true;
                });
            }

        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onDisplayPreferenceDialog(@NonNull Preference preference) {
            DialogFragment dialogFragment = null;
            if (preference instanceof TimeDialogPreference) {
                dialogFragment = new TimeDialogFragment();
                Bundle bundle = new Bundle(1);
                bundle.putString("key", preference.getKey());
                dialogFragment.setArguments(bundle);
            }

            if (dialogFragment != null) {
                dialogFragment.setTargetFragment(this, 0);
                dialogFragment.show(getParentFragmentManager(), "TIME_PICKER_FRAGMENT");
            } else {
                super.onDisplayPreferenceDialog(preference);
            }
        }

        /**
         * Open image chooser.
         */
        public void openImportChooser() {

            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            //intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            importChooserResultLauncher.launch(Intent.createChooser(intent, getString(R.string.intent_chooser_select_imports)));
        }

        /**
         * For when the import function is used and we need a ZIP chooser.
         */
        private void registerImportChooser() {
            importChooserResultLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        HashSet<Uri> sources = new HashSet<>();
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            // There are no request codes
                            Intent data = result.getData();
                            StorageUtils.CleanUpOrphans(requireContext().getFilesDir().getAbsolutePath());
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
                                BackgroundExecutor.getExecutor().execute(() -> {
                                    AtomicInteger i = new AtomicInteger(1);
                                    sources.forEach(zipUri -> {
                                        try {
                                            StorageUtils.importBackup(requireContext(), zipUri, ImageStore.getInstance(), i.get(), sources.size(), this);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                            onWallpaperLoadingFinished(WallpaperManager.IWallpaperAddedListener.ERROR, e.getMessage());
                                        }
                                        i.getAndIncrement();
                                    });
                                });
                            }
                        }
                    });
        }

        @Override
        public void onWallpaperAdded(ImageObject img) {

        }
        private ProgressDialogFragment importDialog;
        @Override
        public void onWallpaperLoadingStarted(int size, String message) {
            requireActivity().runOnUiThread(() -> {
                importDialog = ProgressDialogFragment.newInstance(size);
                importDialog.showNow(getChildFragmentManager(), "add_progress");
                if (message != null) {
                    importDialog.setMessage(message);
                }
            });
        }

        @Override
        public void onWallpaperLoadingIncrement(int inc) {
            requireActivity().runOnUiThread(() -> {
                if (inc < 0)
                    importDialog.setIndeterminate(true);
                else {
                    importDialog.setIndeterminate(false);
                    importDialog.incrementProgressBy(inc);
                }
            });
        }

        @Override
        public void onWallpaperLoadingFinished(int status, String message) {
            if (importDialog != null)
                requireActivity().runOnUiThread(() -> importDialog.dismiss());
            WallpaperManager.getInstance().removeWallpaperAddedListener(this);
            ImageStore.getInstance().saveToPrefs(requireContext());
            if (status != WallpaperManager.IWallpaperAddedListener.SUCCESS) {
                new Handler(Looper.getMainLooper()).post(() -> new AlertDialog.Builder(requireContext())
                        .setTitle("Error(s) loading images")
                        .setMessage((message != null) ? message : "Unknown error.")
                        .setPositiveButton("Got it", (dialog2, which2) -> dialog2.dismiss())
                        .show());
            } else {
                Snackbar.make(requireView(), (message == null)?"Import complete.":message, Snackbar.LENGTH_LONG)
                        .setBackgroundTint(requireActivity().getColor(androidx.cardview.R.color.cardview_dark_background))
                        .setTextColor(requireActivity().getColor(R.color.white))
                        .show();
            }
        }
        private ProgressDialogFragment exportDialog;
        @Override
        public void onExportStarted(int size, String message) {
            requireActivity().runOnUiThread(() -> {
                exportDialog = ProgressDialogFragment.newInstance(size);
                exportDialog.showNow(getChildFragmentManager(), "export_progress");
                if (message != null){
                    exportDialog.setMessage(message);
                }
            });
        }

        @Override
        public void onExportIncrement(int inc) {
            requireActivity().runOnUiThread(() -> {
                if (inc < 0)
                    exportDialog.setIndeterminate(true);
                else {
                    exportDialog.setIndeterminate(false);
                    exportDialog.incrementProgressBy(inc);
                }
            });
        }

        @Override
        public void onExportFinished(int status, String message) {
                requireActivity().runOnUiThread(() -> {if (exportDialog != null) exportDialog.dismiss();});
            if (status != WallpaperManager.IWallpaperAddedListener.SUCCESS) {
                new Handler(Looper.getMainLooper()).post(() -> new AlertDialog.Builder(requireContext())
                        .setTitle("Error(s) exporting images")
                        .setMessage((message != null) ? message : "Unknown error.")
                        .setPositiveButton("Got it", (dialog2, which2) -> dialog2.dismiss())
                        .show());
            } else {
                Snackbar.make(requireView(), (message == null)?"Export complete.":message, Snackbar.LENGTH_LONG)
                        .setBackgroundTint(requireActivity().getColor(androidx.cardview.R.color.cardview_dark_background))
                        .setTextColor(requireActivity().getColor(R.color.white))
                        .show();
            }
        }
    }

}