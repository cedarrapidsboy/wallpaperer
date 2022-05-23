package com.moosedrive.wallpaperer;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;

import java.util.Locale;

public class DialogTimePreference extends DialogPreference {
    public int hour = 0;
    public int minute = 0;

    public DialogTimePreference(@NonNull Context context, @Nullable AttributeSet attrs) {
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
        return String.format(Locale.US,"%02d", h) + ":" + String.format(Locale.US,"%02d", m);
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
