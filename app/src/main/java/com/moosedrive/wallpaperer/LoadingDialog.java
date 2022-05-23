package com.moosedrive.wallpaperer;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;


public class LoadingDialog {
    // 2 objects activity and dialog
    private final AppCompatActivity activity;
    private AlertDialog dialog;
    private ProgressBar pb = null;

    public LoadingDialog(AppCompatActivity activity) {
        this.activity = activity;
    }


    void startLoadingdialog(int max) {

        // adding ALERT Dialog builder object and passing activity as parameter
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        // layoutinflater object and use activity to get layout inflater
        LayoutInflater inflater = activity.getLayoutInflater();
        View alertView = inflater.inflate(R.layout.loading, null);
        builder.setView(alertView);
        builder.setCancelable(true);
        dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        pb = alertView.findViewById(R.id.loading_progress);
        pb.setMin(0);
        pb.setMax(max);
        pb.setIndeterminate(false);

        dialog.show();
    }

    public void incrementProgressBy(int num){
        if (pb != null){
            pb.incrementProgressBy(num);
        }
    }

    // dismiss method
    void dismissdialog() {
        dialog.dismiss();
    }

}
