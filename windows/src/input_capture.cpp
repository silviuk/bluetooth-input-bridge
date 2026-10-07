#include "input_capture.h"
#include "bluetooth_manager.h"
#include "protocol.h"
#include <iostream>

static InputCapture* g_currentInputCapture = nullptr;

uint16_t WindowsVkToAndroidKeycode(DWORD vk) {
    // Letters A-Z
    if (vk >= 'A' && vk <= 'Z') {
        return 29 + (vk - 'A'); // KEYCODE_A (29) .. KEYCODE_Z (54)
    }
    // Digits 0-9
    if (vk >= '0' && vk <= '9') {
        return 7 + (vk - '0');  // KEYCODE_0 (7) .. KEYCODE_9 (16)
    }
    // Numpad 0-9
    if (vk >= VK_NUMPAD0 && vk <= VK_NUMPAD9) {
        return 144 + (vk - VK_NUMPAD0); // KEYCODE_NUMPAD_0 (144) ..
    }

    // Function keys F1-F12
    if (vk >= VK_F1 && vk <= VK_F12) {
        return 131 + (vk - VK_F1); // KEYCODE_F1 (131) .. KEYCODE_F12 (142)
    }

    // Numpad math operators
    if (vk == VK_MULTIPLY) return 155; // KEYCODE_NUMPAD_MULTIPLY
    if (vk == VK_ADD)      return 157; // KEYCODE_NUMPAD_ADD
    if (vk == VK_SEPARATOR)return 159; // KEYCODE_NUMPAD_COMMA
    if (vk == VK_SUBTRACT) return 156; // KEYCODE_NUMPAD_SUBTRACT
    if (vk == VK_DECIMAL)  return 158; // KEYCODE_NUMPAD_DOT
    if (vk == VK_DIVIDE)   return 154; // KEYCODE_NUMPAD_DIVIDE

    switch (vk) {
        case VK_RETURN:   return 66;  // KEYCODE_ENTER
        case VK_BACK:     return 67;  // KEYCODE_DEL
        case VK_TAB:      return 61;  // KEYCODE_TAB
        case VK_SPACE:    return 62;  // KEYCODE_SPACE
        case VK_ESCAPE:   return 111; // KEYCODE_ESCAPE (or 4 for BACK)
        case VK_LEFT:     return 21;  // KEYCODE_DPAD_LEFT
        case VK_UP:       return 19;  // KEYCODE_DPAD_UP
        case VK_RIGHT:    return 22;  // KEYCODE_DPAD_RIGHT
        case VK_DOWN:     return 20;  // KEYCODE_DPAD_DOWN
        case VK_DELETE:   return 112; // KEYCODE_FORWARD_DEL
        case VK_HOME:     return 122; // KEYCODE_MOVE_HOME
        case VK_END:      return 123; // KEYCODE_MOVE_END
        case VK_PRIOR:    return 92;  // KEYCODE_PAGE_UP
        case VK_NEXT:     return 93;  // KEYCODE_PAGE_DOWN
        case VK_LWIN:
        case VK_RWIN:     return 3;   // KEYCODE_HOME
        case VK_APPS:     return 82;  // KEYCODE_MENU

        // Additional editing & locks
        case VK_INSERT:   return 124; // KEYCODE_INSERT
        case VK_CAPITAL:  return 115; // KEYCODE_CAPS_LOCK
        case VK_NUMLOCK:  return 143; // KEYCODE_NUM_LOCK
        case VK_SCROLL:   return 116; // KEYCODE_SCROLL_LOCK
        case VK_SNAPSHOT: return 120; // KEYCODE_SYSRQ (PrintScreen / Screenshot)
        case VK_PAUSE:    return 85;  // KEYCODE_MEDIA_PLAY_PAUSE

        // Modifiers
        case VK_SHIFT:
        case VK_LSHIFT:   return 59;  // KEYCODE_SHIFT_LEFT
        case VK_RSHIFT:   return 60;  // KEYCODE_SHIFT_RIGHT
        case VK_CONTROL:
        case VK_LCONTROL: return 113; // KEYCODE_CTRL_LEFT
        case VK_RCONTROL: return 114; // KEYCODE_CTRL_RIGHT
        case VK_MENU:
        case VK_LMENU:    return 57;  // KEYCODE_ALT_LEFT
        case VK_RMENU:    return 58;  // KEYCODE_ALT_RIGHT

        // Punctuation & symbols
        case VK_OEM_1:      return 74; // KEYCODE_SEMICOLON
        case VK_OEM_PLUS:   return 70; // KEYCODE_EQUALS
        case VK_OEM_COMMA:  return 55; // KEYCODE_COMMA
        case VK_OEM_MINUS:  return 69; // KEYCODE_MINUS
        case VK_OEM_PERIOD: return 56; // KEYCODE_PERIOD
        case VK_OEM_2:      return 76; // KEYCODE_SLASH
        case VK_OEM_3:      return 68; // KEYCODE_GRAVE
        case VK_OEM_4:      return 71; // KEYCODE_LEFT_BRACKET
        case VK_OEM_5:      return 73; // KEYCODE_BACKSLASH
        case VK_OEM_6:      return 72; // KEYCODE_RIGHT_BRACKET
        case VK_OEM_7:      return 75; // KEYCODE_APOSTROPHE

        // Media keys
        case VK_VOLUME_MUTE:       return 164; // KEYCODE_VOLUME_MUTE
        case VK_VOLUME_DOWN:       return 25;  // KEYCODE_VOLUME_DOWN
        case VK_VOLUME_UP:         return 24;  // KEYCODE_VOLUME_UP
        case VK_MEDIA_NEXT_TRACK:  return 87;  // KEYCODE_MEDIA_NEXT
        case VK_MEDIA_PREV_TRACK:  return 88;  // KEYCODE_MEDIA_PREVIOUS
        case VK_MEDIA_PLAY_PAUSE:  return 85;  // KEYCODE_MEDIA_PLAY_PAUSE
        case VK_MEDIA_STOP:        return 86;  // KEYCODE_MEDIA_STOP

        default: return 0; // Unknown
    }
}

static LRESULT CALLBACK GlobalKeyboardHookProc(int nCode, WPARAM wParam, LPARAM lParam) {
    if (g_currentInputCapture != nullptr) {
        return g_currentInputCapture->HandleKeyboardHook(nCode, wParam, lParam);
    }
    return CallNextHookEx(NULL, nCode, wParam, lParam);
}

static LRESULT CALLBACK GlobalMouseHookProc(int nCode, WPARAM wParam, LPARAM lParam) {
    if (g_currentInputCapture != nullptr) {
        return g_currentInputCapture->HandleMouseHook(nCode, wParam, lParam);
    }
    return CallNextHookEx(NULL, nCode, wParam, lParam);
}

InputCapture::InputCapture(BluetoothManager* btManager)
    : m_btManager(btManager)
    , m_keyboardHook(NULL)
    , m_mouseHook(NULL)
    , m_capturing(false)
    , m_sensitivity(3.0f)
    , m_recentering(false)
{
    g_currentInputCapture = this;
    ZeroMemory(m_keyState, sizeof(m_keyState));
}

InputCapture::~InputCapture() {
    StopCapture();
    if (g_currentInputCapture == this) {
        g_currentInputCapture = nullptr;
    }
}

bool InputCapture::StartCapture() {
    if (m_capturing) return true;

    // Center point of primary screen
    int screenW = GetSystemMetrics(SM_CXSCREEN);
    int screenH = GetSystemMetrics(SM_CYSCREEN);
    m_centerPt.x = screenW / 2;
    m_centerPt.y = screenH / 2;

    HINSTANCE hInst = GetModuleHandle(NULL);
    m_keyboardHook = SetWindowsHookEx(WH_KEYBOARD_LL, GlobalKeyboardHookProc, hInst, 0);
    m_mouseHook = SetWindowsHookEx(WH_MOUSE_LL, GlobalMouseHookProc, hInst, 0);

    if (!m_keyboardHook || !m_mouseHook) {
        StopCapture();
        return false;
    }

    m_capturing = true;
    m_recentering = true;
    ZeroMemory(m_keyState, sizeof(m_keyState));
    SetCursorPos(m_centerPt.x, m_centerPt.y);

    if (m_toggleCb) {
        m_toggleCb(true);
    }
    return true;
}

void InputCapture::StopCapture() {
    if (!m_capturing) return;

    m_capturing = false;
    ZeroMemory(m_keyState, sizeof(m_keyState));

    if (m_keyboardHook) {
        UnhookWindowsHookEx(m_keyboardHook);
        m_keyboardHook = NULL;
    }
    if (m_mouseHook) {
        UnhookWindowsHookEx(m_mouseHook);
        m_mouseHook = NULL;
    }

    if (m_toggleCb) {
        m_toggleCb(false);
    }
}

bool InputCapture::ToggleCapture() {
    if (m_capturing) {
        StopCapture();
        return false;
    } else {
        return StartCapture();
    }
}

LRESULT InputCapture::HandleKeyboardHook(int nCode, WPARAM wParam, LPARAM lParam) {
    if (nCode != HC_ACTION) {
        return CallNextHookEx(NULL, nCode, wParam, lParam);
    }

    KBDLLHOOKSTRUCT* pKbd = (KBDLLHOOKSTRUCT*)lParam;

    // Check for Toggle Hotkey: VK_F12 toggles capture on/off
    if (wParam == WM_KEYDOWN || wParam == WM_SYSKEYDOWN) {
        if (pKbd->vkCode == VK_F12) {
            ToggleCapture();
            return 1; // Handled
        }
    }

    if (!m_capturing) {
        return CallNextHookEx(NULL, nCode, wParam, lParam);
    }

    bool isDown = (wParam == WM_KEYDOWN || wParam == WM_SYSKEYDOWN);
    bool isUp   = (wParam == WM_KEYUP   || wParam == WM_SYSKEYUP);

    if (!isDown && !isUp) {
        return CallNextHookEx(NULL, nCode, wParam, lParam);
    }

    // Track key state in local table
    if (pKbd->vkCode == VK_LSHIFT || pKbd->vkCode == VK_RSHIFT || pKbd->vkCode == VK_SHIFT) {
        m_keyState[VK_SHIFT]  = isDown ? 0x80 : 0x00;
        m_keyState[pKbd->vkCode & 0xFF] = isDown ? 0x80 : 0x00;
    } else if (pKbd->vkCode == VK_LCONTROL || pKbd->vkCode == VK_RCONTROL || pKbd->vkCode == VK_CONTROL) {
        m_keyState[VK_CONTROL] = isDown ? 0x80 : 0x00;
        m_keyState[pKbd->vkCode & 0xFF] = isDown ? 0x80 : 0x00;
    } else if (pKbd->vkCode == VK_LMENU || pKbd->vkCode == VK_RMENU || pKbd->vkCode == VK_MENU) {
        m_keyState[VK_MENU]   = isDown ? 0x80 : 0x00;
        m_keyState[pKbd->vkCode & 0xFF] = isDown ? 0x80 : 0x00;
    } else if (pKbd->vkCode == VK_LWIN || pKbd->vkCode == VK_RWIN) {
        m_keyState[VK_LWIN]   = isDown ? 0x80 : 0x00;
        m_keyState[pKbd->vkCode & 0xFF] = isDown ? 0x80 : 0x00;
    } else {
        m_keyState[pKbd->vkCode & 0xFF] = isDown ? 0x80 : 0x00;
    }

    // Determine current state of modifiers combining internal tracking and system state
    bool shiftDown = (m_keyState[VK_SHIFT] & 0x80) != 0 ||
                     (m_keyState[VK_LSHIFT] & 0x80) != 0 ||
                     (m_keyState[VK_RSHIFT] & 0x80) != 0 ||
                     (GetAsyncKeyState(VK_SHIFT) & 0x8000) != 0 ||
                     (GetAsyncKeyState(VK_LSHIFT) & 0x8000) != 0 ||
                     (GetAsyncKeyState(VK_RSHIFT) & 0x8000) != 0 ||
                     (GetKeyState(VK_SHIFT) & 0x8000) != 0;

    bool ctrlDown  = (m_keyState[VK_CONTROL] & 0x80) != 0 ||
                     (m_keyState[VK_LCONTROL] & 0x80) != 0 ||
                     (m_keyState[VK_RCONTROL] & 0x80) != 0 ||
                     (GetAsyncKeyState(VK_CONTROL) & 0x8000) != 0 ||
                     (GetAsyncKeyState(VK_LCONTROL) & 0x8000) != 0 ||
                     (GetAsyncKeyState(VK_RCONTROL) & 0x8000) != 0 ||
                     (GetKeyState(VK_CONTROL) & 0x8000) != 0;

    bool altDown   = (m_keyState[VK_MENU] & 0x80) != 0 ||
                     (m_keyState[VK_LMENU] & 0x80) != 0 ||
                     (m_keyState[VK_RMENU] & 0x80) != 0 ||
                     (pKbd->flags & LLKHF_ALTDOWN) != 0 ||
                     (pKbd->vkCode == VK_MENU) ||
                     (pKbd->vkCode == VK_LMENU) ||
                     (pKbd->vkCode == VK_RMENU) ||
                     (GetAsyncKeyState(VK_MENU) & 0x8000) != 0 ||
                     (GetAsyncKeyState(VK_LMENU) & 0x8000) != 0 ||
                     (GetAsyncKeyState(VK_RMENU) & 0x8000) != 0 ||
                     (GetKeyState(VK_MENU) & 0x8000) != 0;

    bool metaDown  = (m_keyState[VK_LWIN] & 0x80) != 0 ||
                     (m_keyState[VK_RWIN] & 0x80) != 0 ||
                     (pKbd->vkCode == VK_LWIN) ||
                     (pKbd->vkCode == VK_RWIN) ||
                     (GetAsyncKeyState(VK_LWIN) & 0x8000) != 0 ||
                     (GetAsyncKeyState(VK_RWIN) & 0x8000) != 0;

    // Modifiers bitmask
    uint8_t modifiers = 0;
    if (shiftDown) modifiers |= KEY_MOD_SHIFT;
    if (ctrlDown)  modifiers |= KEY_MOD_CTRL;
    if (altDown)   modifiers |= KEY_MOD_ALT;
    if (metaDown)  modifiers |= KEY_MOD_META;

    // Map to Android keycode
    uint16_t androidKc = WindowsVkToAndroidKeycode(pKbd->vkCode);

    // Accurate keyboard state for low-level hook
    BYTE keyboardState[256] = {0};
    if (shiftDown) {
        keyboardState[VK_SHIFT]  = 0x80;
        keyboardState[VK_LSHIFT] = 0x80;
    }
    if (ctrlDown) {
        keyboardState[VK_CONTROL]  = 0x80;
        keyboardState[VK_LCONTROL] = 0x80;
    }
    if (altDown) {
        keyboardState[VK_MENU]  = 0x80;
        keyboardState[VK_LMENU] = 0x80;
    }
    if (GetKeyState(VK_CAPITAL) & 0x0001) keyboardState[VK_CAPITAL] = 0x01;
    if (GetKeyState(VK_NUMLOCK) & 0x0001) keyboardState[VK_NUMLOCK] = 0x01;

    // Try to get unicode character using active keyboard layout
    WCHAR unicodeChar = 0;
    HKL hkl = GetKeyboardLayout(0);

    // If Ctrl is held without Alt (not AltGr), this is a command shortcut (Ctrl+A, Ctrl+V, etc.)
    // NEVER produce a printable unicodeChar for Ctrl shortcuts so Android does not type literal letters!
    if (isDown && (!ctrlDown || (ctrlDown && altDown))) {
        if (ToUnicodeEx(pKbd->vkCode, pKbd->scanCode, keyboardState, &unicodeChar, 1, 0, hkl) != 1) {
            unicodeChar = 0;
        }
        if (unicodeChar < 32) {
            unicodeChar = 0; // Control character, not printable
        }

        // Direct fallback for keys that have fixed printable representation
        if (unicodeChar == 0) {
            if (pKbd->vkCode >= VK_NUMPAD0 && pKbd->vkCode <= VK_NUMPAD9) {
                unicodeChar = L'0' + (WCHAR)(pKbd->vkCode - VK_NUMPAD0);
            } else if (pKbd->vkCode == VK_MULTIPLY) {
                unicodeChar = L'*';
            } else if (pKbd->vkCode == VK_ADD) {
                unicodeChar = L'+';
            } else if (pKbd->vkCode == VK_SUBTRACT) {
                unicodeChar = L'-';
            } else if (pKbd->vkCode == VK_DECIMAL) {
                unicodeChar = L'.';
            } else if (pKbd->vkCode == VK_DIVIDE) {
                unicodeChar = L'/';
            } else if (pKbd->vkCode == VK_SPACE) {
                unicodeChar = L' ';
            }
        }
    } else {
        unicodeChar = 0;
    }

    KeyEventPayload payload;
    payload.win_vk     = (uint16_t)pKbd->vkCode;
    payload.android_kc = androidKc;
    payload.state      = isDown ? KEY_STATE_DOWN : KEY_STATE_UP;
    payload.modifiers  = modifiers;
    payload.unicode_ch = (uint16_t)unicodeChar;

    if (m_btManager) {
        m_btManager->SendPacket(MSG_KEY_EVENT, &payload, sizeof(payload));
    }

    // Suppress local Windows key handling while capturing
    return 1;
}

LRESULT InputCapture::HandleMouseHook(int nCode, WPARAM wParam, LPARAM lParam) {
    if (nCode != HC_ACTION || !m_capturing) {
        return CallNextHookEx(NULL, nCode, wParam, lParam);
    }

    MSLLHOOKSTRUCT* pMouse = (MSLLHOOKSTRUCT*)lParam;

    switch (wParam) {
        case WM_MOUSEMOVE: {
            if (m_recentering) {
                m_recentering = false;
                return 1;
            }

            int dx = pMouse->pt.x - m_centerPt.x;
            int dy = pMouse->pt.y - m_centerPt.y;

            if (dx != 0 || dy != 0) {
                MouseMovePayload payload;
                payload.dx = (int16_t)((float)dx * m_sensitivity);
                payload.dy = (int16_t)((float)dy * m_sensitivity);

                if (m_btManager) {
                    m_btManager->SendPacket(MSG_MOUSE_MOVE, &payload, sizeof(payload));
                }

                m_recentering = true;
                SetCursorPos(m_centerPt.x, m_centerPt.y);
            }
            return 1;
        }

        case WM_LBUTTONDOWN:
        case WM_LBUTTONUP: {
            MouseButtonPayload payload;
            payload.button = BTN_LEFT;
            payload.state  = (wParam == WM_LBUTTONDOWN) ? BTN_STATE_DOWN : BTN_STATE_UP;
            if (m_btManager) {
                m_btManager->SendPacket(MSG_MOUSE_BUTTON, &payload, sizeof(payload));
            }
            return 1;
        }

        case WM_RBUTTONDOWN:
        case WM_RBUTTONUP: {
            MouseButtonPayload payload;
            payload.button = BTN_RIGHT;
            payload.state  = (wParam == WM_RBUTTONDOWN) ? BTN_STATE_DOWN : BTN_STATE_UP;
            if (m_btManager) {
                m_btManager->SendPacket(MSG_MOUSE_BUTTON, &payload, sizeof(payload));
            }
            return 1;
        }

        case WM_MBUTTONDOWN:
        case WM_MBUTTONUP: {
            MouseButtonPayload payload;
            payload.button = BTN_MIDDLE;
            payload.state  = (wParam == WM_MBUTTONDOWN) ? BTN_STATE_DOWN : BTN_STATE_UP;
            if (m_btManager) {
                m_btManager->SendPacket(MSG_MOUSE_BUTTON, &payload, sizeof(payload));
            }
            return 1;
        }

        case WM_MOUSEWHEEL: {
            short wheelDelta = GET_WHEEL_DELTA_WPARAM(pMouse->mouseData);
            MouseWheelPayload payload;
            payload.delta_y = wheelDelta;
            payload.delta_x = 0;
            if (m_btManager) {
                m_btManager->SendPacket(MSG_MOUSE_WHEEL, &payload, sizeof(payload));
            }
            return 1;
        }

        case WM_MOUSEHWHEEL: {
            short wheelDelta = GET_WHEEL_DELTA_WPARAM(pMouse->mouseData);
            MouseWheelPayload payload;
            payload.delta_y = 0;
            payload.delta_x = wheelDelta;
            if (m_btManager) {
                m_btManager->SendPacket(MSG_MOUSE_WHEEL, &payload, sizeof(payload));
            }
            return 1;
        }
    }

    return CallNextHookEx(NULL, nCode, wParam, lParam);
}
