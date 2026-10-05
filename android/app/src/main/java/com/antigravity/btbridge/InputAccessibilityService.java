package com.antigravity.btbridge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

public class InputAccessibilityService extends AccessibilityService {
    private static InputAccessibilityService sInstance = null;

    public static InputAccessibilityService getInstance() {
        return sInstance;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        AppLogger.i("Accessibility", "InputAccessibilityService connected and ready");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        sInstance = null;
        AppLogger.w("Accessibility", "InputAccessibilityService unbound");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
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
        AccessibilityNodeInfo focused = getFocusedInputNode();
        if (focused == null) {
            AppLogger.d("Accessibility", "No focused input node found for key: " + androidKeycode);
            return;
        }

        try {
            CharSequence current = focused.getText();
            int selStart = focused.getTextSelectionStart();
            int selEnd = focused.getTextSelectionEnd();
            int len = (current != null) ? current.length() : 0;

            if (selStart < 0 || selEnd < 0) {
                selStart = len;
                selEnd = len;
            }

            // 1. BACKSPACE (KEYCODE_DEL = 67)
            if (androidKeycode == 67) {
                if (current != null && len > 0) {
                    StringBuilder sb = new StringBuilder(current);
                    int newCursor = selStart;

                    if (selStart != selEnd) {
                        int min = Math.min(selStart, selEnd);
                        int max = Math.max(selStart, selEnd);
                        sb.delete(min, max);
                        newCursor = min;
                    } else if (selStart > 0) {
                        sb.delete(selStart - 1, selStart);
                        newCursor = selStart - 1;
                    } else {
                        return; // At beginning, nothing to delete
                    }

                    applyTextAndSelection(focused, sb.toString(), newCursor);
                    AppLogger.d("Accessibility", "Executed Backspace at position " + newCursor);
                }
                return;
            }

            // 2. DELETE (FORWARD DELETE = 112)
            if (androidKeycode == 112) {
                if (current != null && len > 0) {
                    StringBuilder sb = new StringBuilder(current);
                    int newCursor = selStart;

                    if (selStart != selEnd) {
                        int min = Math.min(selStart, selEnd);
                        int max = Math.max(selStart, selEnd);
                        sb.delete(min, max);
                        newCursor = min;
                    } else if (selStart < len) {
                        sb.delete(selStart, selStart + 1);
                        newCursor = selStart;
                    } else {
                        return; // At end, nothing forward to delete
                    }

                    applyTextAndSelection(focused, sb.toString(), newCursor);
                    AppLogger.d("Accessibility", "Executed Forward Delete at position " + newCursor);
                }
                return;
            }

            // 3. ENTER (KEYCODE_ENTER = 66)
            if (androidKeycode == 66) {
                StringBuilder sb = new StringBuilder(current != null ? current : "");
                int min = Math.min(selStart, selEnd);
                int max = Math.max(selStart, selEnd);
                sb.replace(min, max, "\n");
                int newCursor = min + 1;
                applyTextAndSelection(focused, sb.toString(), newCursor);
                AppLogger.d("Accessibility", "Executed Enter newline at position " + newCursor);
                return;
            }

            // 4. ARROW LEFT (KEYCODE_DPAD_LEFT = 21)
            if (androidKeycode == 21) {
                int newCursor = Math.max(0, selStart - 1);
                setCursorPosition(focused, newCursor);
                return;
            }

            // 5. ARROW RIGHT (KEYCODE_DPAD_RIGHT = 22)
            if (androidKeycode == 22) {
                int newCursor = Math.min(len, selStart + 1);
                setCursorPosition(focused, newCursor);
                return;
            }

            // 6. HOME (KEYCODE_MOVE_HOME = 122)
            if (androidKeycode == 122) {
                setCursorPosition(focused, 0);
                return;
            }

            // 7. END (KEYCODE_MOVE_END = 123)
            if (androidKeycode == 123) {
                setCursorPosition(focused, len);
                return;
            }

            // 8. REGULAR UNICODE CHARACTER TYPING
            if (unicodeChar != 0 && !Character.isISOControl(unicodeChar)) {
                String insertStr = String.valueOf(unicodeChar);
                StringBuilder sb = new StringBuilder(current != null ? current : "");
                int min = Math.min(selStart, selEnd);
                int max = Math.max(selStart, selEnd);
                sb.replace(min, max, insertStr);
                int newCursor = min + insertStr.length();
                applyTextAndSelection(focused, sb.toString(), newCursor);
            }
        } finally {
            focused.recycle();
        }
    }

    private void applyTextAndSelection(AccessibilityNodeInfo node, String newText, int cursor) {
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText);
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);

        setCursorPosition(node, cursor);
    }

    private void setCursorPosition(AccessibilityNodeInfo node, int cursor) {
        Bundle selArgs = new Bundle();
        selArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor);
        selArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor);
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs);
    }

    private AccessibilityNodeInfo getFocusedInputNode() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            AccessibilityNodeInfo focused = findInTree(root);
            root.recycle();
            if (focused != null) return focused;
        }

        // Fallback: check interactive windows
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) {
                    if (window.isFocused() || window.isActive()) {
                        AccessibilityNodeInfo wRoot = window.getRoot();
                        if (wRoot != null) {
                            AccessibilityNodeInfo focused = findInTree(wRoot);
                            wRoot.recycle();
                            if (focused != null) return focused;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private AccessibilityNodeInfo findInTree(AccessibilityNodeInfo root) {
        // 1. Standard Input Focus
        AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (f != null) return f;

        // 2. Accessibility Focus
        f = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        if (f != null && (f.isEditable() || f.isFocused())) return f;
        if (f != null) f.recycle();

        // 3. Search children recursively
        return searchRecursive(root);
    }

    private AccessibilityNodeInfo searchRecursive(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isFocused() && node.isEditable()) {
            return AccessibilityNodeInfo.obtain(node);
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo res = searchRecursive(child);
                child.recycle();
                if (res != null) return res;
            }
        }
        return null;
    }
}
