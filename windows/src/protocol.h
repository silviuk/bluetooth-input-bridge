#ifndef BT_BRIDGE_PROTOCOL_H
#define BT_BRIDGE_PROTOCOL_H

#include <stdint.h>

#pragma pack(push, 1)

// Magic bytes for packet framing
#define PROTOCOL_MAGIC_0 0xAA
#define PROTOCOL_MAGIC_1 0x55

// Standard UUID string for RFCOMM Serial Port Profile
// Standard SPP UUID: 00001101-0000-1000-8000-00805F9B34FB
#define BT_BRIDGE_SERVICE_NAME "Lapdroid_Bridge"
#define BT_BRIDGE_UUID_STR "00001101-0000-1000-8000-00805F9B34FB"

// Message Types
enum MessageType : uint8_t {
    MSG_PING            = 0x01,
    MSG_PONG            = 0x02,
    MSG_MOUSE_MOVE      = 0x10,
    MSG_MOUSE_BUTTON    = 0x11,
    MSG_MOUSE_WHEEL     = 0x12,
    MSG_KEY_EVENT       = 0x20,
    MSG_TEXT_STRING     = 0x21,
    MSG_SYSTEM_ACTION   = 0x30,
    MSG_DEVICE_INFO     = 0x40
};

// Mouse Buttons
enum MouseButton : uint8_t {
    BTN_LEFT   = 0x01,
    BTN_RIGHT  = 0x02,
    BTN_MIDDLE = 0x04
};

// Button States
enum ButtonState : uint8_t {
    BTN_STATE_UP   = 0x00,
    BTN_STATE_DOWN = 0x01
};

// Key States
enum KeyState : uint8_t {
    KEY_STATE_UP   = 0x00,
    KEY_STATE_DOWN = 0x01
};

// Key Modifiers bitmask
enum KeyModifiers : uint8_t {
    KEY_MOD_SHIFT = 0x01,
    KEY_MOD_CTRL  = 0x02,
    KEY_MOD_ALT   = 0x04,
    KEY_MOD_META  = 0x08  // Windows / Command key
};

// System Actions
enum SystemAction : uint8_t {
    ACT_BACK             = 0x01,
    ACT_HOME             = 0x02,
    ACT_RECENTS          = 0x03,
    ACT_NOTIFICATIONS    = 0x04,
    ACT_VOLUME_UP        = 0x05,
    ACT_VOLUME_DOWN      = 0x06,
    ACT_LOCK_SCREEN      = 0x07,
    ACT_VOLUME_MUTE      = 0x08,
    ACT_MEDIA_PLAY_PAUSE = 0x09,
    ACT_MEDIA_NEXT       = 0x0A,
    ACT_MEDIA_PREV       = 0x0B,
    ACT_SCREENSHOT       = 0x0C,
    ACT_QUICK_SETTINGS   = 0x0D
};

// Packet Header
struct PacketHeader {
    uint8_t magic0; // 0xAA
    uint8_t magic1; // 0x55
    uint8_t type;   // MessageType
    uint8_t length; // Length of payload
};

// Payload structures
struct MouseMovePayload {
    int16_t dx;
    int16_t dy;
};

struct MouseButtonPayload {
    uint8_t button; // MouseButton
    uint8_t state;  // ButtonState
};

struct MouseWheelPayload {
    int16_t delta_y; // Vertical scroll
    int16_t delta_x; // Horizontal scroll
};

struct KeyEventPayload {
    uint16_t win_vk;     // Windows Virtual Key code
    uint16_t android_kc; // Mapped Android Keycode
    uint8_t  state;      // KeyState
    uint8_t  modifiers;  // KeyModifiers
    uint16_t unicode_ch; // Unicode character if printable
};

struct SystemActionPayload {
    uint8_t action; // SystemAction
};

#pragma pack(pop)

// Helper to calculate packet checksum (XOR of type, len, and payload)
static inline uint8_t CalcChecksum(uint8_t type, uint8_t len, const uint8_t* payload) {
    uint8_t cs = type ^ len;
    for (uint8_t i = 0; i < len; ++i) {
        cs ^= payload[i];
    }
    return cs;
}

#endif // BT_BRIDGE_PROTOCOL_H
