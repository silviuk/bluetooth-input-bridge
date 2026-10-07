package com.antigravity.btbridge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.EditorInfo;

import java.util.ArrayList;
import java.util.List;

public class InputAccessibilityService extends AccessibilityService {
    private static InputAccessibilityService sInstance = null;

    // Fast typing buffer cache (eliminates IPC lag, dropped chars, and WhatsApp placeholder race conditions)
    private String mCachedText = null;
    private int mCachedCursor = -1;
    private int mCachedSelStart = -1;
    private int mCachedSelEnd = -1;
    private int mCachedNodeWindowId = -1;
    private long mLastKeyTypeTime = 0;

    // Anchor-based text selection tracking (Shift + Arrows, Ctrl + Shift + Arrows)
    private int mSelectionAnchor = -1;
    private int mSelectionCaret = -1;

    // UI Element Tab navigation focus tracking
    private AccessibilityNodeInfo mTabActiveNode = null;

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
        if (mTabActiveNode != null) {
            mTabActiveNode.recycle();
            mTabActiveNode = null;
        }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_VIEW_FOCUSED || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Window or focus changed; invalidate typing cache
            mCachedText = null;
            mCachedCursor = -1;
            mCachedNodeWindowId = -1;
            mSelectionAnchor = -1;
            mSelectionCaret = -1;
            if (mTabActiveNode != null) {
                mTabActiveNode.recycle();
                mTabActiveNode = null;
            }
        }
    }

    @Override
    public void onInterrupt() {
    }

    public void dispatchClick(float x, float y) {
        cancelAltTab();
        Path clickPath = new Path();
        clickPath.moveTo(x, y);
        clickPath.lineTo(x, y + 1.0f); // Non-zero length required by Android GestureDescription

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(clickPath, 0, 50);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);

        boolean dispatched = dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                AppLogger.d("Accessibility", "dispatchClick completed at (" + x + ", " + y + ")");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                AppLogger.w("Accessibility", "dispatchClick cancelled at (" + x + ", " + y + "), attempting node click fallback");
                clickNodeAt(x, y);
            }
        }, null);

        if (!dispatched) {
            AppLogger.w("Accessibility", "dispatchGesture returned false at (" + x + ", " + y + "), attempting node click fallback");
            clickNodeAt(x, y);
        }
    }

    public boolean clickNodeAt(float x, float y) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            AccessibilityNodeInfo target = findClickableNodeAt(root, (int) x, (int) y);
            if (target != null) {
                try {
                    boolean ok = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    AppLogger.d("Accessibility", "clickNodeAt (" + x + ", " + y + ") ok=" + ok);
                    return ok;
                } finally {
                    target.recycle();
                }
            }
        } finally {
            root.recycle();
        }
        return false;
    }

    private AccessibilityNodeInfo findClickableNodeAt(AccessibilityNodeInfo node, int x, int y) {
        if (node == null) return null;
        android.graphics.Rect bounds = new android.graphics.Rect();
        node.getBoundsInScreen(bounds);
        if (!bounds.contains(x, y)) {
            return null;
        }

        // Search children in reverse z-order (topmost child first)
        int count = node.getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo hit = findClickableNodeAt(child, x, y);
                if (hit != null) {
                    return hit;
                }
                child.recycle();
            }
        }

        if (node.isClickable()) {
            return AccessibilityNodeInfo.obtain(node);
        }
        return null;
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
        dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                AppLogger.d("Accessibility", "Scroll gesture completed");
            }
            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                AppLogger.w("Accessibility", "Scroll gesture cancelled");
            }
        }, null);
    }

    public void dispatchSwipe(float fromX, float fromY, float toX, float toY, long durationMs) {
        Path path = new Path();
        path.moveTo(fromX, fromY);
        path.lineTo(toX, toY);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, Math.max(80, durationMs));
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                AppLogger.d("Accessibility", "Swipe gesture completed");
            }
            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                AppLogger.w("Accessibility", "Swipe gesture cancelled");
            }
        }, null);
    }

    public void adjustVolume(int direction) {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.adjustVolume(direction, AudioManager.FLAG_SHOW_UI);
                AppLogger.d("Accessibility", "Adjusted volume direction: " + direction);
            }
        } catch (Exception e) {
            AppLogger.w("Accessibility", "Failed to adjust volume: " + e.getMessage());
        }
    }

    public void sendMediaKey(int keyCode) {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
                AppLogger.d("Accessibility", "Dispatched media key: " + keyCode);
            }
        } catch (Exception e) {
            AppLogger.w("Accessibility", "Failed to dispatch media key: " + e.getMessage());
        }
    }

    public void performAction(byte action) {
        switch (action) {
            case Protocol.ACT_BACK:
                boolean backOk = performGlobalAction(GLOBAL_ACTION_BACK);
                AppLogger.i("Accessibility", "Executed GLOBAL_ACTION_BACK: ok=" + backOk);
                break;
            case Protocol.ACT_HOME:
                boolean homeOk = performGlobalAction(GLOBAL_ACTION_HOME);
                AppLogger.i("Accessibility", "Executed GLOBAL_ACTION_HOME: ok=" + homeOk);
                break;
            case Protocol.ACT_RECENTS:
                boolean recOk = performGlobalAction(GLOBAL_ACTION_RECENTS);
                AppLogger.i("Accessibility", "Executed GLOBAL_ACTION_RECENTS: ok=" + recOk);
                break;
            case Protocol.ACT_NOTIFICATIONS:
                boolean notifOk = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
                AppLogger.i("Accessibility", "Executed GLOBAL_ACTION_NOTIFICATIONS: ok=" + notifOk);
                break;
            case Protocol.ACT_LOCK_SCREEN:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                }
                break;
            case Protocol.ACT_SCREENSHOT:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
                    AppLogger.i("Accessibility", "Captured screenshot via GLOBAL_ACTION_TAKE_SCREENSHOT");
                }
                break;
            case Protocol.ACT_QUICK_SETTINGS:
                performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
                break;
            case Protocol.ACT_VOLUME_UP:
                adjustVolume(AudioManager.ADJUST_RAISE);
                break;
            case Protocol.ACT_VOLUME_DOWN:
                adjustVolume(AudioManager.ADJUST_LOWER);
                break;
            case Protocol.ACT_VOLUME_MUTE:
                adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE);
                break;
            case Protocol.ACT_MEDIA_PLAY_PAUSE:
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                break;
            case Protocol.ACT_MEDIA_NEXT:
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
                break;
            case Protocol.ACT_MEDIA_PREV:
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                break;
        }
    }

    public void injectTextOrKey(int androidKeycode, char unicodeChar) {
        injectTextOrKey(androidKeycode, unicodeChar, (byte) 0);
    }

    public void injectTextOrKey(int androidKeycode, char unicodeChar, byte modifiers) {
        BridgeSettings settings = BridgeSettings.getInstance(this);
        boolean isCtrl  = (modifiers & Protocol.MOD_CTRL) != 0;
        boolean isShift = (modifiers & Protocol.MOD_SHIFT) != 0;

        // 0. Tab Focus Element Click: If an element was focused via Tab, Enter or Space clicks it!
        if (mTabActiveNode != null) {
            if (androidKeycode == 61) { // Tab continues cycling
                handleTabNavigation(isShift);
                return;
            } else if (androidKeycode == 66 || androidKeycode == 62) { // Enter or Space activates
                performClickSafe(mTabActiveNode);
                android.graphics.Rect r = new android.graphics.Rect();
                mTabActiveNode.getBoundsInScreen(r);
                if (r.width() > 0 && r.height() > 0) {
                    dispatchClick(r.centerX(), r.centerY());
                }
                AppLogger.i("Accessibility", "Clicked tab-focused element with key " + androidKeycode);
                mTabActiveNode.recycle();
                mTabActiveNode = null;
                return;
            } else if (androidKeycode == 111 || androidKeycode == 4) { // Esc / Back cancels tab focus
                mTabActiveNode.recycle();
                mTabActiveNode = null;
                return;
            }
        }

        // Ctrl + Enter: Trigger Send in WhatsApp and messaging apps
        if (isCtrl && androidKeycode == 66) {
            if (triggerSendAction()) {
                return;
            }
        }

        // Tab Navigation from outside an input box
        if (androidKeycode == 61 && settings.getTabMode() == BridgeSettings.TAB_MODE_FOCUS) {
            handleTabNavigation(isShift);
            return;
        }

        AccessibilityNodeInfo focused = getFocusedInputNode();
        if (focused == null) {
            // If no editable node found, check if user pressed Enter/Space on a clickable view
            AccessibilityNodeInfo nonEditable = getFocusedClickableNode();
            if (nonEditable != null) {
                try {
                    if (androidKeycode == 66 || androidKeycode == 62) { // Enter or Space
                        nonEditable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        AppLogger.d("Accessibility", "Clicked focused clickable node with key " + androidKeycode);
                    }
                } finally {
                    nonEditable.recycle();
                }
            } else {
                AppLogger.d("Accessibility", "No focused node found for key: " + androidKeycode);
            }
            return;
        }

        try {
            long now = SystemClock.uptimeMillis();
            boolean useCache = (mCachedText != null && (now - mLastKeyTypeTime < 1200) && (focused.getWindowId() == mCachedNodeWindowId));
            CharSequence current;
            int selStart;
            int selEnd;

            if (useCache) {
                current = mCachedText;
                selStart = (mCachedSelStart >= 0 && mCachedSelStart <= mCachedText.length()) ? mCachedSelStart : mCachedText.length();
                selEnd = (mCachedSelEnd >= 0 && mCachedSelEnd <= mCachedText.length()) ? mCachedSelEnd : mCachedText.length();
            } else {
                current = focused.getText();
                CharSequence hintText = focused.getHintText();
                boolean isShowingHint = focused.isShowingHintText();
                selStart = focused.getTextSelectionStart();
                selEnd = focused.getTextSelectionEnd();

                // Detect if current text is background placeholder/hint text (e.g. WhatsApp "Message" or "Type a message")
                boolean isPlaceholder = false;
                if (settings.isIgnorePlaceholdersEnabled()) {
                    if (isShowingHint) {
                        isPlaceholder = true;
                    } else if (hintText != null && current != null) {
                        String curStr = current.toString().trim();
                        String hStr = hintText.toString().trim();
                        if (curStr.equalsIgnoreCase(hStr)) {
                            isPlaceholder = true;
                        }
                    }
                    if (!isPlaceholder && current != null) {
                        String curStr = current.toString().trim();
                        if (curStr.equalsIgnoreCase("Message") ||
                            curStr.equalsIgnoreCase("Type a message") ||
                            curStr.equalsIgnoreCase("Write a message...") ||
                            curStr.equalsIgnoreCase("Send a message") ||
                            curStr.equalsIgnoreCase("Search") ||
                            curStr.equalsIgnoreCase("Search...")) {
                            isPlaceholder = true;
                        }
                    }
                }

                if (isPlaceholder) {
                    current = "";
                    selStart = 0;
                    selEnd = 0;
                } else {
                    int l = (current != null) ? current.length() : 0;
                    if (selStart < 0 || selEnd < 0) {
                        selStart = l;
                        selEnd = l;
                    }
                }
            }
            int len = (current != null) ? current.length() : 0;

            // ================= 1. CTRL + SHIFT COMBINATIONS =================
            if (isCtrl && isShift && settings.isCtrlShortcutsEnabled()) {
                // Ctrl + Shift + Left Arrow (Select Word Backward)
                if (androidKeycode == 21 && current != null) {
                    if (mSelectionAnchor < 0) {
                        mSelectionAnchor = Math.max(selStart, selEnd);
                        mSelectionCaret = Math.min(selStart, selEnd);
                    }
                    mSelectionCaret = findPrevWordBoundary(current, mSelectionCaret);
                    setSelectionRange(focused, Math.min(mSelectionAnchor, mSelectionCaret), Math.max(mSelectionAnchor, mSelectionCaret));
                    return;
                }
                // Ctrl + Shift + Right Arrow (Select Word Forward)
                if (androidKeycode == 22 && current != null) {
                    if (mSelectionAnchor < 0) {
                        mSelectionAnchor = Math.min(selStart, selEnd);
                        mSelectionCaret = Math.max(selStart, selEnd);
                    }
                    mSelectionCaret = findNextWordBoundary(current, mSelectionCaret);
                    setSelectionRange(focused, Math.min(mSelectionAnchor, mSelectionCaret), Math.max(mSelectionAnchor, mSelectionCaret));
                    return;
                }
                // Ctrl + Shift + Home (Select to Start of Document)
                if (androidKeycode == 122) {
                    if (mSelectionAnchor < 0) {
                        mSelectionAnchor = Math.max(selStart, selEnd);
                    }
                    mSelectionCaret = 0;
                    setSelectionRange(focused, 0, mSelectionAnchor);
                    return;
                }
                // Ctrl + Shift + End (Select to End of Document)
                if (androidKeycode == 123) {
                    if (mSelectionAnchor < 0) {
                        mSelectionAnchor = Math.min(selStart, selEnd);
                    }
                    mSelectionCaret = len;
                    setSelectionRange(focused, mSelectionAnchor, len);
                    return;
                }
            }

            // ================= 2. WINDOWS CTRL SHORTCUTS =================
            if (isCtrl) {
                if (settings.isCtrlShortcutsEnabled()) {
                    // Ctrl + A (Select All)
                    if (androidKeycode == 29) { // KEYCODE_A
                        mSelectionAnchor = 0;
                        mSelectionCaret = len;
                        setSelectionRange(focused, 0, len);
                        AppLogger.d("Accessibility", "Executed Ctrl+A (Select All)");
                        return;
                    }
                    // Ctrl + C (Copy) or Ctrl + Insert
                    if (androidKeycode == 31 || androidKeycode == 124) { // KEYCODE_C or KEYCODE_INSERT
                        int min = Math.min(selStart, selEnd);
                        int max = Math.max(selStart, selEnd);
                        String textToCopy = "";
                        if (min != max && current != null) {
                            textToCopy = current.subSequence(min, max).toString();
                        } else if (current != null) {
                            textToCopy = current.toString();
                        }
                        if (!textToCopy.isEmpty()) {
                            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.setPrimaryClip(ClipData.newPlainText("text", textToCopy));
                            }
                        }
                        focused.performAction(AccessibilityNodeInfo.ACTION_COPY);
                        AppLogger.d("Accessibility", "Executed Ctrl+C (Copy)");
                        return;
                    }
                    // Ctrl + X (Cut)
                    if (androidKeycode == 52) { // KEYCODE_X
                        int min = Math.min(selStart, selEnd);
                        int max = Math.max(selStart, selEnd);
                        if (min != max && current != null) {
                            String textToCut = current.subSequence(min, max).toString();
                            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.setPrimaryClip(ClipData.newPlainText("text", textToCut));
                            }
                            boolean cutOk = focused.performAction(AccessibilityNodeInfo.ACTION_CUT);
                            if (!cutOk) {
                                StringBuilder sb = new StringBuilder(current);
                                sb.delete(min, max);
                                applyTextAndSelection(focused, sb.toString(), min);
                            } else {
                                mCachedText = null;
                            }
                            mSelectionAnchor = -1;
                            mSelectionCaret = -1;
                            AppLogger.d("Accessibility", "Executed Ctrl+X (Cut)");
                        }
                        return;
                    }
                    // Ctrl + V (Paste)
                    if (androidKeycode == 50) { // KEYCODE_V
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        String clipText = "";
                        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                            CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                            if (text != null) clipText = text.toString();
                        }
                        boolean pasteOk = focused.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                        if (!pasteOk && !clipText.isEmpty()) {
                            StringBuilder sb = new StringBuilder(current != null ? current : "");
                            int min = Math.min(selStart, selEnd);
                            int max = Math.max(selStart, selEnd);
                            sb.replace(min, max, clipText);
                            int newCursor = min + clipText.length();
                            applyTextAndSelection(focused, sb.toString(), newCursor);
                        } else {
                            mCachedText = null;
                        }
                        mSelectionAnchor = -1;
                        mSelectionCaret = -1;
                        AppLogger.d("Accessibility", "Executed Ctrl+V (Paste)");
                        return;
                    }
                    // Ctrl + Z (Undo)
                    if (androidKeycode == 54) { // KEYCODE_Z
                        focused.performAction(android.R.id.undo);
                        mCachedText = null;
                        mSelectionAnchor = -1;
                        mSelectionCaret = -1;
                        AppLogger.d("Accessibility", "Executed Ctrl+Z (Undo)");
                        return;
                    }
                    // Ctrl + Y (Redo)
                    if (androidKeycode == 53) { // KEYCODE_Y
                        focused.performAction(android.R.id.redo);
                        mCachedText = null;
                        mSelectionAnchor = -1;
                        mSelectionCaret = -1;
                        AppLogger.d("Accessibility", "Executed Ctrl+Y (Redo)");
                        return;
                    }
                    // Ctrl + Home (Jump to Start of Document)
                    if (androidKeycode == 122) {
                        mSelectionAnchor = -1;
                        mSelectionCaret = -1;
                        setCursorPosition(focused, 0);
                        return;
                    }
                    // Ctrl + End (Jump to End of Document)
                    if (androidKeycode == 123) {
                        mSelectionAnchor = -1;
                        mSelectionCaret = -1;
                        setCursorPosition(focused, len);
                        return;
                    }
                    // Ctrl + Backspace (Delete Word Backward)
                    if (androidKeycode == 67 && current != null && selStart > 0) {
                        int prevWord = findPrevWordBoundary(current, selStart);
                        StringBuilder sb = new StringBuilder(current);
                        sb.delete(prevWord, selStart);
                        applyTextAndSelection(focused, sb.toString(), prevWord);
                        mSelectionAnchor = -1;
                        mSelectionCaret = -1;
                        AppLogger.d("Accessibility", "Executed Ctrl+Backspace (Delete Word Backward)");
                        return;
                    }
                    // Ctrl + Delete (Delete Word Forward)
                    if (androidKeycode == 112 && current != null && selStart < len) {
                        int nextWord = findNextWordBoundary(current, selStart);
                        StringBuilder sb = new StringBuilder(current);
                        sb.delete(selStart, nextWord);
                        applyTextAndSelection(focused, sb.toString(), selStart);
                        mSelectionAnchor = -1;
                        mSelectionCaret = -1;
                        AppLogger.d("Accessibility", "Executed Ctrl+Delete (Delete Word Forward)");
                        return;
                    }
                    // Ctrl + Left (Jump Word Backward)
                    if (androidKeycode == 21 && current != null) {
                        int prevWord = findPrevWordBoundary(current, selStart);
                        mSelectionAnchor = -1;
                        mSelectionCaret = -1;
                        setCursorPosition(focused, prevWord);
                        return;
                    }
                    // Ctrl + Right (Jump Word Forward)
                    if (androidKeycode == 22 && current != null) {
                        int nextWord = findNextWordBoundary(current, selStart);
                        mSelectionAnchor = -1;
                        mSelectionCaret = -1;
                        setCursorPosition(focused, nextWord);
                        return;
                    }
                }
                // Suppress any other Ctrl key combination from typing characters
                return;
            }

            // ================= 3. SHIFT + NAVIGATION (TEXT SELECTION) =================
            if (isShift) {
                // Shift + Delete (Windows Cut shortcut)
                if (androidKeycode == 112 && selStart != selEnd && current != null) {
                    int min = Math.min(selStart, selEnd);
                    int max = Math.max(selStart, selEnd);
                    String textToCut = current.subSequence(min, max).toString();
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("text", textToCut));
                    }
                    boolean cutOk = focused.performAction(AccessibilityNodeInfo.ACTION_CUT);
                    if (!cutOk) {
                        StringBuilder sb = new StringBuilder(current);
                        sb.delete(min, max);
                        applyTextAndSelection(focused, sb.toString(), min);
                    }
                    mSelectionAnchor = -1;
                    mSelectionCaret = -1;
                    return;
                }
                // Shift + Insert (Windows Paste shortcut)
                if (androidKeycode == 124) {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    String clipText = "";
                    if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                        CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                        if (text != null) clipText = text.toString();
                    }
                    if (!clipText.isEmpty()) {
                        StringBuilder sb = new StringBuilder(current != null ? current : "");
                        int min = Math.min(selStart, selEnd);
                        int max = Math.max(selStart, selEnd);
                        sb.replace(min, max, clipText);
                        int newCursor = min + clipText.length();
                        applyTextAndSelection(focused, sb.toString(), newCursor);
                    }
                    mSelectionAnchor = -1;
                    mSelectionCaret = -1;
                    return;
                }
                // Shift + Left Arrow
                if (androidKeycode == 21) {
                    if (mSelectionAnchor < 0) {
                        mSelectionAnchor = Math.max(selStart, selEnd);
                        mSelectionCaret = Math.min(selStart, selEnd);
                    }
                    mSelectionCaret = Math.max(0, mSelectionCaret - 1);
                    setSelectionRange(focused, Math.min(mSelectionAnchor, mSelectionCaret), Math.max(mSelectionAnchor, mSelectionCaret));
                    return;
                }
                // Shift + Right Arrow
                if (androidKeycode == 22) {
                    if (mSelectionAnchor < 0) {
                        mSelectionAnchor = Math.min(selStart, selEnd);
                        mSelectionCaret = Math.max(selStart, selEnd);
                    }
                    mSelectionCaret = Math.min(len, mSelectionCaret + 1);
                    setSelectionRange(focused, Math.min(mSelectionAnchor, mSelectionCaret), Math.max(mSelectionAnchor, mSelectionCaret));
                    return;
                }
                // Shift + Up Arrow
                if (androidKeycode == 19 && current != null) {
                    if (mSelectionAnchor < 0) {
                        mSelectionAnchor = Math.max(selStart, selEnd);
                        mSelectionCaret = Math.min(selStart, selEnd);
                    }
                    mSelectionCaret = moveLineUp(current, mSelectionCaret);
                    setSelectionRange(focused, Math.min(mSelectionAnchor, mSelectionCaret), Math.max(mSelectionAnchor, mSelectionCaret));
                    return;
                }
                // Shift + Down Arrow
                if (androidKeycode == 20 && current != null) {
                    if (mSelectionAnchor < 0) {
                        mSelectionAnchor = Math.min(selStart, selEnd);
                        mSelectionCaret = Math.max(selStart, selEnd);
                    }
                    mSelectionCaret = moveLineDown(current, mSelectionCaret);
                    setSelectionRange(focused, Math.min(mSelectionAnchor, mSelectionCaret), Math.max(mSelectionAnchor, mSelectionCaret));
                    return;
                }
                // Shift + Home (Select to Start of Line)
                if (androidKeycode == 122) {
                    if (mSelectionAnchor < 0) {
                        mSelectionAnchor = Math.max(selStart, selEnd);
                        mSelectionCaret = Math.min(selStart, selEnd);
                    }
                    int lineStart = (current != null) ? current.toString().lastIndexOf('\n', Math.max(0, mSelectionCaret - 1)) + 1 : 0;
                    if (lineStart < 0) lineStart = 0;
                    mSelectionCaret = lineStart;
                    setSelectionRange(focused, Math.min(mSelectionAnchor, mSelectionCaret), Math.max(mSelectionAnchor, mSelectionCaret));
                    return;
                }
                // Shift + End (Select to End of Line)
                if (androidKeycode == 123) {
                    if (mSelectionAnchor < 0) {
                        mSelectionAnchor = Math.min(selStart, selEnd);
                        mSelectionCaret = Math.max(selStart, selEnd);
                    }
                    int lineEnd = (current != null) ? current.toString().indexOf('\n', mSelectionCaret) : len;
                    if (lineEnd < 0) lineEnd = len;
                    mSelectionCaret = lineEnd;
                    setSelectionRange(focused, Math.min(mSelectionAnchor, mSelectionCaret), Math.max(mSelectionAnchor, mSelectionCaret));
                    return;
                }
            } else {
                mSelectionAnchor = -1;
                mSelectionCaret = -1;
            }

            // ================= 4. BACKSPACE (KEYCODE_DEL = 67) =================
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
                        return; // At beginning
                    }

                    applyTextAndSelection(focused, sb.toString(), newCursor);
                    AppLogger.d("Accessibility", "Executed Backspace at position " + newCursor);
                }
                return;
            }

            // ================= 5. DELETE (FORWARD DELETE = 112) =================
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
                        return; // At end
                    }

                    applyTextAndSelection(focused, sb.toString(), newCursor);
                    AppLogger.d("Accessibility", "Executed Forward Delete at position " + newCursor);
                }
                return;
            }

            // ================= 6. ENTER (KEYCODE_ENTER = 66) =================
            if (androidKeycode == 66) {
                int enterMode = settings.getEnterMode();
                CharSequence pkg = focused.getPackageName();
                String pkgStr = (pkg != null) ? pkg.toString().toLowerCase() : "";
                boolean isKeep = pkgStr.contains("keep");
                CharSequence hint = focused.getHintText();
                String hintStr = (hint != null) ? hint.toString().toLowerCase() : "";
                String idStr = (focused.getViewIdResourceName() != null) ? focused.getViewIdResourceName().toLowerCase() : "";
                boolean isSearchField = hintStr.contains("search") || hintStr.contains("find") ||
                                        idStr.contains("search") || idStr.contains("query") || idStr.contains("url") || idStr.contains("address");

                if (isShift) {
                    if (enterMode == BridgeSettings.ENTER_MODE_ACTION) {
                        insertNewlineOnNode(focused, current, selStart, selEnd);
                        return;
                    } else {
                        if (triggerSendAction()) {
                            return;
                        }
                    }
                }

                if (isKeep) {
                    // Google Keep note editor handling
                    if (idStr.contains("title")) {
                        boolean advanced = focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
                        if (!advanced) {
                            AccessibilityNodeInfo next = focused.focusSearch(View.FOCUS_DOWN);
                            if (next != null) {
                                next.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                                next.recycle();
                            }
                        }
                    } else if (idStr.contains("description") || idStr.contains("list") || idStr.contains("item")) {
                        boolean ok = focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
                        if (!ok) {
                            insertNewlineOnNode(focused, current, selStart, selEnd);
                        }
                    } else {
                        insertNewlineOnNode(focused, current, selStart, selEnd);
                    }
                    AppLogger.d("Accessibility", "Executed Enter in Google Keep");
                    return;
                }

                if (enterMode == BridgeSettings.ENTER_MODE_NEWLINE ||
                    (enterMode == BridgeSettings.ENTER_MODE_SMART && !isSearchField) ||
                    focused.isMultiLine()) {
                    insertNewlineOnNode(focused, current, selStart, selEnd);
                    AppLogger.d("Accessibility", "Executed Enter newline");
                } else {
                    if (!triggerSendAction()) {
                        boolean clicked = clickNearbyActionOrButton(focused);
                        if (!clicked) {
                            boolean imeEntered = focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
                            if (!imeEntered) {
                                insertNewlineOnNode(focused, current, selStart, selEnd);
                            }
                        }
                    }
                    AppLogger.d("Accessibility", "Executed Enter action (Submit/Click)");
                }
                return;
            }

            // ================= 7. TAB (KEYCODE_TAB = 61) =================
            if (androidKeycode == 61) {
                int tabMode = settings.getTabMode();
                if (tabMode == BridgeSettings.TAB_MODE_FOCUS) {
                    handleTabNavigation(isShift);
                } else {
                    // Text insertion
                    String tabStr = (tabMode == BridgeSettings.TAB_MODE_SPACES) ? "    " : "\t";
                    StringBuilder sb = new StringBuilder(current != null ? current : "");
                    int min = Math.min(selStart, selEnd);
                    int max = Math.max(selStart, selEnd);
                    sb.replace(min, max, tabStr);
                    int newCursor = min + tabStr.length();
                    applyTextAndSelection(focused, sb.toString(), newCursor);
                    AppLogger.d("Accessibility", "Executed Tab text insertion");
                }
                return;
            }

            // ================= 8. ARROW NAVIGATION =================
            // Arrow Left (21)
            if (androidKeycode == 21) {
                mSelectionAnchor = -1;
                mSelectionCaret = -1;
                int newCursor = (selStart != selEnd) ? Math.min(selStart, selEnd) : Math.max(0, selStart - 1);
                setCursorPosition(focused, newCursor);
                return;
            }

            // Arrow Right (22)
            if (androidKeycode == 22) {
                mSelectionAnchor = -1;
                mSelectionCaret = -1;
                int newCursor = (selStart != selEnd) ? Math.max(selStart, selEnd) : Math.min(len, selStart + 1);
                setCursorPosition(focused, newCursor);
                return;
            }

            // Arrow Up (19)
            if (androidKeycode == 19) {
                mSelectionAnchor = -1;
                mSelectionCaret = -1;
                if (current != null && current.length() > 0) {
                    int newCursor = moveLineUp(current, selStart);
                    setCursorPosition(focused, newCursor);
                }
                return;
            }

            // Arrow Down (20)
            if (androidKeycode == 20) {
                mSelectionAnchor = -1;
                mSelectionCaret = -1;
                if (current != null && current.length() > 0) {
                    int newCursor = moveLineDown(current, selStart);
                    setCursorPosition(focused, newCursor);
                }
                return;
            }

            // Home (122)
            if (androidKeycode == 122) {
                mSelectionAnchor = -1;
                mSelectionCaret = -1;
                int lineStart = (current != null) ? current.toString().lastIndexOf('\n', Math.max(0, selStart - 1)) + 1 : 0;
                if (lineStart < 0) lineStart = 0;
                setCursorPosition(focused, lineStart);
                return;
            }

            // End (123)
            if (androidKeycode == 123) {
                mSelectionAnchor = -1;
                mSelectionCaret = -1;
                int lineEnd = (current != null) ? current.toString().indexOf('\n', selStart) : len;
                if (lineEnd < 0) lineEnd = len;
                setCursorPosition(focused, lineEnd);
                return;
            }

            // Page Up (92)
            if (androidKeycode == 92) {
                focused.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                return;
            }

            // Page Down (93)
            if (androidKeycode == 93) {
                focused.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                return;
            }

            // ================= 8. REGULAR UNICODE TYPING =================
            if (!isCtrl && unicodeChar != 0 && !Character.isISOControl(unicodeChar)) {
                mSelectionAnchor = -1;
                mSelectionCaret = -1;
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

    public void insertNewline() {
        AccessibilityNodeInfo focused = getFocusedInputNode();
        if (focused == null) return;
        try {
            long now = SystemClock.uptimeMillis();
            boolean useCache = (mCachedText != null && (now - mLastKeyTypeTime < 1500) && (focused.getWindowId() == mCachedNodeWindowId));
            CharSequence current;
            int selStart;
            int selEnd;
            if (useCache) {
                current = mCachedText;
                selStart = (mCachedSelStart >= 0 && mCachedSelStart <= mCachedText.length()) ? mCachedSelStart : mCachedText.length();
                selEnd = (mCachedSelEnd >= 0 && mCachedSelEnd <= mCachedText.length()) ? mCachedSelEnd : mCachedText.length();
            } else {
                current = focused.getText();
                selStart = focused.getTextSelectionStart();
                selEnd = focused.getTextSelectionEnd();
                int l = (current != null) ? current.length() : 0;
                if (selStart < 0 || selEnd < 0) {
                    selStart = l;
                    selEnd = l;
                }
            }
            insertNewlineOnNode(focused, current, selStart, selEnd);
        } finally {
            focused.recycle();
        }
    }

    private void insertNewlineOnNode(AccessibilityNodeInfo node, CharSequence current, int selStart, int selEnd) {
        StringBuilder sb = new StringBuilder(current != null ? current : "");
        int min = Math.min(selStart, selEnd);
        int max = Math.max(selStart, selEnd);
        sb.replace(min, max, "\n");
        int newCursor = min + 1;
        applyTextAndSelection(node, sb.toString(), newCursor);
    }

    private void applyTextAndSelection(AccessibilityNodeInfo node, String newText, int cursor) {
        mCachedText = newText;
        mCachedCursor = cursor;
        mCachedSelStart = cursor;
        mCachedSelEnd = cursor;
        mCachedNodeWindowId = node.getWindowId();
        mLastKeyTypeTime = SystemClock.uptimeMillis();

        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText);
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);

        setCursorPosition(node, cursor);
    }

    private void setCursorPosition(AccessibilityNodeInfo node, int cursor) {
        setSelectionRange(node, cursor, cursor);
    }

    private void setSelectionRange(AccessibilityNodeInfo node, int start, int end) {
        if (mCachedText != null) {
            mCachedCursor = end;
            mCachedSelStart = start;
            mCachedSelEnd = end;
            mLastKeyTypeTime = SystemClock.uptimeMillis();
        }
        Bundle selArgs = new Bundle();
        selArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start);
        selArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end);
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs);
    }

    private int findPrevWordBoundary(CharSequence text, int cursor) {
        if (text == null || cursor <= 0) return 0;
        int i = cursor - 1;
        while (i > 0 && Character.isWhitespace(text.charAt(i))) {
            i--;
        }
        while (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    private int findNextWordBoundary(CharSequence text, int cursor) {
        if (text == null || cursor >= text.length()) return text != null ? text.length() : 0;
        int len = text.length();
        int i = cursor;
        while (i < len && !Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        while (i < len && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private int moveLineUp(CharSequence text, int cursor) {
        if (text == null || text.length() == 0 || cursor <= 0) return 0;
        String s = text.toString();
        int currentLineStart = s.lastIndexOf('\n', cursor - 1);
        if (currentLineStart < 0) {
            return 0; // Already on first line
        }
        int col = cursor - (currentLineStart + 1);
        int prevLineStart = s.lastIndexOf('\n', currentLineStart - 1);
        if (prevLineStart < 0) prevLineStart = 0;
        else prevLineStart += 1;

        int prevLineLen = currentLineStart - prevLineStart;
        return prevLineStart + Math.min(col, prevLineLen);
    }

    private int moveLineDown(CharSequence text, int cursor) {
        if (text == null || text.length() == 0) return 0;
        String s = text.toString();
        int len = s.length();
        if (cursor >= len) return len;

        int currentLineStart = s.lastIndexOf('\n', Math.max(0, cursor - 1));
        if (currentLineStart < 0) currentLineStart = 0;
        else currentLineStart += 1;
        int col = cursor - currentLineStart;

        int nextLineStart = s.indexOf('\n', cursor);
        if (nextLineStart < 0) {
            return len; // Already on last line
        }
        nextLineStart += 1;
        int nextLineEnd = s.indexOf('\n', nextLineStart);
        if (nextLineEnd < 0) nextLineEnd = len;

        int nextLineLen = nextLineEnd - nextLineStart;
        return nextLineStart + Math.min(col, nextLineLen);
    }

    private boolean clickNearbyActionOrButton(AccessibilityNodeInfo focused) {
        AccessibilityNodeInfo parent = focused.getParent();
        if (parent != null) {
            try {
                int childCount = parent.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    AccessibilityNodeInfo child = parent.getChild(i);
                    if (child != null) {
                        try {
                            if (child.isClickable() && !child.equals(focused)) {
                                CharSequence text = child.getText();
                                CharSequence desc = child.getContentDescription();
                                String combined = ((text != null ? text.toString() : "") + " " +
                                        (desc != null ? desc.toString() : "")).toLowerCase();
                                if (combined.contains("send") || combined.contains("search") ||
                                        combined.contains("go") || combined.contains("submit") ||
                                        combined.contains("done") || combined.contains("enter")) {
                                    child.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    return true;
                                }
                            }
                        } finally {
                            child.recycle();
                        }
                    }
                }
            } finally {
                parent.recycle();
            }
        }
        return false;
    }

    private AccessibilityNodeInfo getFocusedClickableNode() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (f != null && f.isClickable()) {
                root.recycle();
                return f;
            }
            if (f != null) f.recycle();
            root.recycle();
        }
        return null;
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

    // ================= CTRL+ENTER & SHIFT+ENTER SEND TRIGGER =================
    public boolean triggerSendAction() {
        AppLogger.i("Accessibility", "Attempting to trigger Send action via accessibility tree");
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            try {
                List<AccessibilityWindowInfo> windows = getWindows();
                if (windows != null) {
                    for (AccessibilityWindowInfo w : windows) {
                        if (w.isFocused() || w.isActive()) {
                            AccessibilityNodeInfo wRoot = w.getRoot();
                            if (wRoot != null) {
                                boolean handled = triggerSendActionOnRoot(wRoot);
                                wRoot.recycle();
                                if (handled) return true;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            return false;
        }

        try {
            return triggerSendActionOnRoot(root);
        } finally {
            root.recycle();
        }
    }

    private boolean triggerSendActionOnRoot(AccessibilityNodeInfo root) {
        if (root == null) return false;

        // 1. Direct match on popular messaging app send button IDs
        String[] targetIds = {
            "com.whatsapp:id/send",
            "com.whatsapp.w4b:id/send",
            "org.telegram.messenger:id/send_button",
            "org.thoughtcrime.securesms:id/send_button",
            "com.google.android.apps.messaging:id/send_message_button_icon",
            "com.google.android.apps.messaging:id/send_message_button",
            "com.facebook.orca:id/composer_send_button",
            "com.discord:id/send_btn",
            "com.slack:id/send_button"
        };

        for (String id : targetIds) {
            try {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
                if (nodes != null && !nodes.isEmpty()) {
                    for (AccessibilityNodeInfo node : nodes) {
                        try {
                            Rect r = new Rect();
                            node.getBoundsInScreen(r);
                            boolean clicked = performClickSafe(node);
                            if (r.width() > 0 && r.height() > 0) {
                                dispatchClick(r.centerX(), r.centerY());
                                clicked = true;
                            }
                            if (clicked) {
                                AppLogger.i("Accessibility", "Triggered Send via viewId: " + id + " at " + r);
                                return true;
                            }
                        } finally {
                            node.recycle();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. Recursive tree search for any view containing "send" in description or ID
        AccessibilityNodeInfo sendNode = findSendNodeRecursive(root);
        if (sendNode != null) {
            try {
                Rect r = new Rect();
                sendNode.getBoundsInScreen(r);
                boolean clicked = performClickSafe(sendNode);
                if (r.width() > 0 && r.height() > 0) {
                    dispatchClick(r.centerX(), r.centerY());
                    clicked = true;
                }
                if (clicked) {
                    AppLogger.i("Accessibility", "Triggered Send via recursive search at " + r);
                    return true;
                }
            } finally {
                sendNode.recycle();
            }
        }

        // 3. Spatial bottom-right lookup next to focused input box (messaging apps only)
        CharSequence pkg = root.getPackageName();
        String pkgStr = (pkg != null) ? pkg.toString().toLowerCase() : "";
        boolean isChatApp = pkgStr.contains("whatsapp") || pkgStr.contains("telegram") ||
                            pkgStr.contains("signal") || pkgStr.contains("thoughtcrime") ||
                            pkgStr.contains("messaging") || pkgStr.contains("orca") ||
                            pkgStr.contains("discord") || pkgStr.contains("slack") ||
                            pkgStr.contains("viber") || pkgStr.contains("skype") ||
                            pkgStr.contains("line") || pkgStr.contains("wechat") ||
                            pkgStr.contains("chat");

        if (isChatApp) {
            AccessibilityNodeInfo focused = getFocusedInputNode();
            if (focused != null) {
                try {
                    Rect inputRect = new Rect();
                    focused.getBoundsInScreen(inputRect);
                    int screenW = getResources().getDisplayMetrics().widthPixels;
                    int screenH = getResources().getDisplayMetrics().heightPixels;

                    // If input box is in the lower half of the screen
                    if (inputRect.centerY() > screenH * 0.45f) {
                        float clickX;
                        if (screenW - inputRect.right > 40) {
                            clickX = inputRect.right + (screenW - inputRect.right) / 2.0f;
                        } else {
                            clickX = screenW - 40f;
                        }
                        float clickY = inputRect.centerY();

                        AppLogger.i("Accessibility", "Triggering Send via spatial tap at (" + clickX + ", " + clickY + ")");
                        clickNodeAt(clickX, clickY);
                        dispatchClick(clickX, clickY);
                        return true;
                    }
                } finally {
                    focused.recycle();
                }
            }
        }

        // 4. Fallback: IME action SEND on focused node
        AccessibilityNodeInfo focused = getFocusedInputNode();
        if (focused != null) {
            try {
                boolean ok = focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
                if (ok) {
                    AppLogger.i("Accessibility", "Triggered Send via ACTION_IME_ENTER");
                    return true;
                }
            } finally {
                focused.recycle();
            }
        }

        return false;
    }

    private AccessibilityNodeInfo findSendNodeRecursive(AccessibilityNodeInfo node) {
        if (node == null || !node.isVisibleToUser()) return null;

        String resId = node.getViewIdResourceName();
        if (resId != null && resId.toLowerCase().contains("send")) {
            return AccessibilityNodeInfo.obtain(node);
        }

        CharSequence cd = node.getContentDescription();
        if (cd != null) {
            String s = cd.toString().trim().toLowerCase();
            if (s.equals("send") || s.equals("enviar") || s.equals("senden") ||
                s.equals("envoyer") || s.equals("invia") || s.equals("отправить") ||
                s.equals("trimite") || s.equals("wyslij") || s.equals("stuur") ||
                s.equals("gönder") || s.equals("gonder") || s.contains("send message")) {
                return AccessibilityNodeInfo.obtain(node);
            }
        }

        int count = node.getChildCount();
        for (int i = count - 1; i >= 0; i--) { // Reverse order: bottom-right children first
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findSendNodeRecursive(child);
                child.recycle();
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean performClickSafe(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isClickable()) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null) {
            try {
                if (parent.isClickable()) {
                    return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                AccessibilityNodeInfo grandParent = parent.getParent();
                if (grandParent != null) {
                    try {
                        if (grandParent.isClickable()) {
                            return grandParent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        }
                    } finally {
                        grandParent.recycle();
                    }
                }
            } finally {
                parent.recycle();
            }
        }
        return false;
    }

    // ================= TAB FOCUS ELEMENT NAVIGATION =================
    public void handleTabNavigation(boolean isShift) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            try {
                List<AccessibilityWindowInfo> windows = getWindows();
                if (windows != null) {
                    for (AccessibilityWindowInfo w : windows) {
                        if (w.isFocused() || w.isActive()) {
                            AccessibilityNodeInfo wr = w.getRoot();
                            if (wr != null) {
                                root = wr;
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        if (root == null) return;

        try {
            int screenW = getResources().getDisplayMetrics().widthPixels;
            int screenH = getResources().getDisplayMetrics().heightPixels;

            List<AccessibilityNodeInfo> interactiveNodes = new ArrayList<>();
            collectInteractiveNodes(root, interactiveNodes, screenW, screenH);

            if (interactiveNodes.isEmpty()) return;

            // Sort nodes visually: reading order (row by row, left to right within row)
            interactiveNodes.sort((a, b) -> {
                Rect ra = new Rect();
                Rect rb = new Rect();
                a.getBoundsInScreen(ra);
                b.getBoundsInScreen(rb);
                if (Math.abs(ra.centerY() - rb.centerY()) < 60) {
                    return Integer.compare(ra.left, rb.left);
                }
                return Integer.compare(ra.top, rb.top);
            });

            // Find current active index
            int currentIndex = -1;
            if (mTabActiveNode != null) {
                Rect tr = new Rect();
                mTabActiveNode.getBoundsInScreen(tr);
                for (int i = 0; i < interactiveNodes.size(); i++) {
                    Rect nr = new Rect();
                    interactiveNodes.get(i).getBoundsInScreen(nr);
                    if (tr.equals(nr)) {
                        currentIndex = i;
                        break;
                    }
                }
            }

            if (currentIndex == -1) {
                AccessibilityNodeInfo focused = getFocusedInputNode();
                if (focused != null) {
                    try {
                        Rect fr = new Rect();
                        focused.getBoundsInScreen(fr);
                        for (int i = 0; i < interactiveNodes.size(); i++) {
                            Rect nr = new Rect();
                            interactiveNodes.get(i).getBoundsInScreen(nr);
                            if (fr.contains(nr) || nr.contains(fr) || (Math.abs(fr.centerY() - nr.centerY()) < 40 && Math.abs(fr.centerX() - nr.centerX()) < 60)) {
                                currentIndex = i;
                                break;
                            }
                        }
                    } finally {
                        focused.recycle();
                    }
                }
            }

            int targetIndex;
            if (currentIndex == -1) {
                targetIndex = isShift ? interactiveNodes.size() - 1 : 0;
            } else {
                targetIndex = isShift ? (currentIndex - 1 + interactiveNodes.size()) % interactiveNodes.size()
                                      : (currentIndex + 1) % interactiveNodes.size();
            }

            AccessibilityNodeInfo target = interactiveNodes.get(targetIndex);

            if (mTabActiveNode != null) {
                mTabActiveNode.recycle();
                mTabActiveNode = null;
            }
            mTabActiveNode = AccessibilityNodeInfo.obtain(target);

            target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
            }

            Rect tr = new Rect();
            target.getBoundsInScreen(tr);
            CharSequence desc = target.getContentDescription();
            CharSequence txt = target.getText();
            String label = (desc != null) ? desc.toString() : ((txt != null) ? txt.toString() : target.getClassName().toString());
            AppLogger.i("Accessibility", "Tab focus moved to [" + targetIndex + "/" + interactiveNodes.size() + "] " + label + " at " + tr);

            for (AccessibilityNodeInfo n : interactiveNodes) {
                n.recycle();
            }
        } finally {
            root.recycle();
        }
    }

    private void collectInteractiveNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out, int screenW, int screenH) {
        if (node == null || !node.isVisibleToUser()) return;

        Rect r = new Rect();
        node.getBoundsInScreen(r);

        boolean isInteractive = node.isClickable() || node.isFocusable() || node.isEditable();
        boolean validSize = r.width() >= 20 && r.height() >= 20 && (r.width() < screenW * 0.98f || r.height() < screenH * 0.95f);

        if (isInteractive && validSize) {
            out.add(AccessibilityNodeInfo.obtain(node));
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectInteractiveNodes(child, out, screenW, screenH);
                child.recycle();
            }
        }
    }

    // ================= ALT+TAB APP SWITCHER & CYCLER =================
    private boolean mAltHeld = false;
    private boolean mAltTabActive = false;
    private int mAltTabCount = 0;
    private long mAltTabStartTime = 0;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public boolean isAltHeld() {
        return mAltHeld;
    }

    public boolean isAltTabActive() {
        return mAltTabActive;
    }

    public void cancelAltTab() {
        if (mAltTabActive) {
            AppLogger.i("AltTab", "Alt+Tab canceled");
            mAltTabActive = false;
            mAltTabCount = 0;
        }
    }

    public void onAltStateChanged(boolean isDown) {
        mAltHeld = isDown;
        if (!isDown && mAltTabActive) {
            onAltReleased();
        }
    }

    public void handleAltTab(boolean isShift) {
        long now = SystemClock.uptimeMillis();

        if (!mAltTabActive) {
            // First press of Alt+Tab: open Recents overview
            mAltTabActive = true;
            mAltTabCount = 1;
            mAltTabStartTime = now;

            performGlobalAction(GLOBAL_ACTION_RECENTS);
            AppLogger.i("AltTab", "Alt+Tab (1st press): Opened Recents overview");
        } else {
            // Consecutive Tab presses while Alt is held: cycle apps!
            mAltTabCount++;
            AppLogger.i("AltTab", "Alt+Tab (press " + mAltTabCount + ", shift=" + isShift + "): Cycling apps");
            handleAltTabNav(!isShift);
        }
    }

    public void handleAltTabNav(boolean forward) {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;

        List<AccessibilityNodeInfo> tasks = getRecentsTaskCards(screenW, screenH);
        if (tasks.size() > 1) {
            int targetIndex;
            if (forward) {
                targetIndex = (mAltTabCount - 1) % tasks.size();
                if (targetIndex < 0) targetIndex += tasks.size();
            } else {
                targetIndex = (mAltTabCount - 1) % tasks.size();
                if (targetIndex < 0) targetIndex += tasks.size();
            }
            AccessibilityNodeInfo target = tasks.get(targetIndex);
            target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
            }

            Rect tr = new Rect();
            target.getBoundsInScreen(tr);
            AppLogger.i("AltTab", "Navigated to task card [" + targetIndex + "/" + tasks.size() + "] at " + tr);

            float cx = screenW * 0.5f;
            float cy = screenH * 0.5f;
            float dx = cx - tr.centerX();
            if (Math.abs(dx) > 100) {
                float startX = cx;
                float endX = cx + (dx > 0 ? 300 : -300);
                dispatchSwipe(startX, cy, endX, cy, 180);
            }

            for (AccessibilityNodeInfo t : tasks) {
                t.recycle();
            }
        } else {
            for (AccessibilityNodeInfo t : tasks) {
                t.recycle();
            }
            float cy = screenH * 0.5f;
            if (forward) {
                float startX = screenW * 0.75f;
                float endX   = screenW * 0.25f;
                dispatchSwipe(startX, cy, endX, cy, 200);
            } else {
                float startX = screenW * 0.25f;
                float endX   = screenW * 0.75f;
                dispatchSwipe(startX, cy, endX, cy, 200);
            }
        }
    }

    public void onAltReleased() {
        if (!mAltTabActive) return;

        final int count = mAltTabCount;
        mAltTabActive = false;
        mAltTabCount = 0;

        AppLogger.i("AltTab", "Alt released: count=" + count);

        mMainHandler.postDelayed(() -> {
            commitRecentsSelection(count);
        }, 220);
    }

    public void commitAltTab() {
        if (!mAltTabActive) return;
        final int count = mAltTabCount;
        mAltTabActive = false;
        mAltTabCount = 0;
        commitRecentsSelection(count);
    }

    private void commitRecentsSelection(int count) {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        float cx = screenW * 0.5f;
        float cy = screenH * 0.5f;

        // 1. Check focused accessibility node in any window
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo w : windows) {
                    AccessibilityNodeInfo root = w.getRoot();
                    if (root != null) {
                        try {
                            AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
                            if (focused != null) {
                                try {
                                    Rect r = new Rect();
                                    focused.getBoundsInScreen(r);
                                    boolean clicked = performClickSafe(focused);
                                    if (r.width() > 0 && r.height() > 0) {
                                        dispatchClick(r.centerX(), r.centerY());
                                        clicked = true;
                                    }
                                    if (clicked) {
                                        AppLogger.i("AltTab", "Committed app switch via focused node at " + r);
                                        return;
                                    }
                                } finally {
                                    focused.recycle();
                                }
                            }
                        } finally {
                            root.recycle();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // 2. Find closest task card to screen center
        List<AccessibilityNodeInfo> tasks = getRecentsTaskCards(screenW, screenH);
        if (!tasks.isEmpty()) {
            AccessibilityNodeInfo best = null;
            float bestDist = Float.MAX_VALUE;
            for (AccessibilityNodeInfo t : tasks) {
                Rect r = new Rect();
                t.getBoundsInScreen(r);
                float dist = Math.abs(r.centerX() - cx);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = t;
                }
            }
            if (best != null) {
                Rect r = new Rect();
                best.getBoundsInScreen(r);
                performClickSafe(best);
                dispatchClick(r.centerX(), r.centerY());
                AppLogger.i("AltTab", "Committed app switch via closest task card at " + r);
                for (AccessibilityNodeInfo t : tasks) t.recycle();
                return;
            }
            for (AccessibilityNodeInfo t : tasks) t.recycle();
        }

        // 3. Fallback: single Alt+Tab quick-switch or center click
        if (count == 1) {
            performGlobalAction(GLOBAL_ACTION_RECENTS);
            AppLogger.i("AltTab", "Committed single Alt+Tab via double-tap Recents quick-switch");
        } else {
            boolean nodeClicked = clickNodeAt(cx, cy);
            if (!nodeClicked) {
                dispatchClick(cx, cy);
            }
            AppLogger.i("AltTab", "Committed app switch via center click at (" + cx + ", " + cy + ")");
        }
    }

    private List<AccessibilityNodeInfo> getRecentsTaskCards(int screenW, int screenH) {
        List<AccessibilityNodeInfo> tasks = new ArrayList<>();
        AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
        if (activeRoot != null) {
            findTaskCardsRecursive(activeRoot, tasks, screenW, screenH);
            activeRoot.recycle();
        }

        if (tasks.isEmpty()) {
            try {
                List<AccessibilityWindowInfo> windows = getWindows();
                if (windows != null) {
                    for (AccessibilityWindowInfo w : windows) {
                        AccessibilityNodeInfo wr = w.getRoot();
                        if (wr != null) {
                            findTaskCardsRecursive(wr, tasks, screenW, screenH);
                            wr.recycle();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (tasks.size() > 1) {
            tasks.sort((a, b) -> {
                Rect ra = new Rect();
                Rect rb = new Rect();
                a.getBoundsInScreen(ra);
                b.getBoundsInScreen(rb);
                return Integer.compare(ra.centerX(), rb.centerX());
            });
        }
        return tasks;
    }

    private void findTaskCardsRecursive(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out, int screenW, int screenH) {
        if (node == null || !node.isVisibleToUser()) return;
        Rect r = new Rect();
        node.getBoundsInScreen(r);

        CharSequence className = node.getClassName();
        String cls = (className != null) ? className.toString() : "";
        String resId = node.getViewIdResourceName();
        String idStr = (resId != null) ? resId.toLowerCase() : "";

        boolean isCard = (cls.contains("TaskView") || cls.contains("RecentsView") || idStr.contains("task_view") || idStr.contains("snapshot") || idStr.contains("card"))
                || (node.isClickable() && r.width() >= screenW * 0.25f && r.height() >= screenH * 0.25f);

        if (isCard) {
            out.add(AccessibilityNodeInfo.obtain(node));
            return;
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findTaskCardsRecursive(child, out, screenW, screenH);
                child.recycle();
            }
        }
    }
}
