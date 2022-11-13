package com.moosedrive.wallpaperer;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * The type Drag to move callback.
 */
abstract public class DragToMoveCallback extends ItemTouchHelper.SimpleCallback {

    /**
     * The Dragged view.
     */
    View draggedView;
    /**
     * The recycler view.
     */
    RecyclerView myRv;

    /**
     * Instantiates a new Drag to move callback.
     *
     * @param myRv the my rv
     */
    public DragToMoveCallback(RecyclerView myRv) {
        super(ItemTouchHelper.UP
                | ItemTouchHelper.DOWN
                | ItemTouchHelper.LEFT
                | ItemTouchHelper.RIGHT, 0);
        this.myRv = myRv;
    }

    /**
     * An item is dragged. Do some scaling to visually indicate the action.
     * @param viewHolder
     * @param actionState
     */
    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
        super.onSelectedChanged(viewHolder, actionState);
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            draggedView = viewHolder.itemView;
            scaleOthers(draggedView, 0.9f);
        }
        if (actionState == ItemTouchHelper.ACTION_STATE_IDLE && draggedView != null) {
            scaleOthers(draggedView, 1f);
        }
    }

    /**
     * Scale the view holder a percentage of its original size
     * @param view A View
     * @param scale 1.0 == original size, 0.0 minimum
     */
    private void scaleView(@NonNull View view, float scale) {
        ObjectAnimator scaleUp = ObjectAnimator.ofPropertyValuesHolder(
                view,
                PropertyValuesHolder.ofFloat("scaleX", (scale < 0F)?0:scale),
                PropertyValuesHolder.ofFloat("scaleY", (scale < 0F)?0:scale)
        );
        scaleUp.setDuration(100);
        scaleUp.start();
    }

    /**
     * Scale all views except the one provided
     * @param excludeView a View
     * @param scale 1.0 == original size, 0.0 minimum
     */
    private void scaleOthers(View excludeView, float scale) {
        int otherCards = myRv.getChildCount();
        for (int i = 0; i < otherCards; i++) {
            View otherCard = myRv.getChildAt(i);
            if (otherCard != null && otherCard != excludeView) {
                RecyclerView.ViewHolder vHolder = myRv.getChildViewHolder(otherCard);
                if (vHolder != null) {
                    scaleView(vHolder.itemView, (scale < 0F)?0:scale);
                }
            }
        }
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

    }
}
