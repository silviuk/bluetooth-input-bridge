package com.antigravity.btbridge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class DigitizerView extends View {

    public interface StylusEventListener {
        void onStylusTelemetry(int pressure, int tiltDeg, boolean isBarrel, boolean isEraser, boolean isHover);
    }

    private Paint mBorderPaint;
    private Paint mActiveAreaPaint;
    private Paint mIndicatorPaint;
    private Paint mCrosshairPaint;
    private Paint mGridPaint;
    private Paint mTextPaint;

    private RectF mActiveRect = new RectF();
    private boolean mLockAspectRatio = true;
    private float mTargetAspect = 16f / 9f; // Standard PC display aspect ratio

    private float mLastX = -1;
    private float mLastY = -1;
    private float mLastPressure = 0f;
    private int mLastTiltDeg = 0;
    private boolean mIsContact = false;
    private boolean mIsHover = false;
    private boolean mIsBarrel = false;
    private boolean mIsEraser = false;

    private StylusEventListener mTelemetryListener;

    public DigitizerView(Context context) {
        super(context);
        init();
    }

    public DigitizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DigitizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setBackgroundColor(0xFF16161E); // Sleek Tokyo Night / Dark Slate

        mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBorderPaint.setColor(0xFF7AA2F7);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(3f);

        mActiveAreaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mActiveAreaPaint.setColor(0xFF1A1B26);
        mActiveAreaPaint.setStyle(Paint.Style.FILL);

        mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGridPaint.setColor(0xFF24283B);
        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setStrokeWidth(1f);
        mGridPaint.setPathEffect(new DashPathEffect(new float[]{6f, 6f}, 0));

        mIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mIndicatorPaint.setColor(0xFF7AA2F7);
        mIndicatorPaint.setStyle(Paint.Style.FILL);

        mCrosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCrosshairPaint.setColor(0x887AA2F7);
        mCrosshairPaint.setStyle(Paint.Style.STROKE);
        mCrosshairPaint.setStrokeWidth(1.5f);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(0xFF9ECE6A);
        mTextPaint.setTextSize(32f);
    }

    public void setTelemetryListener(StylusEventListener listener) {
        this.mTelemetryListener = listener;
    }

    public void setLockAspectRatio(boolean lock) {
        this.mLockAspectRatio = lock;
        requestLayout();
        invalidate();
    }

    public boolean isLockAspectRatio() {
        return mLockAspectRatio;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateActiveRect(w, h);
    }

    private void updateActiveRect(int w, int h) {
        if (!mLockAspectRatio || w <= 0 || h <= 0) {
            mActiveRect.set(0, 0, w, h);
            return;
        }

        float currentAspect = (float) w / (float) h;
        if (currentAspect > mTargetAspect) {
            // Screen is wider than 16:9, fit to height
            float targetW = h * mTargetAspect;
            float padX = (w - targetW) / 2f;
            mActiveRect.set(padX, 0, padX + targetW, h);
        } else {
            // Screen is taller than 16:9, fit to width
            float targetH = w / mTargetAspect;
            float padY = (h - targetH) / 2f;
            mActiveRect.set(0, padY, w, padY + targetH);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int historySize = event.getHistorySize();

        // Send batched intermediate points for maximum stroke smoothness
        for (int i = 0; i < historySize; i++) {
            float histX = event.getHistoricalX(0, i);
            float histY = event.getHistoricalY(0, i);
            float histPressure = event.getHistoricalPressure(0, i);
            dispatchMotion(Protocol.STYLUS_MOVE, histX, histY, histPressure, event, false);
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mIsContact = true;
                mIsHover = false;
                dispatchMotion(Protocol.STYLUS_DOWN, event.getX(), event.getY(), event.getPressure(), event, true);
                break;

            case MotionEvent.ACTION_MOVE:
                mIsContact = true;
                mIsHover = false;
                dispatchMotion(Protocol.STYLUS_MOVE, event.getX(), event.getY(), event.getPressure(), event, true);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsContact = false;
                dispatchMotion(Protocol.STYLUS_UP, event.getX(), event.getY(), 0f, event, true);
                break;
        }

        invalidate();
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE:
                mIsHover = true;
                mIsContact = false;
                dispatchMotion(Protocol.STYLUS_HOVER, event.getX(), event.getY(), 0f, event, true);
                invalidate();
                return true;

            case MotionEvent.ACTION_HOVER_EXIT:
                mIsHover = false;
                mLastX = -1;
                mLastY = -1;
                invalidate();
                return true;
        }

        return super.onGenericMotionEvent(event);
    }

    private void dispatchMotion(byte stylusAction, float x, float y, float rawPressure, MotionEvent event, boolean updateVisuals) {
        // Clamp coordinates within active drawing rectangle
        float clampedX = Math.max(mActiveRect.left, Math.min(x, mActiveRect.right));
        float clampedY = Math.max(mActiveRect.top, Math.min(y, mActiveRect.bottom));

        float normXFloat = (mActiveRect.width() > 0) ? (clampedX - mActiveRect.left) / mActiveRect.width() : 0f;
        float normYFloat = (mActiveRect.height() > 0) ? (clampedY - mActiveRect.top) / mActiveRect.height() : 0f;

        int normX = (int) Math.max(0, Math.min(65535, normXFloat * 65535f));
        int normY = (int) Math.max(0, Math.min(65535, normYFloat * 65535f));

        // Pressure mapping: 0 to 1024
        int pressureInt = (int) Math.max(0, Math.min(1024, rawPressure * 1024f));

        // Tilt angle
        float tiltRad = event.getAxisValue(MotionEvent.AXIS_TILT);
        int tiltDeg = (int) Math.toDegrees(tiltRad);

        // Tool type and button flags
        int toolType = event.getToolType(0);
        int buttonState = event.getButtonState();

        boolean isBarrel = (buttonState & MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 ||
                           (buttonState & MotionEvent.BUTTON_SECONDARY) != 0;
        boolean isEraser = (toolType == MotionEvent.TOOL_TYPE_ERASER);

        byte flags = Protocol.STYLUS_FLAG_IN_RANGE;
        if (stylusAction == Protocol.STYLUS_DOWN || stylusAction == Protocol.STYLUS_MOVE) {
            flags |= Protocol.STYLUS_FLAG_TIP;
        }
        if (isBarrel) {
            flags |= Protocol.STYLUS_FLAG_BARREL;
        }
        if (isEraser) {
            flags |= Protocol.STYLUS_FLAG_ERASER;
        }

        // Send to remote Windows PC
        BluetoothBridgeService service = BluetoothBridgeService.getInstance();
        if (service != null && service.isConnected()) {
            service.sendStylusEvent(stylusAction, flags, normX, normY, pressureInt, tiltDeg, 0);
        }

        if (updateVisuals) {
            mLastX = x;
            mLastY = y;
            mLastPressure = rawPressure;
            mLastTiltDeg = tiltDeg;
            mIsBarrel = isBarrel;
            mIsEraser = isEraser;

            if (mTelemetryListener != null) {
                mTelemetryListener.onStylusTelemetry(pressureInt, tiltDeg, isBarrel, isEraser, mIsHover);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 1. Draw active drawing area
        canvas.drawRect(mActiveRect, mActiveAreaPaint);
        canvas.drawRect(mActiveRect, mBorderPaint);

        // 2. Draw subtle guide cross in the center
        float cx = mActiveRect.centerX();
        float cy = mActiveRect.centerY();
        canvas.drawLine(mActiveRect.left, cy, mActiveRect.right, cy, mGridPaint);
        canvas.drawLine(cx, mActiveRect.top, cx, mActiveRect.bottom, mGridPaint);

        // 3. Draw stylus cursor / feedback circle
        if (mLastX >= 0 && mLastY >= 0) {
            float radius = 10f;
            if (mIsContact) {
                radius = 8f + (mLastPressure * 36f); // Scales dynamically with pressure
                mIndicatorPaint.setColor(mIsEraser ? 0xFFF7768E : 0xFF7AA2F7);
                mIndicatorPaint.setAlpha(220);
                canvas.drawCircle(mLastX, mLastY, radius, mIndicatorPaint);
            } else if (mIsHover) {
                mIndicatorPaint.setColor(0xFFBB9AF7);
                mIndicatorPaint.setStyle(Paint.Style.STROKE);
                mIndicatorPaint.setStrokeWidth(2.5f);
                canvas.drawCircle(mLastX, mLastY, 14f, mIndicatorPaint);
                mIndicatorPaint.setStyle(Paint.Style.FILL);
            }

            // Crosshair lines
            canvas.drawLine(mLastX - 25, mLastY, mLastX + 25, mLastY, mCrosshairPaint);
            canvas.drawLine(mLastX, mLastY - 25, mLastX, mLastY + 25, mCrosshairPaint);
        }
    }
}
