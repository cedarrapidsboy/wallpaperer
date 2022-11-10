package com.moosedrive.wallpaperer;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

abstract public class DragToMoveCallback extends ItemTouchHelper.SimpleCallback {
    boolean drag = false;
    View draggedView;
    RecyclerView myRv;

    public DragToMoveCallback(RecyclerView myRv) {
        super(ItemTouchHelper.UP
                | ItemTouchHelper.DOWN
                | ItemTouchHelper.LEFT
                | ItemTouchHelper.RIGHT
                | ItemTouchHelper.START
                | ItemTouchHelper.END, 0);
        this.myRv = myRv;
    }

    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
        super.onSelectedChanged(viewHolder, actionState);

        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            drag = true;
            draggedView = viewHolder.itemView;
            //scaleView(viewHolder.itemView, 1.10f);
            scaleOthers(draggedView, 0.9f);
        }
        if (actionState == ItemTouchHelper.ACTION_STATE_IDLE && drag && draggedView != null) {
            //scaleView(draggedView, 1f);
            scaleOthers(draggedView, 1f);
            drag = false;
        }
    }

    private void scaleView(@NonNull View viewHolder, float x) {
        ObjectAnimator scaleUp = ObjectAnimator.ofPropertyValuesHolder(
                viewHolder,
                PropertyValuesHolder.ofFloat("scaleX", x),
                PropertyValuesHolder.ofFloat("scaleY", x)
        );
        scaleUp.setDuration(100);
        scaleUp.start();
    }

    private void scaleOthers(View excludeView, float scale) {
        int otherCards = myRv.getChildCount();
        for (int i = 0; i < otherCards; i++) {
            View otherCard = myRv.getChildAt(i);
            if (otherCard != null && otherCard != excludeView) {
                RecyclerView.ViewHolder vHolder = myRv.getChildViewHolder(otherCard);
                if (vHolder != null) {
                    scaleView(vHolder.itemView, scale);
                }
            }
        }
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

    }
}
