package com.antigravity.btbridge;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

public class LapdroidInputMethodService extends InputMethodService {
    private static LapdroidInputMethodService sInstance = null;
    private int mImeSelectionAnchor = -1;
    private int mImeSelectionCaret = -1;

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
        boolean isShift = (modifiers & Protocol.MOD_SHIFT) != 0;
        boolean isCtrl  = (modifiers & Protocol.MOD_CTRL)  != 0;
        boolean isAlt   = (modifiers & Protocol.MOD_ALT)   != 0;
        boolean isMeta  = (modifiers & Protocol.MOD_META)  != 0;

        int metaState = 0;
        if (isShift) metaState |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        if (isCtrl)  metaState |= KeyEvent.META_CTRL_ON  | KeyEvent.META_CTRL_LEFT_ON;
        if (isAlt)   metaState |= KeyEvent.META_ALT_ON   | KeyEvent.META_ALT_LEFT_ON;
        if (isMeta)  metaState |= KeyEvent.META_META_ON  | KeyEvent.META_META_LEFT_ON;

        // Reset IME selection anchor if not holding Shift
        if (!isShift) {
            mImeSelectionAnchor = -1;
            mImeSelectionCaret = -1;
        }

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
        if (isCtrl) {
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

            // Ctrl + A (Select All)
            if (androidKeycode == 29) {
                boolean ok = ic.performContextMenuAction(android.R.id.selectAll);
                if (!ok) {
                    ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
                    if (et != null && et.text != null) {
                        mImeSelectionAnchor = 0;
                        mImeSelectionCaret = et.text.length();
                        ic.setSelection(0, et.text.length());
                    } else {
                        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0, metaState));
                        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 0, metaState));
                    }
                }
                return true;
            }

            // Ctrl + C (Copy)
            if (androidKeycode == 31) {
                boolean ok = ic.performContextMenuAction(android.R.id.copy);
                if (!ok) {
                    CharSequence sel = ic.getSelectedText(0);
                    if (sel != null && sel.length() > 0) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("text", sel.toString()));
                        }
                    } else {
                        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C, 0, metaState));
                        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_C, 0, metaState));
                    }
                }
                return true;
            }

            // Ctrl + V (Paste)
            if (androidKeycode == 50) {
                boolean ok = ic.performContextMenuAction(android.R.id.paste);
                if (!ok) {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                        CharSequence clip = cm.getPrimaryClip().getItemAt(0).getText();
                        if (clip != null && clip.length() > 0) {
                            ic.commitText(clip, 1);
                        }
                    } else {
                        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_V, 0, metaState));
                        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_V, 0, metaState));
                    }
                }
                return true;
            }

            // Ctrl + X (Cut)
            if (androidKeycode == 52) {
                boolean ok = ic.performContextMenuAction(android.R.id.cut);
                if (!ok) {
                    CharSequence sel = ic.getSelectedText(0);
                    if (sel != null && sel.length() > 0) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("text", sel.toString()));
                        }
                        ic.commitText("", 1);
                    } else {
                        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_X, 0, metaState));
                        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_X, 0, metaState));
                    }
                }
                return true;
            }

            // Ctrl + Z (Undo)
            if (androidKeycode == 54) {
                boolean ok = ic.performContextMenuAction(android.R.id.undo);
                if (!ok) {
                    ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, metaState));
                    ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, metaState));
                }
                return true;
            }

            // Ctrl + Y (Redo)
            if (androidKeycode == 53) {
                boolean ok = ic.performContextMenuAction(android.R.id.redo);
                if (!ok) {
                    ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Y, 0, metaState));
                    ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Y, 0, metaState));
                }
                return true;
            }

            // Prevent other Ctrl combos from typing unicode characters
            return false;
        }

        // 4. Shift + Navigation (Text Selection)
        if (isShift && (androidKeycode == 21 || androidKeycode == 22 || androidKeycode == 122 || androidKeycode == 123)) {
            ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
            if (et != null && et.text != null) {
                int len = et.text.length();
                int selStart = et.selectionStart;
                int selEnd = et.selectionEnd;
                if (mImeSelectionAnchor < 0) {
                    if (androidKeycode == 21 || androidKeycode == 122) {
                        mImeSelectionAnchor = Math.max(selStart, selEnd);
                        mImeSelectionCaret = Math.min(selStart, selEnd);
                    } else {
                        mImeSelectionAnchor = Math.min(selStart, selEnd);
                        mImeSelectionCaret = Math.max(selStart, selEnd);
                    }
                }

                if (androidKeycode == 21) { // Left
                    mImeSelectionCaret = Math.max(0, mImeSelectionCaret - 1);
                } else if (androidKeycode == 22) { // Right
                    mImeSelectionCaret = Math.min(len, mImeSelectionCaret + 1);
                } else if (androidKeycode == 122) { // Home
                    int lineStart = et.text.toString().lastIndexOf('\n', Math.max(0, mImeSelectionCaret - 1)) + 1;
                    if (lineStart < 0) lineStart = 0;
                    mImeSelectionCaret = lineStart;
                } else if (androidKeycode == 123) { // End
                    int lineEnd = et.text.toString().indexOf('\n', mImeSelectionCaret);
                    if (lineEnd < 0) lineEnd = len;
                    mImeSelectionCaret = lineEnd;
                }

                ic.setSelection(mImeSelectionAnchor, mImeSelectionCaret);
                return true;
            }
            // Fallback to sendKeyEvent
            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, androidKeycode, 0, metaState));
            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, androidKeycode, 0, metaState));
            return true;
        }

        // Alt + Tab: Do not consume in IME, let AltTab system switcher handle it
        if (isAlt && androidKeycode == 61) {
            return false;
        }

        // Windows / Meta keys, Back/Esc, Home, Recents: Do not consume in IME, let system navigation handle it
        if (androidKeycode == 3 || androidKeycode == 117 || androidKeycode == 118 || androidKeycode == 4 || androidKeycode == 111 || androidKeycode == 187) {
            return false;
        }

        // 5. Enter
        if (androidKeycode == 66) { // KEYCODE_ENTER
            BridgeSettings settings = BridgeSettings.getInstance(this);
            if (isShift) {
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

        // 6. Regular printable character without Ctrl
        if (!isCtrl && unicodeChar != 0 && !Character.isISOControl(unicodeChar)) {
            ic.commitText(String.valueOf(unicodeChar), 1);
            return true;
        }

        // 7. Send raw key events for navigation, shortcuts, and games
        if (androidKeycode > 0) {
            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, androidKeycode, 0, metaState));
            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, androidKeycode, 0, metaState));
            return true;
        }

        return false;
    }
}
