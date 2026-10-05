package com.antigravity.btbridge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
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

    public void dispatchSwipe(float fromX, float fromY, float toX, float toY, long durationMs) {
        Path path = new Path();
        path.moveTo(fromX, fromY);
        path.lineTo(toX, toY);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, Math.max(50, durationMs));
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        dispatchGesture(builder.build(), null, null);
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

        // Ctrl + Enter: Trigger Send in WhatsApp and messaging apps
        if (isCtrl && androidKeycode == 66) {
            if (triggerSendAction()) {
                return;
            }
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
            CharSequence current = focused.getText();
            CharSequence hintText = focused.getHintText();
            boolean isShowingHint = focused.isShowingHintText();
            int selStart = focused.getTextSelectionStart();
            int selEnd = focused.getTextSelectionEnd();

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
                        // If unselected or at boundary, it's the background placeholder
                        if (selStart < 0 || selStart == 0 || selStart == curStr.length()) {
                            isPlaceholder = true;
                        }
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
            int len = (current != null) ? current.length() : 0;

            // ================= 1. WINDOWS CTRL SHORTCUTS =================
            if (isCtrl && settings.isCtrlShortcutsEnabled()) {
                // Ctrl + A (Select All)
                if (androidKeycode == 29) { // KEYCODE_A
                    setSelectionRange(focused, 0, len);
                    focused.performAction(AccessibilityNodeInfo.ACTION_SELECT);
                    AppLogger.d("Accessibility", "Executed Ctrl+A (Select All)");
                    return;
                }
                // Ctrl + C (Copy)
                if (androidKeycode == 31) { // KEYCODE_C
                    focused.performAction(AccessibilityNodeInfo.ACTION_COPY);
                    AppLogger.d("Accessibility", "Executed Ctrl+C (Copy)");
                    return;
                }
                // Ctrl + V (Paste)
                if (androidKeycode == 50) { // KEYCODE_V
                    focused.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                    AppLogger.d("Accessibility", "Executed Ctrl+V (Paste)");
                    return;
                }
                // Ctrl + X (Cut)
                if (androidKeycode == 52) { // KEYCODE_X
                    focused.performAction(AccessibilityNodeInfo.ACTION_CUT);
                    AppLogger.d("Accessibility", "Executed Ctrl+X (Cut)");
                    return;
                }
                // Ctrl + Z (Undo)
                if (androidKeycode == 54) { // KEYCODE_Z
                    focused.performAction(android.R.id.undo);
                    AppLogger.d("Accessibility", "Executed Ctrl+Z (Undo)");
                    return;
                }
                // Ctrl + Y (Redo)
                if (androidKeycode == 53) { // KEYCODE_Y
                    focused.performAction(android.R.id.redo);
                    AppLogger.d("Accessibility", "Executed Ctrl+Y (Redo)");
                    return;
                }
                // Ctrl + Backspace (Delete Word Backward)
                if (androidKeycode == 67 && current != null && selStart > 0) {
                    int prevWord = findPrevWordBoundary(current, selStart);
                    StringBuilder sb = new StringBuilder(current);
                    sb.delete(prevWord, selStart);
                    applyTextAndSelection(focused, sb.toString(), prevWord);
                    AppLogger.d("Accessibility", "Executed Ctrl+Backspace (Delete Word Backward)");
                    return;
                }
                // Ctrl + Delete (Delete Word Forward)
                if (androidKeycode == 112 && current != null && selStart < len) {
                    int nextWord = findNextWordBoundary(current, selStart);
                    StringBuilder sb = new StringBuilder(current);
                    sb.delete(selStart, nextWord);
                    applyTextAndSelection(focused, sb.toString(), selStart);
                    AppLogger.d("Accessibility", "Executed Ctrl+Delete (Delete Word Forward)");
                    return;
                }
                // Ctrl + Left (Jump Word Backward)
                if (androidKeycode == 21 && current != null) {
                    int prevWord = findPrevWordBoundary(current, selStart);
                    setCursorPosition(focused, prevWord);
                    return;
                }
                // Ctrl + Right (Jump Word Forward)
                if (androidKeycode == 22 && current != null) {
                    int nextWord = findNextWordBoundary(current, selStart);
                    setCursorPosition(focused, nextWord);
                    return;
                }
            }

            // ================= 2. SHIFT + NAVIGATION (TEXT SELECTION) =================
            if (isShift) {
                // Shift + Left Arrow
                if (androidKeycode == 21) {
                    int newEnd = Math.max(0, selEnd - 1);
                    setSelectionRange(focused, selStart, newEnd);
                    return;
                }
                // Shift + Right Arrow
                if (androidKeycode == 22) {
                    int newEnd = Math.min(len, selEnd + 1);
                    setSelectionRange(focused, selStart, newEnd);
                    return;
                }
                // Shift + Home
                if (androidKeycode == 122) {
                    setSelectionRange(focused, selStart, 0);
                    return;
                }
                // Shift + End
                if (androidKeycode == 123) {
                    setSelectionRange(focused, selStart, len);
                    return;
                }
            }

            // ================= 3. BACKSPACE (KEYCODE_DEL = 67) =================
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

            // ================= 4. DELETE (FORWARD DELETE = 112) =================
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

            // ================= 5. ENTER (KEYCODE_ENTER = 66) =================
            if (androidKeycode == 66) {
                int enterMode = settings.getEnterMode();
                if (enterMode == BridgeSettings.ENTER_MODE_NEWLINE || (enterMode == BridgeSettings.ENTER_MODE_SMART && focused.isMultiLine())) {
                    StringBuilder sb = new StringBuilder(current != null ? current : "");
                    int min = Math.min(selStart, selEnd);
                    int max = Math.max(selStart, selEnd);
                    sb.replace(min, max, "\n");
                    int newCursor = min + 1;
                    applyTextAndSelection(focused, sb.toString(), newCursor);
                    AppLogger.d("Accessibility", "Executed Enter newline at position " + newCursor);
                } else {
                    // Single-line field action: attempt click on submit/send button or perform IME_ENTER / click
                    boolean clicked = clickNearbyActionOrButton(focused);
                    if (!clicked) {
                        boolean imeEntered = focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
                        if (!imeEntered) {
                            focused.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        }
                    }
                    AppLogger.d("Accessibility", "Executed Enter action (Submit/Click)");
                }
                return;
            }

            // ================= 6. TAB (KEYCODE_TAB = 61) =================
            if (androidKeycode == 61) {
                int tabMode = settings.getTabMode();
                if (tabMode == BridgeSettings.TAB_MODE_FOCUS) {
                    // Focus navigation
                    int focusDir = isShift ? View.FOCUS_BACKWARD : View.FOCUS_FORWARD;
                    AccessibilityNodeInfo target = focused.focusSearch(focusDir);
                    if (target != null) {
                        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                        target.recycle();
                        AppLogger.d("Accessibility", "Executed Tab focus navigation");
                    }
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

            // ================= 7. ARROW NAVIGATION =================
            // Arrow Left (21)
            if (androidKeycode == 21) {
                int newCursor = Math.max(0, selStart - 1);
                setCursorPosition(focused, newCursor);
                return;
            }

            // Arrow Right (22)
            if (androidKeycode == 22) {
                int newCursor = Math.min(len, selStart + 1);
                setCursorPosition(focused, newCursor);
                return;
            }

            // Arrow Up (19)
            if (androidKeycode == 19) {
                if (current != null && current.length() > 0) {
                    int newCursor = moveLineUp(current, selStart);
                    setCursorPosition(focused, newCursor);
                }
                return;
            }

            // Arrow Down (20)
            if (androidKeycode == 20) {
                if (current != null && current.length() > 0) {
                    int newCursor = moveLineDown(current, selStart);
                    setCursorPosition(focused, newCursor);
                }
                return;
            }

            // Home (122)
            if (androidKeycode == 122) {
                setCursorPosition(focused, 0);
                return;
            }

            // End (123)
            if (androidKeycode == 123) {
                setCursorPosition(focused, len);
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
        setSelectionRange(node, cursor, cursor);
    }

    private void setSelectionRange(AccessibilityNodeInfo node, int start, int end) {
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

    // ================= CTRL+ENTER SEND TRIGGER =================
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
                            if (performClickSafe(node)) {
                                AppLogger.i("Accessibility", "Triggered Send via viewId: " + id);
                                return true;
                            }
                        } finally {
                            node.recycle();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. Multilingual Content Description match for Send
        String[] sendDescriptions = {
            "send", "enviar", "senden", "envoyer", "invia", "отправить", "trimite", "wyslij", "stuur", "gonder"
        };
        for (String desc : sendDescriptions) {
            try {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(desc);
                if (nodes != null) {
                    for (AccessibilityNodeInfo node : nodes) {
                        try {
                            CharSequence cd = node.getContentDescription();
                            CharSequence text = node.getText();
                            String s = (cd != null ? cd.toString() : (text != null ? text.toString() : "")).trim();
                            if (s.equalsIgnoreCase(desc)) {
                                if (performClickSafe(node)) {
                                    AppLogger.i("Accessibility", "Triggered Send via description: " + desc);
                                    return true;
                                }
                            }
                        } finally {
                            node.recycle();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // 3. Recursive tree search for any viewId containing "send"
        AccessibilityNodeInfo sendNode = findSendNodeRecursive(root);
        if (sendNode != null) {
            try {
                if (performClickSafe(sendNode)) {
                    AppLogger.i("Accessibility", "Triggered Send via recursive search");
                    return true;
                }
            } finally {
                sendNode.recycle();
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
        if (node == null) return null;
        String resId = node.getViewIdResourceName();
        if (resId != null && resId.toLowerCase().contains("send")) {
            if (node.isClickable() || (node.getParent() != null && node.getParent().isClickable())) {
                return AccessibilityNodeInfo.obtain(node);
            }
        }
        CharSequence cd = node.getContentDescription();
        if (cd != null && cd.toString().trim().equalsIgnoreCase("send")) {
            if (node.isClickable() || (node.getParent() != null && node.getParent().isClickable())) {
                return AccessibilityNodeInfo.obtain(node);
            }
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
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

    // ================= ALT+TAB APP SWITCHER & CYCLER =================
    private boolean mAltHeld = false;
    private boolean mAltTabActive = false;
    private int mAltTabCount = 0;
    private long mAltTabStartTime = 0;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private Runnable mSingleTabCommitRunnable = null;

    public boolean isAltHeld() {
        return mAltHeld;
    }

    public boolean isAltTabActive() {
        return mAltTabActive;
    }

    public void onAltStateChanged(boolean isDown) {
        mAltHeld = isDown;
        if (!isDown && mAltTabActive) {
            onAltReleased();
        }
    }

    public void handleAltTab(boolean isShift) {
        long now = SystemClock.uptimeMillis();
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;

        if (!mAltTabActive) {
            // First press of Alt+Tab: open Recents
            mAltTabActive = true;
            mAltTabCount = 1;
            mAltTabStartTime = now;

            performGlobalAction(GLOBAL_ACTION_RECENTS);
            AppLogger.i("AltTab", "Alt+Tab (1st press): Opened Recents overview");
        } else {
            // Consecutive Tab presses while Alt is held: cycle apps!
            mAltTabCount++;
            AppLogger.i("AltTab", "Alt+Tab (press " + mAltTabCount + ", shift=" + isShift + "): Cycling apps");

            if (mSingleTabCommitRunnable != null) {
                mMainHandler.removeCallbacks(mSingleTabCommitRunnable);
                mSingleTabCommitRunnable = null;
            }

            float cy = screenH * 0.5f;
            if (!isShift) {
                // Cycle forward (swipe left to bring next card into center)
                float startX = screenW * 0.80f;
                float endX   = screenW * 0.20f;
                dispatchSwipe(startX, cy, endX, cy, 140);
            } else {
                // Cycle backward (swipe right to bring previous card into center)
                float startX = screenW * 0.20f;
                float endX   = screenW * 0.80f;
                dispatchSwipe(startX, cy, endX, cy, 140);
            }
        }
    }

    public void onAltReleased() {
        if (!mAltTabActive) return;

        final int count = mAltTabCount;
        mAltTabActive = false;
        mAltTabCount = 0;

        AppLogger.i("AltTab", "Alt released: count=" + count);

        if (count == 1) {
            // "press once, switch to previous app"
            // Wait brief moment for Recents window to settle then switch
            mMainHandler.postDelayed(() -> {
                boolean clicked = clickCenteredRecentApp();
                if (!clicked) {
                    performGlobalAction(GLOBAL_ACTION_RECENTS);
                }
                AppLogger.i("AltTab", "Committed quick switch to previous app");
            }, 180);
        } else {
            // "press twice cycle to next one, alt-shift-tab cycle to previous"
            // Commit the selected app by clicking the centered app card
            mMainHandler.postDelayed(() -> {
                clickCenteredRecentApp();
                AppLogger.i("AltTab", "Committed cycled app switch to active task");
            }, 160);
        }
    }

    private boolean clickCenteredRecentApp() {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        float cx = screenW * 0.5f;
        float cy = screenH * 0.5f;

        // In Android Overview, clicking the center of the screen activates the centered task card
        dispatchClick(cx, cy);
        return true;
    }
}
