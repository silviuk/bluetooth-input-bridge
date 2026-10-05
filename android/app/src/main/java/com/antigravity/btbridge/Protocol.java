package com.antigravity.btbridge;

import java.util.UUID;

public class Protocol {
    public static final byte MAGIC_0 = (byte) 0xAA;
    public static final byte MAGIC_1 = (byte) 0x55;

    public static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static final String SERVICE_NAME = "Lapdroid_Bridge";

    // Message Types
    public static final byte MSG_PING          = 0x01;
    public static final byte MSG_PONG          = 0x02;
    public static final byte MSG_MOUSE_MOVE    = 0x10;
    public static final byte MSG_MOUSE_BUTTON  = 0x11;
    public static final byte MSG_MOUSE_WHEEL   = 0x12;
    public static final byte MSG_KEY_EVENT     = 0x20;
    public static final byte MSG_TEXT_STRING   = 0x21;
    public static final byte MSG_SYSTEM_ACTION = 0x30;
    public static final byte MSG_DEVICE_INFO   = 0x40;

    // Mouse Buttons
    public static final byte BTN_LEFT   = 0x01;
    public static final byte BTN_RIGHT  = 0x02;
    public static final byte BTN_MIDDLE = 0x04;

    // Button / Key States
    public static final byte STATE_UP   = 0x00;
    public static final byte STATE_DOWN = 0x01;

    // System Actions
    public static final byte ACT_BACK          = 0x01;
    public static final byte ACT_HOME          = 0x02;
    public static final byte ACT_RECENTS       = 0x03;
    public static final byte ACT_NOTIFICATIONS = 0x04;
    public static final byte ACT_VOLUME_UP     = 0x05;
    public static final byte ACT_VOLUME_DOWN   = 0x06;
    public static final byte ACT_LOCK_SCREEN   = 0x07;

    public static byte calcChecksum(byte type, byte len, byte[] payload, int offset, int payloadLen) {
        byte cs = (byte) (type ^ len);
        for (int i = 0; i < payloadLen; ++i) {
            cs ^= payload[offset + i];
        }
        return cs;
    }
}
