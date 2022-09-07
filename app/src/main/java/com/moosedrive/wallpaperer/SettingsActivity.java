package com.moosedrive.wallpaperer;

import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

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

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);


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

        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
        }

        @Override
        public void onDisplayPreferenceDialog(@NonNull Preference preference) {
            DialogFragment dialogFragment = null;
            if (preference instanceof DialogTimePreference) {
                dialogFragment = new DialogTimePrefCompat();
                Bundle bundle = new Bundle(1);
                bundle.putString("key", preference.getKey());
                dialogFragment.setArguments(bundle);
            }

            if (dialogFragment != null) {
                dialogFragment.setTargetFragment(this, 0);
                dialogFragment.show(this.getFragmentManager(), "TIME_PICKER_FRAGMENT");
            } else {
                super.onDisplayPreferenceDialog(preference);
            }
        }
    }

}