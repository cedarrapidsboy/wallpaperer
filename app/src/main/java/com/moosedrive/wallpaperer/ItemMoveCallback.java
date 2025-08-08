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
     * Instantiates a new ItemMoveCallback.
     * This callback enables drag-and-drop and swipe-to-delete functionality for items in a RecyclerView.
     *
     * @param myRv The RecyclerView to which this callback will be attached.
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
     * Called when the ViewHolder swiped or dragged by the ItemTouchHelper is changed.
     * <p>
     * If you override this method, you should call super.
     *
     * @param viewHolder  The new ViewHolder that is being swiped or dragged. Might be null if
     *                    it is cleared.
     * @param actionState The current action state. Might be {@link ItemTouchHelper#ACTION_STATE_IDLE},
     *                    {@link ItemTouchHelper#ACTION_STATE_SWIPE} or
     *                    {@link ItemTouchHelper#ACTION_STATE_DRAG}.
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
     * Scales the provided view by a specified percentage of its original size.
     * The scaling is animated over a short duration.
     *
     * @param view The View object to be scaled. Cannot be null.
     * @param scale The desired scale factor. 1.0 represents the original size,
     *              while 0.0 represents the minimum possible size (effectively invisible).
     *              Values less than 0.0 will be treated as 0.0.
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
     * Scales all views in the RecyclerView except for the specified view.
     * This is used to visually indicate which item is being dragged or has been released.
     *
     * @param excludeView The view that should not be scaled. Typically, this is the view being dragged.
     * @param scale       The scaling factor to apply to the other views.
     *                    1.0 represents the original size.
     *                    Values less than 1.0 will shrink the views.
     *                    Values greater than 1.0 will enlarge the views.
     *                    A value of 0.0 will make the views invisible (minimum scale).
     *                    Negative values are treated as 0.0.
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
