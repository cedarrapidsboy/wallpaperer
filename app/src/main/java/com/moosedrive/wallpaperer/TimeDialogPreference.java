package com.moosedrive.wallpaperer;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;

import java.util.Locale;

/**
 * A DialogPreference that allows the user to select a time (hour and minute).
 * <p>
 * The selected time is stored as a string in the format "HH:mm" (e.g., "08:30").
 * This class provides helper methods to parse and format time strings.
 * </p>
 * <p>
 * Example usage in XML:
 * <pre>{@code
 * <com.moosedrive.wallpaperer.TimeDialogPreference
 *     android:key="time_preference_key"
 *     android:title="Select Time"
 *     android:defaultValue="12:00" />
 * }</pre>
 * </p>
 */
public class TimeDialogPreference extends DialogPreference {
    public int hour = 0;
    public int minute = 0;

    public TimeDialogPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public static int parseHour(String value) {
        try {
            String[] time = value.split(":");
            return (Integer.parseInt(time[0]));
        } catch (Exception e) {
            return 0;
        }
    }

    public static int parseMinute(String value) {
        try {
            String[] time = value.split(":");
            return (Integer.parseInt(time[1]));
        } catch (Exception e) {
            return 0;
        }
    }

    public static String timeToString(int h, int m) {
        return String.format(Locale.US, "%02d", h) + ":" + String.format(Locale.US, "%02d", m);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        String value;
        if (defaultValue == null)
            value = getPersistedString("00:15");
        else
            value = getPersistedString(defaultValue.toString());

        hour = parseHour(value);
        minute = parseMinute(value);
    }

    public void persistStringValue(String value) {
        persistString(value);
    }
}
