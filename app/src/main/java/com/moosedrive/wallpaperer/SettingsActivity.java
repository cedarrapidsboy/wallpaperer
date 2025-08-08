package com.moosedrive.wallpaperer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.moosedrive.wallpaperer.data.ImportData;
import com.moosedrive.wallpaperer.utils.BackgroundExecutor;
import com.moosedrive.wallpaperer.utils.StorageUtils;

import java.util.UUID;

/**
 * Activity for managing application settings.
 * <p>
 * This activity provides a user interface for configuring various aspects of the application.
 * It uses a {@link SettingsFragment} to display and manage preferences.
 * <p>
 * Key functionalities include:
 * <ul>
 *     <li>Displaying application settings using {@link PreferenceFragmentCompat}.</li>
 *     <li>Handling import and export of application data (backups).</li>
 *     <li>Managing battery optimization settings to ensure proper background operation.</li>
 *     <li>Providing custom dialogs for specific preference types (e.g., {@link TimeDialogPreference}).</li>
 * </ul>
 * <p>
 * Constants for result codes and intent extras related to import/export operations are defined here.
 * The activity also sets up the action bar with a "home" button for navigation.
 */
public class SettingsActivity extends AppCompatActivity {

    public static final int IMPORT_RESULT_CODE = 5;
    public static final int EXPORT_RESULT_CODE = 6;
    public static final String IMPORT_UUID = "import_uuid";
    public static final String EXPORT_UUID = "export_uuid";

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

    public static class SettingsFragment extends PreferenceFragmentCompat {

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
                           WorkRequest exportWork = new OneTimeWorkRequest.Builder(StorageUtils.ExportBackupWorker.class)
                                    .setInputData(
                                            new Data.Builder().build()
                                    ).build();
                            UUID exportId = exportWork.getId();
                            WorkManager.getInstance(requireContext()).enqueue(exportWork);
                            Intent importIntent = new Intent();
                            importIntent.putExtra(EXPORT_UUID, exportId);
                            requireActivity().setResult(EXPORT_RESULT_CODE, importIntent);
                            requireActivity().finish();
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

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            importChooserResultLauncher.launch(Intent.createChooser(intent, getString(R.string.intent_chooser_select_imports)));
        }

        /**
         * Registers an activity result launcher for the import chooser.
         * <p>
         * This method is called when the import function is used and a ZIP chooser is needed.
         * It handles the result of the chooser, processing the selected ZIP file(s) for import.
         * If the result is OK, it retrieves the URI(s) of the selected file(s),
         * takes persistable URI permissions, and adds the URI(s) to the import sources.
         * It then initiates a background worker to perform the import operation.
         * Finally, it sets the result of the activity and finishes it.
         */
        private void registerImportChooser() {
            importChooserResultLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        ImportData.getInstance().importSources.clear();
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            // There are no request codes
                            Intent data = result.getData();
                            StorageUtils.CleanUpOrphans(requireContext().getApplicationContext(), requireContext().getFilesDir().getAbsolutePath());
                            if (data != null) {
                                if (data.getData() != null) {
                                    //Single select
                                    requireContext().getContentResolver().takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    ImportData.getInstance().importSources.add(data.getData());
                                } else if (data.getClipData() != null) {
                                    for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                                        Uri uri = data.getClipData().getItemAt(i).getUri();
                                        try {
                                            requireContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                            ImportData.getInstance().importSources.add(uri);
                                        }
                                        catch (SecurityException e){
                                            Log.e(SettingsActivity.class.getSimpleName(), "Unable to get persistable URI permission for " + uri + ". Will attempt to import anyway. This could be problematic for ContentObserver.", e);
                                        }

                                    }
                                }
                                WorkRequest importWork = new OneTimeWorkRequest.Builder(StorageUtils.ImportBackupWorker.class)
                                        .setInputData(
                                                new Data.Builder().build()
                                        ).build();
                                UUID importId = importWork.getId();
                                WorkManager.getInstance(requireContext()).enqueue(importWork);
                                Intent importIntent = new Intent();
                                importIntent.putExtra(IMPORT_UUID, importId);
                                requireActivity().setResult(IMPORT_RESULT_CODE, importIntent);
                                requireActivity().finish();
                            }
                        }
                    });
        }
    }

}