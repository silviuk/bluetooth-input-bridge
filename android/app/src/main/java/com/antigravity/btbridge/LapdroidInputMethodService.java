package com.antigravity.btbridge;

import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class LapdroidInputMethodService extends InputMethodService {
    private static LapdroidInputMethodService sInstance = null;

    public static LapdroidInputMethodService getInstance() {
        return sInstance;
    }

    public static boolean isActive() {
        return sInstance != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        AppLogger.i("IME", "Lapdroid Direct Input Keyboard initialized");
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        super.onDestroy();
    }

    @Override
    public View onCreateInputView() {
        // Lightweight empty view as this IME is driven via remote laptop Bluetooth bridge
        View v = new View(this);
        v.setMinimumHeight(0);
        return v;
    }

    public boolean forwardKey(int androidKeycode, char unicodeChar, byte modifiers) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;

        long now = SystemClock.uptimeMillis();
        int metaState = 0;
        if ((modifiers & Protocol.MOD_SHIFT) != 0) metaState |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        if ((modifiers & Protocol.MOD_CTRL) != 0)  metaState |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        if ((modifiers & Protocol.MOD_ALT) != 0)   metaState |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
        if ((modifiers & Protocol.MOD_META) != 0)  metaState |= KeyEvent.META_META_ON | KeyEvent.META_META_LEFT_ON;

        // 1. Backspace (native key event deletes selection or char)
        if (androidKeycode == 67) { // KEYCODE_DEL
            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, 0, metaState));
            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL, 0, metaState));
            return true;
        }

        // 2. Forward Delete
        if (androidKeycode == 112) { // KEYCODE_FORWARD_DEL
            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FORWARD_DEL, 0, metaState));
            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_FORWARD_DEL, 0, metaState));
            return true;
        }

        // 3. Ctrl Shortcuts
        if ((modifiers & Protocol.MOD_CTRL) != 0) {
            // Ctrl + Enter: Send message in WhatsApp and chat apps
            if (androidKeycode == 66) {
                InputAccessibilityService accessService = InputAccessibilityService.getInstance();
                if (accessService != null && accessService.triggerSendAction()) {
                    return true;
                }
                if (ic.performEditorAction(EditorInfo.IME_ACTION_SEND)) {
                    return true;
                }
                if (ic.performEditorAction(EditorInfo.IME_ACTION_DONE)) {
                    return true;
                }
                return false;
            }
            if (androidKeycode == 29) { ic.performContextMenuAction(android.R.id.selectAll); return true; }
            if (androidKeycode == 31) { ic.performContextMenuAction(android.R.id.copy); return true; }
            if (androidKeycode == 50) { ic.performContextMenuAction(android.R.id.paste); return true; }
            if (androidKeycode == 52) { ic.performContextMenuAction(android.R.id.cut); return true; }
            if (androidKeycode == 54) { ic.performContextMenuAction(android.R.id.undo); return true; }
            if (androidKeycode == 53) { ic.performContextMenuAction(android.R.id.redo); return true; }
        }

        // Alt + Tab: Do not consume in IME, let AltTab system switcher handle it
        if ((modifiers & Protocol.MOD_ALT) != 0 && androidKeycode == 61) {
            return false;
        }

        // Windows / Meta keys, Back/Esc, Home, Recents: Do not consume in IME, let system navigation handle it
        if (androidKeycode == 3 || androidKeycode == 117 || androidKeycode == 118 || androidKeycode == 4 || androidKeycode == 111 || androidKeycode == 187) {
            return false;
        }

        // 4. Enter
        if (androidKeycode == 66) { // KEYCODE_ENTER
            BridgeSettings settings = BridgeSettings.getInstance(this);
            if ((modifiers & Protocol.MOD_SHIFT) != 0) {
                if (settings.getEnterMode() == BridgeSettings.ENTER_MODE_ACTION) {
                    ic.commitText("\n", 1);
                    return true;
                } else {
                    InputAccessibilityService accessService = InputAccessibilityService.getInstance();
                    if (accessService != null && accessService.triggerSendAction()) {
                        return true;
                    }
                    if (ic.performEditorAction(EditorInfo.IME_ACTION_SEND)) {
                        return true;
                    }
                }
            } else if (settings.getEnterMode() == BridgeSettings.ENTER_MODE_ACTION) {
                InputAccessibilityService accessService = InputAccessibilityService.getInstance();
                if (accessService != null && accessService.triggerSendAction()) {
                    return true;
                }
                if (ic.performEditorAction(EditorInfo.IME_ACTION_SEND)) {
                    return true;
                }
            }
            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0, metaState));
            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0, metaState));
            return true;
        }

        // 5. Regular printable character without Ctrl
        if ((modifiers & Protocol.MOD_CTRL) == 0 && unicodeChar != 0 && !Character.isISOControl(unicodeChar)) {
            ic.commitText(String.valueOf(unicodeChar), 1);
            return true;
        }

        // 6. Send raw key events for navigation, shortcuts, and games
        if (androidKeycode > 0) {
            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, androidKeycode, 0, metaState));
            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, androidKeycode, 0, metaState));
            return true;
        }

        return false;
    }
}
