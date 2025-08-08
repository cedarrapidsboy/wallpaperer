package com.moosedrive.wallpaperer; // Use your actual package name

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class ProgressViewModel extends ViewModel {

    private final MutableLiveData<ProgressDialogState> uiState = new MutableLiveData<>(new ProgressDialogState());
    public LiveData<ProgressDialogState> getUiState() {
        return uiState;
    }

    public void setMaxProgress(int max) {
        ProgressDialogState currentState = uiState.getValue();
        if (currentState != null) {
            uiState.setValue(currentState.copy(max, currentState.currentProgress, currentState.message, currentState.isIndeterminate, currentState.isVisible));
        }
    }

    public void incrementProgressBy(int amount) {
        ProgressDialogState currentState = uiState.getValue();
        if (currentState != null) {
            uiState.setValue(currentState.copy(currentState.maxProgress, currentState.currentProgress + amount, currentState.message, currentState.isIndeterminate, currentState.isVisible));
        }
    }

    public void setMessage(String newMessage) {
        ProgressDialogState currentState = uiState.getValue();
        if (currentState != null) {
            uiState.setValue(currentState.copy(currentState.maxProgress, currentState.currentProgress, newMessage, currentState.isIndeterminate, currentState.isVisible));
        }
    }

    public void setIndeterminate(boolean indeterminate) {
        ProgressDialogState currentState = uiState.getValue();
        if (currentState != null) {
            uiState.setValue(currentState.copy(currentState.maxProgress, currentState.currentProgress, currentState.message, indeterminate, currentState.isVisible));
        }
    }

    public void showDialog(int max, String initialMessage, boolean isIndeterminate) {
        uiState.setValue(new ProgressDialogState(max, 0, initialMessage, isIndeterminate, true));
    }

    public void hideDialog() {
        ProgressDialogState currentState = uiState.getValue();
        if (currentState != null) {
            uiState.setValue(currentState.copy(currentState.maxProgress, currentState.currentProgress, currentState.message, currentState.isIndeterminate, false));
        } else {
            // Should ideally not happen, but as a fallback
            uiState.setValue(new ProgressDialogState(100, 0, "", false, false));
        }
    }

    public void updateProgress(int progress, String message, Boolean indeterminate) {
        ProgressDialogState currentState = uiState.getValue();
        if (currentState != null) {
            String msgToSet = (message != null) ? message : currentState.message;
            boolean indetToSet = (indeterminate != null) ? indeterminate : currentState.isIndeterminate;
            uiState.setValue(currentState.copy(currentState.maxProgress, progress, msgToSet, indetToSet, currentState.isVisible));
        }
    }
}
