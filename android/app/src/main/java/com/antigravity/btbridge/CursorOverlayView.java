package com.antigravity.btbridge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

public class CursorOverlayView extends View {
    private final WindowManager mWindowManager;
    private final WindowManager.LayoutParams mParams;
    private final Paint mPaintPointer;
    private final Paint mPaintStroke;
    private final Path mPointerPath;

    private float mX = 500f;
    private float mY = 1000f;
    private int mScreenWidth = 1080;
    private int mScreenHeight = 2400;
    private boolean mIsAttached = false;

    public CursorOverlayView(Context context) {
        super(context);
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        Point size = new Point();
        if (mWindowManager != null && mWindowManager.getDefaultDisplay() != null) {
            mWindowManager.getDefaultDisplay().getRealSize(size);
            mScreenWidth = size.x;
            mScreenHeight = size.y;
            mX = mScreenWidth / 2f;
            mY = mScreenHeight / 2f;
        }

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        mParams = new WindowManager.LayoutParams(
                64, 64,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        mParams.gravity = Gravity.TOP | Gravity.START;
        mParams.x = (int) mX;
        mParams.y = (int) mY;

        mPaintPointer = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintPointer.setColor(0xFFFFFFFF);
        mPaintPointer.setStyle(Paint.Style.FILL);

        mPaintStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintStroke.setColor(0xFF000000);
        mPaintStroke.setStyle(Paint.Style.STROKE);
        mPaintStroke.setStrokeWidth(3f);

        mPointerPath = new Path();
        // Modern arrow cursor path
        mPointerPath.moveTo(0f, 0f);
        mPointerPath.lineTo(0f, 44f);
        mPointerPath.lineTo(12f, 32f);
        mPointerPath.lineTo(24f, 56f);
        mPointerPath.lineTo(32f, 52f);
        mPointerPath.lineTo(20f, 28f);
        mPointerPath.lineTo(36f, 28f);
        mPointerPath.close();
    }

    public synchronized void show() {
        if (!mIsAttached && mWindowManager != null) {
            try {
                mWindowManager.addView(this, mParams);
                mIsAttached = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void hide() {
        if (mIsAttached && mWindowManager != null) {
            try {
                mWindowManager.removeView(this);
                mIsAttached = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void moveDelta(int dx, int dy) {
        mX = Math.max(0, Math.min(mScreenWidth - 1, mX + dx));
        mY = Math.max(0, Math.min(mScreenHeight - 1, mY + dy));

        if (mIsAttached && mWindowManager != null) {
            mParams.x = (int) mX;
            mParams.y = (int) mY;
            post(() -> {
                if (mIsAttached) {
                    try {
                        mWindowManager.updateViewLayout(this, mParams);
                    } catch (Exception ignored) {}
                }
            });
        }
    }

    public float getCursorX() {
        return mX;
    }

    public float getCursorY() {
        return mY;
    }

    public int getScreenWidth() {
        return mScreenWidth;
    }

    public int getScreenHeight() {
        return mScreenHeight;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(mPointerPath, mPaintPointer);
        canvas.drawPath(mPointerPath, mPaintStroke);
    }
}
