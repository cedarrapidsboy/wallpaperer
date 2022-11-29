package com.moosedrive.wallpaperer;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * The type Drag to move callback.
 */
abstract public class ItemMoveCallback extends ItemTouchHelper.SimpleCallback {

    /**
     * The Dragged view.
     */
    View draggedView;
    /**
     * The recycler view.
     */
    RecyclerView myRv;
    final Context mContext;
    private final Paint mClearPaint;
    private final ColorDrawable mBackground;
    private final int backgroundColor;
    private final Drawable deleteDrawable;
    private final int intrinsicWidth;
    private final int intrinsicHeight;
    /**
     * Instantiates a new Drag to move callback.
     *
     * @param myRv the my rv
     */
    public ItemMoveCallback(RecyclerView myRv) {
        super(ItemTouchHelper.UP
                | ItemTouchHelper.DOWN
                | ItemTouchHelper.LEFT
                | ItemTouchHelper.RIGHT, ItemTouchHelper.LEFT
                | ItemTouchHelper.RIGHT);
        this.myRv = myRv;
        mContext = myRv.getContext();
        mBackground = new ColorDrawable();
        backgroundColor = Color.parseColor("#b80f0a");
        mClearPaint = new Paint();
        mClearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        deleteDrawable = ContextCompat.getDrawable(mContext, R.drawable.ic_delete);
        assert deleteDrawable != null;
        intrinsicWidth = deleteDrawable.getIntrinsicWidth();
        intrinsicHeight = deleteDrawable.getIntrinsicHeight();
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
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            View itemView = viewHolder.itemView;
            int itemHeight = itemView.getHeight();

            boolean isCancelled = dX == 0 && !isCurrentlyActive;

            if (isCancelled) {
                clearCanvas(c, itemView.getRight() + dX, (float) itemView.getTop(), (float) itemView.getRight(), (float) itemView.getBottom());
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, false);
                return;
            }

            mBackground.setColor(backgroundColor);
            mBackground.setBounds(itemView.getLeft(), itemView.getTop(), itemView.getRight(), itemView.getBottom());
            mBackground.draw(c);

            int deleteIconTop = itemView.getTop() + (itemHeight - intrinsicHeight) / 2;
            int deleteIconMargin = (itemHeight - intrinsicHeight) / 2;
            int deleteIconLeft = itemView.getRight() - deleteIconMargin - intrinsicWidth;
            int deleteIconRight = itemView.getRight() - deleteIconMargin;
            int deleteIconBottom = deleteIconTop + intrinsicHeight;


            deleteDrawable.setBounds(deleteIconLeft, deleteIconTop, deleteIconRight, deleteIconBottom);
            deleteDrawable.draw(c);

            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

        }
    }

    private void clearCanvas(Canvas c, Float left, Float top, Float right, Float bottom) {
        c.drawRect(left, top, right, bottom, mClearPaint);

    }

}
