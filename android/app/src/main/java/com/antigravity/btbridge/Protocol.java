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
    public static final byte MSG_STYLUS_INPUT  = 0x50;

    // Stylus Actions
    public static final byte STYLUS_HOVER      = 0x00;
    public static final byte STYLUS_DOWN       = 0x01;
    public static final byte STYLUS_MOVE       = 0x02;
    public static final byte STYLUS_UP         = 0x03;

    // Stylus Buttons / Flags
    public static final byte STYLUS_FLAG_TIP      = 0x01;
    public static final byte STYLUS_FLAG_BARREL   = 0x02;
    public static final byte STYLUS_FLAG_ERASER   = 0x04;
    public static final byte STYLUS_FLAG_IN_RANGE = 0x08;

    // Mouse Buttons
    public static final byte BTN_LEFT   = 0x01;
    public static final byte BTN_RIGHT  = 0x02;
    public static final byte BTN_MIDDLE = 0x04;

    // Button / Key States
    public static final byte STATE_UP   = 0x00;
    public static final byte STATE_DOWN = 0x01;

    // System Actions
    public static final byte ACT_BACK             = 0x01;
    public static final byte ACT_HOME             = 0x02;
    public static final byte ACT_RECENTS          = 0x03;
    public static final byte ACT_NOTIFICATIONS    = 0x04;
    public static final byte ACT_VOLUME_UP        = 0x05;
    public static final byte ACT_VOLUME_DOWN      = 0x06;
    public static final byte ACT_LOCK_SCREEN      = 0x07;
    public static final byte ACT_VOLUME_MUTE      = 0x08;
    public static final byte ACT_MEDIA_PLAY_PAUSE = 0x09;
    public static final byte ACT_MEDIA_NEXT       = 0x0A;
    public static final byte ACT_MEDIA_PREV       = 0x0B;
    public static final byte ACT_SCREENSHOT       = 0x0C;
    public static final byte ACT_QUICK_SETTINGS   = 0x0D;

    // Key Modifiers
    public static final byte MOD_SHIFT            = 0x01;
    public static final byte MOD_CTRL             = 0x02;
    public static final byte MOD_ALT              = 0x04;
    public static final byte MOD_META             = 0x08;

    public static byte calcChecksum(byte type, byte len, byte[] payload, int offset, int payloadLen) {
        byte cs = (byte) (type ^ len);
        for (int i = 0; i < payloadLen; ++i) {
            cs ^= payload[offset + i];
        }
        return cs;
    }

    public static byte[] createStylusPacket(byte action, byte flags, int normX, int normY, int pressure, int tiltX, int tiltY) {
        byte[] payload = new byte[10];
        payload[0] = action;
        payload[1] = flags;
        payload[2] = (byte) (normX & 0xFF);
        payload[3] = (byte) ((normX >> 8) & 0xFF);
        payload[4] = (byte) (normY & 0xFF);
        payload[5] = (byte) ((normY >> 8) & 0xFF);
        payload[6] = (byte) (pressure & 0xFF);
        payload[7] = (byte) ((pressure >> 8) & 0xFF);
        payload[8] = (byte) tiltX;
        payload[9] = (byte) tiltY;

        byte checksum = calcChecksum(MSG_STYLUS_INPUT, (byte) 10, payload, 0, 10);
        byte[] packet = new byte[4 + 10 + 1];
        packet[0] = MAGIC_0;
        packet[1] = MAGIC_1;
        packet[2] = MSG_STYLUS_INPUT;
        packet[3] = 10;
        System.arraycopy(payload, 0, packet, 4, 10);
        packet[14] = checksum;
        return packet;
    }
}
