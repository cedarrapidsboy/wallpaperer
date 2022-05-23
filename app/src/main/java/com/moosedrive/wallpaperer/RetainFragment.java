package com.moosedrive.wallpaperer;

import android.os.Bundle;
import android.util.LruCache;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

public class RetainFragment extends Fragment {
    private static final String TAG = "RetainFragment";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }
}