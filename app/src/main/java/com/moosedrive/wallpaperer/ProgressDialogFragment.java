package com.moosedrive.wallpaperer;


import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

@SuppressWarnings("deprecation")
public class ProgressDialogFragment extends DialogFragment {

    public static ProgressDialogFragment newInstance(int max) {
        Bundle args = new Bundle();
        args.putInt("max", max);
        ProgressDialogFragment f = new ProgressDialogFragment();
        f.setArguments(args);
        return f;
    }

    AlertDialog dialog;
    ProgressBar pb;
    TextView messageView;
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Hack to survive orientation change
        setRetainInstance(true);
        // adding ALERT Dialog builder object and passing activity as parameter
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        // layoutinflater object and use activity to get layout inflater
        LayoutInflater inflater = getLayoutInflater();
        View alertView = inflater.inflate(R.layout.loading, null);
        builder.setView(alertView);
        builder.setCancelable(true);
        dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);
        pb = alertView.findViewById(R.id.loading_progress);
        pb.setMin(0);
        messageView = alertView.findViewById(R.id.loading_dialog_message);
        assert getArguments() != null;
        pb.setMax(getArguments().getInt("max"));
        pb.setIndeterminate(false);
        return dialog;
    }
    public void incrementProgressBy(int num) {
        pb.incrementProgressBy(num);
    }

    public void setMessage(String msg){
        messageView.setText(msg);
    }

    public void setIndeterminate(boolean ind) {
        pb.setIndeterminate(ind);
    }
}