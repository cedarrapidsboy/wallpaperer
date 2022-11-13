package com.moosedrive.wallpaperer;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.moosedrive.wallpaperer.utils.PreferenceHelper;

import java.util.Date;

public class TimerArc extends View {
    private static final int ARC_START_ANGLE = 270; // 12 o'clock

    private static final float THICKNESS_SCALE = 0.5f;
    private final Paint mCirclePaint;
    private final Paint mEraserPaint;
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private RectF mCircleOuterBounds;
    private RectF mCircleInnerBounds;
    private float mCircleSweepAngle;

    private ValueAnimator mTimerAnimator;

    public TimerArc(Context context) {
        this(context, null);
    }

    public TimerArc(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TimerArc(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        int circleColor = Color.RED;

        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.TimerArc);
            if (ta != null) {
                circleColor = ta.getColor(R.styleable.TimerArc_ringColor, circleColor);
                ta.recycle();
            }
        }

        mCirclePaint = new Paint();
        mCirclePaint.setAntiAlias(true);
        mCirclePaint.setColor(circleColor);

        mEraserPaint = new Paint();
        mEraserPaint.setAntiAlias(true);
        mEraserPaint.setColor(Color.TRANSPARENT);
        mEraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    @SuppressWarnings("SuspiciousNameCombination")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec); // Trick to make the view square
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w != oldw || h != oldh) {
            mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            mBitmap.eraseColor(Color.TRANSPARENT);
            mCanvas = new Canvas(mBitmap);
        }

        super.onSizeChanged(w, h, oldw, oldh);
        updateBounds();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mCanvas.drawColor(0, PorterDuff.Mode.CLEAR);

        if (mCircleSweepAngle > 0f) {
            mCanvas.drawArc(mCircleOuterBounds, ARC_START_ANGLE, mCircleSweepAngle, true, mCirclePaint);
            mCanvas.drawOval(mCircleInnerBounds, mEraserPaint);
        }

        canvas.drawBitmap(mBitmap, 0, 0, null);
    }

    public void start() {
        stop();
        long now = new Date().getTime();
        mTimerAnimator = ValueAnimator.ofFloat(0f, 1f);
        long timeToGo = PreferenceHelper.getScheduledWallpaperChange(getContext()) - now;
        mTimerAnimator.setDuration((timeToGo > 0) ? timeToGo : 0);
        mTimerAnimator.setInterpolator(new LinearInterpolator());
        mTimerAnimator.addUpdateListener(animation -> {
            long now1 = new Date().getTime();
            long timeToGoLocal = (PreferenceHelper.getScheduledWallpaperChange(getContext()) - now1);
            long wallpaperDelay = PreferenceHelper.getWallpaperDelay(getContext());
            drawProgress((timeToGoLocal <= wallpaperDelay && timeToGoLocal >= 0) ? timeToGoLocal / (float) wallpaperDelay * 360 : 0);
        });
        mTimerAnimator.start();
    }

    public void stop() {
        if (mTimerAnimator != null && mTimerAnimator.isRunning()) {
            mTimerAnimator.cancel();
            mTimerAnimator = null;

            drawProgress(0);
        }
    }

    private void drawProgress(float progress) {
        mCircleSweepAngle = progress;

        invalidate();
    }

    private void updateBounds() {
        final float thickness = getWidth() * THICKNESS_SCALE;

        mCircleOuterBounds = new RectF(0, 0, getWidth(), getHeight());
        mCircleInnerBounds = new RectF(
                mCircleOuterBounds.left + thickness,
                mCircleOuterBounds.top + thickness,
                mCircleOuterBounds.right - thickness,
                mCircleOuterBounds.bottom - thickness);

        invalidate();
    }
}