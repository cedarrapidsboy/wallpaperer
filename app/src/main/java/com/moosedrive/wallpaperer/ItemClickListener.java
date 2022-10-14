package com.moosedrive.wallpaperer;

import android.view.View;

public interface ItemClickListener {
    void onSetWpClick(int position);

    void onImageClick(int pos, View view);
}
