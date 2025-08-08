package com.moosedrive.wallpaperer;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

public class ProgressDialogFragment extends DialogFragment {

    private ProgressBar pb;
    private TextView messageView;
    private ProgressViewModel progressViewModel;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        progressViewModel = new ViewModelProvider(requireActivity()).get(ProgressViewModel.class);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        LayoutInflater inflater = getLayoutInflater();
        View alertView = inflater.inflate(R.layout.loading, null);
        builder.setView(alertView);

        AlertDialog dialog = builder.create();
        try {
            Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        } catch (NullPointerException e) {
            Log.e(ProgressDialogFragment.class.getSimpleName(), "onCreateDialog: ", e);
        }
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);

        pb = alertView.findViewById(R.id.loading_progress);
        pb.setMin(0); // Requires API 26, ensure your minSdk is compatible or handle appropriately
        messageView = alertView.findViewById(R.id.loading_dialog_message);

        progressViewModel.getUiState().observe(this, state -> {
            if (state != null) {
                pb.setMax(state.maxProgress);
                pb.setProgress(state.currentProgress);
                messageView.setText(state.message);
                pb.setIndeterminate(state.isIndeterminate);
                if (!state.isVisible && getDialog() != null && getDialog().isShowing()) {
                    dismissAllowingStateLoss();
                }
            }
        });

        ProgressDialogState initialState = progressViewModel.getUiState().getValue();
        if (initialState != null && initialState.isVisible) {
            pb.setMax(initialState.maxProgress);
            pb.setProgress(initialState.currentProgress);
            messageView.setText(initialState.message);
            pb.setIndeterminate(initialState.isIndeterminate);
        } else {
            pb.setMax(100);
            messageView.setText("");
            pb.setIndeterminate(true);
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        ProgressDialogState currentState = progressViewModel.getUiState().getValue();
        if (currentState != null && !currentState.isVisible && getDialog() != null && getDialog().isShowing()) {
            dismissAllowingStateLoss();
        }
    }

    @Override
    public void onDestroyView() {
        // If you were using setRetainInstance(true), it's generally recommended to remove it
        // when using ViewModels to manage UI state.
        Dialog dialog = getDialog();
        if (dialog != null) {
            dialog.setDismissMessage(null);
        }
        super.onDestroyView();
    }
}
