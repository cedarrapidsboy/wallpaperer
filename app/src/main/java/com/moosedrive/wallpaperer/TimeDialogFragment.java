package com.moosedrive.wallpaperer;

import android.content.Context;
import android.view.View;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceDialogFragmentCompat;

/**
 * A dialog fragment that allows the user to select a time.
 * This class extends PreferenceDialogFragmentCompat, which is a support library class that provides a
 * dialog fragment for use with preferences.
 */
public class TimeDialogFragment extends PreferenceDialogFragmentCompat {

    TimePicker timePicker = null;

    @Override
    protected View onCreateDialogView(@NonNull Context context) {
        timePicker = new TimePicker(context);
        return (timePicker);
    }

    @Override
    protected void onBindDialogView(@NonNull View v) {
        super.onBindDialogView(v);
        timePicker.setIs24HourView(true);
        TimeDialogPreference pref = (TimeDialogPreference) getPreference();
        timePicker.setHour(pref.hour);
        timePicker.setMinute(pref.minute);
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            TimeDialogPreference pref = (TimeDialogPreference) getPreference();
            pref.hour = timePicker.getHour();
            pref.minute = timePicker.getMinute();
            if (pref.hour == 0 && pref.minute < 1) {
                pref.minute = 1;
                Toast.makeText(getContext(), getString(R.string.toast_delay_too_short), Toast.LENGTH_LONG).show();
            }

            String value = TimeDialogPreference.timeToString(pref.hour, pref.minute);
            if (pref.callChangeListener(value)) pref.persistStringValue(value);
        }
    }

}

