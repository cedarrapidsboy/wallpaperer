package com.moosedrive.wallpaperer; // Use your actual package name

public class ProgressDialogState {
    public final int maxProgress;
    public final int currentProgress;
    public final String message;
    public final boolean isIndeterminate;
    public final boolean isVisible;

    public ProgressDialogState(int maxProgress, int currentProgress, String message, boolean isIndeterminate, boolean isVisible) {
        this.maxProgress = maxProgress;
        this.currentProgress = currentProgress;
        this.message = message;
        this.isIndeterminate = isIndeterminate;
        this.isVisible = isVisible;
    }

    // Optional: A constructor for initial state
    public ProgressDialogState() {
        this.maxProgress = 100;
        this.currentProgress = 0;
        this.message = "";
        this.isIndeterminate = false;
        this.isVisible = false;
    }

    public ProgressDialogState copy(int maxProgress, int currentProgress, String message, boolean isIndeterminate, boolean isVisible) {
        return new ProgressDialogState(maxProgress, currentProgress, message, isIndeterminate, isVisible);
    }
}
