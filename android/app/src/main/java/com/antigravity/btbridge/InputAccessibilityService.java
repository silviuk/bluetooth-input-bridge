package com.antigravity.btbridge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class InputAccessibilityService extends AccessibilityService {
    private static InputAccessibilityService sInstance = null;

    public static InputAccessibilityService getInstance() {
        return sInstance;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        sInstance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Can be used to track focused node if needed
    }

    @Override
    public void onInterrupt() {
    }

    public void dispatchClick(float x, float y) {
        Path clickPath = new Path();
        clickPath.moveTo(x, y);
        clickPath.lineTo(x, y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(clickPath, 0, 40);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        dispatchGesture(builder.build(), null, null);
    }

    public void dispatchScroll(float x, float y, int deltaY) {
        Path scrollPath = new Path();
        scrollPath.moveTo(x, y);
        // Scroll distance scaled
        float targetY = y + (deltaY > 0 ? 300 : -300);
        scrollPath.lineTo(x, targetY);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(scrollPath, 0, 150);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        dispatchGesture(builder.build(), null, null);
    }

    public void performAction(byte action) {
        switch (action) {
            case Protocol.ACT_BACK:
                performGlobalAction(GLOBAL_ACTION_BACK);
                break;
            case Protocol.ACT_HOME:
                performGlobalAction(GLOBAL_ACTION_HOME);
                break;
            case Protocol.ACT_RECENTS:
                performGlobalAction(GLOBAL_ACTION_RECENTS);
                break;
            case Protocol.ACT_NOTIFICATIONS:
                performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
                break;
            case Protocol.ACT_LOCK_SCREEN:
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                break;
        }
    }

    public void injectTextOrKey(int androidKeycode, char unicodeChar) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) {
            root.recycle();
            return;
        }

        if (androidKeycode == 67) { // KEYCODE_DEL (Backspace)
            CharSequence current = focused.getText();
            if (current != null && current.length() > 0) {
                CharSequence updated = current.subSequence(0, current.length() - 1);
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated);
                focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            }
        } else if (unicodeChar != 0 && !Character.isISOControl(unicodeChar)) {
            CharSequence current = focused.getText();
            String appendStr = String.valueOf(unicodeChar);
            CharSequence updated = (current == null) ? appendStr : (current.toString() + appendStr);
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated);
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        }

        focused.recycle();
        root.recycle();
    }
}
