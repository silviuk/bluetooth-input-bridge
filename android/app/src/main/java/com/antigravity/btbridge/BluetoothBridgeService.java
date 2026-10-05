package com.antigravity.btbridge;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BluetoothBridgeService extends Service {
    private static final String CHANNEL_ID = "lapdroid_bridge_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    private final IBinder mBinder = new LocalBinder();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private BluetoothAdapter mBluetoothAdapter;
    private AcceptThread mAcceptThread;
    private ConnectedThread mConnectedThread;
    private CursorOverlayView mCursorOverlay;

    private boolean mIsConnected = false;
    private boolean mIsServerMode = true;
    private StatusListener mStatusListener;

    private static volatile BluetoothBridgeService sInstance;

    public static BluetoothBridgeService getInstance() {
        return sInstance;
    }

    public boolean isConnected() {
        return mIsConnected;
    }

    public boolean sendStylusEvent(byte action, byte flags, int normX, int normY, int pressure, int tiltX, int tiltY) {
        ConnectedThread ct = mConnectedThread;
        if (ct != null && mIsConnected) {
            byte[] packet = Protocol.createStylusPacket(action, flags, normX, normY, pressure, tiltX, tiltY);
            return ct.write(packet);
        }
        return false;
    }

    public boolean isServerMode() {
        return mIsServerMode;
    }

    public void setServerMode(boolean serverMode) {
        this.mIsServerMode = serverMode;
    }

    public interface StatusListener {
        void onStatusChanged(String status, boolean isConnected);
        void onKeyInputReceived(int androidKeycode, char unicodeChar);
    }

    public class LocalBinder extends Binder {
        public BluetoothBridgeService getService() {
            return BluetoothBridgeService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        createNotificationChannel();

        try {
            Notification notification = buildNotification("Lapdroid active");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        mCursorOverlay = new CursorOverlayView(this);
        mCursorOverlay.show();

        startServer();
    }

    public static final String ACTION_START = "com.antigravity.btbridge.ACTION_START";
    public static final String ACTION_STOP = "com.antigravity.btbridge.ACTION_STOP";
    public static final String ACTION_RESTART = "com.antigravity.btbridge.ACTION_RESTART";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                stopServiceInternal();
                return START_NOT_STICKY;
            } else if (ACTION_RESTART.equals(action)) {
                startServer();
                return START_STICKY;
            } else if (ACTION_START.equals(action)) {
                startServer();
                return START_STICKY;
            }
        }
        return START_STICKY;
    }

    public synchronized void disconnect() {
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        mIsConnected = false;
        notifyStatus("Disconnected", false);
        AppLogger.i("BT-Bridge", "Disconnected from remote device");
    }

    public synchronized void stopServiceInternal() {
        AppLogger.i("BT-Bridge", "Stopping service completely");
        disconnect();
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        if (mCursorOverlay != null) {
            mCursorOverlay.hide();
        }
        mIsConnected = false;
        notifyStatus("Service Stopped", false);
        stopForeground(true);
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void setStatusListener(StatusListener listener) {
        mStatusListener = listener;
        notifyStatus(mIsConnected ? "Connected to Windows PC" : "Listening for Windows PC...", mIsConnected);
    }

    private void notifyStatus(String status, boolean connected) {
        mIsConnected = connected;
        AppLogger.i("BT-Bridge", "Status -> " + status + " (connected=" + connected + ")");
        mMainHandler.post(() -> {
            if (mStatusListener != null) {
                mStatusListener.onStatusChanged(status, connected);
            }
            updateNotification(status);
        });
    }

    public synchronized void startServer() {
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        // Defensive check: verify Bluetooth permissions before opening RFCOMM socket
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                AppLogger.w("BT-Bridge", "Cannot start server: BLUETOOTH_CONNECT permission not granted");
                notifyStatus("Waiting for Bluetooth permission...", false);
                return;
            }
        }

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            AppLogger.w("BT-Bridge", "Cannot start server: Bluetooth adapter disabled or null");
            notifyStatus("Bluetooth is disabled", false);
            return;
        }

        try {
            AppLogger.i("BT-Bridge", "Starting RFCOMM server listening on " + Protocol.SERVICE_NAME + " (UUID: " + Protocol.SPP_UUID + ")");
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
            notifyStatus("Listening for Windows PC...", false);
        } catch (Exception e) {
            AppLogger.e("BT-Bridge", "Failed to start RFCOMM server", e);
            notifyStatus("Bluetooth error: " + e.getMessage(), false);
        }

        // Refresh overlay view
        if (mCursorOverlay != null) {
            mCursorOverlay.show();
        }
    }

    public synchronized void connectToDevice(BluetoothDevice device) {
        if (device == null) {
            AppLogger.w("BT-Bridge", "connectToDevice called with null device");
            return;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        String devName = "Device";
        String devAddr = device.getAddress();
        try {
            devName = device.getName();
            if (devName == null || devName.isEmpty()) devName = devAddr;
        } catch (SecurityException ignored) {}

        final String finalDevName = devName;
        AppLogger.i("BT-Bridge", "Connecting to remote PC: " + finalDevName + " [" + devAddr + "]");
        notifyStatus("Connecting to " + finalDevName + "...", false);

        new Thread(() -> {
            BluetoothSocket socket = null;
            boolean connected = false;

            // Strategy 1: Standard SPP UUID
            try {
                AppLogger.i("BT-Bridge", "Attempting SPP UUID connection to " + devAddr + " (UUID: " + Protocol.SPP_UUID + ")");
                socket = device.createRfcommSocketToServiceRecord(Protocol.SPP_UUID);
                socket.connect();
                connected = true;
                AppLogger.i("BT-Bridge", "Connected via standard SPP UUID successfully!");
            } catch (Exception e1) {
                AppLogger.w("BT-Bridge", "SPP UUID connection failed: " + e1.getMessage() + ", trying RFCOMM channel 1 fallback...");
                try {
                    if (socket != null) socket.close();
                } catch (Exception ignored) {}

                // Strategy 2: Direct RFCOMM Channel 1 via reflection (widely used for PC SPP)
                try {
                    java.lang.reflect.Method m = device.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
                    socket = (BluetoothSocket) m.invoke(device, 1);
                    if (socket != null) {
                        socket.connect();
                        connected = true;
                        AppLogger.i("BT-Bridge", "Connected via RFCOMM channel 1 fallback successfully!");
                    }
                } catch (Exception e2) {
                    AppLogger.e("BT-Bridge", "Fallback channel 1 also failed: " + e2.getMessage());
                    try {
                        if (socket != null) socket.close();
                    } catch (Exception ignored) {}
                }
            }

            if (connected && socket != null) {
                manageConnectedSocket(socket);
            } else {
                AppLogger.e("BT-Bridge", "Connection failed completely to " + finalDevName);
                notifyStatus("Connection failed to " + finalDevName, false);
            }
        }).start();
    }

    private synchronized void manageConnectedSocket(BluetoothSocket socket) {
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
        }
        AppLogger.i("BT-Bridge", "Managing active socket connection, starting ConnectedThread");
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
        notifyStatus("Connected to Windows PC", true);
    }

    private class AcceptThread extends Thread {
        private BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            try {
                mmServerSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(
                        Protocol.SERVICE_NAME, Protocol.SPP_UUID);
                AppLogger.i("BT-Bridge", "BluetoothServerSocket listening on " + Protocol.SERVICE_NAME);
            } catch (Exception e) {
                AppLogger.e("BT-Bridge", "listenUsingRfcommWithServiceRecord failed", e);
                mmServerSocket = null;
            }
        }

        @Override
        public void run() {
            if (mmServerSocket == null) return;

            BluetoothSocket socket = null;
            while (!Thread.interrupted()) {
                try {
                    socket = mmServerSocket.accept();
                } catch (Exception e) {
                    AppLogger.i("BT-Bridge", "AcceptThread terminated: " + e.getMessage());
                    break;
                }

                if (socket != null) {
                    AppLogger.i("BT-Bridge", "Accepted incoming Bluetooth client connection from " + socket.getRemoteDevice().getAddress());
                    synchronized (BluetoothBridgeService.this) {
                        manageConnectedSocket(socket);
                    }
                    try {
                        mmServerSocket.close();
                    } catch (Exception ignored) {}
                    break;
                }
            }
        }

        public void cancel() {
            try {
                if (mmServerSocket != null) {
                    mmServerSocket.close();
                }
            } catch (Exception ignored) {}
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private volatile boolean mmRunning = true;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (Exception ignored) {}
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bufferLen = 0;

            while (mmRunning) {
                try {
                    int bytesRead = mmInStream.read(buffer, bufferLen, buffer.length - bufferLen);
                    if (bytesRead <= 0) break;
                    bufferLen += bytesRead;

                    // Parse packets
                    int offset = 0;
                    while (offset + 4 <= bufferLen) {
                        if (buffer[offset] != Protocol.MAGIC_0 || buffer[offset + 1] != Protocol.MAGIC_1) {
                            offset++;
                            continue;
                        }

                        byte type = buffer[offset + 2];
                        int payloadLen = buffer[offset + 3] & 0xFF;
                        int totalPacketLen = 4 + payloadLen + 1; // Header(4) + Payload + Checksum(1)

                        if (offset + totalPacketLen > bufferLen) {
                            // Incomplete packet, wait for more bytes
                            break;
                        }

                        byte checksum = buffer[offset + totalPacketLen - 1];
                        byte calculated = Protocol.calcChecksum(type, (byte) payloadLen, buffer, offset + 4, payloadLen);

                        if (checksum == calculated) {
                            handlePacket(type, buffer, offset + 4, payloadLen);
                        }

                        offset += totalPacketLen;
                    }

                    // Shift remaining bytes
                    if (offset > 0) {
                        int remaining = bufferLen - offset;
                        System.arraycopy(buffer, offset, buffer, 0, remaining);
                        bufferLen = remaining;
                    }
                } catch (Exception e) {
                    break;
                }
            }

            if (mmRunning) {
                notifyStatus("Windows PC disconnected", false);
                if (mIsServerMode) {
                    startServer();
                }
            }
        }

        public synchronized boolean write(byte[] bytes) {
            if (mmOutStream == null || !mmRunning) return false;
            try {
                mmOutStream.write(bytes);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        public void cancel() {
            mmRunning = false;
            try {
                mmSocket.close();
            } catch (Exception ignored) {}
        }
    }

    private void handlePacket(byte type, byte[] payload, int offset, int length) {
        ByteBuffer bb = ByteBuffer.wrap(payload, offset, length);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        switch (type) {
            case Protocol.MSG_MOUSE_MOVE: {
                if (length >= 4 && mCursorOverlay != null) {
                    short dx = bb.getShort();
                    short dy = bb.getShort();
                    mCursorOverlay.moveDelta(dx, dy);
                }
                break;
            }

            case Protocol.MSG_MOUSE_BUTTON: {
                if (length >= 2 && mCursorOverlay != null) {
                    byte button = bb.get();
                    byte state = bb.get();

                    if (state == Protocol.STATE_DOWN) {
                        float cx = mCursorOverlay.getCursorX();
                        float cy = mCursorOverlay.getCursorY();
                        InputAccessibilityService accessService = InputAccessibilityService.getInstance();

                        if (button == Protocol.BTN_LEFT) {
                            if (accessService != null) {
                                accessService.dispatchClick(cx, cy);
                            }
                        } else if (button == Protocol.BTN_RIGHT) {
                            if (accessService != null) {
                                accessService.performAction(Protocol.ACT_BACK);
                            }
                        } else if (button == Protocol.BTN_MIDDLE) {
                            if (accessService != null) {
                                accessService.performAction(Protocol.ACT_HOME);
                            }
                        }
                    }
                }
                break;
            }

            case Protocol.MSG_MOUSE_WHEEL: {
                if (length >= 4 && mCursorOverlay != null) {
                    short deltaY = bb.getShort();
                    float cx = mCursorOverlay.getCursorX();
                    float cy = mCursorOverlay.getCursorY();
                    InputAccessibilityService accessService = InputAccessibilityService.getInstance();
                    if (accessService != null) {
                        accessService.dispatchScroll(cx, cy, deltaY);
                    }
                }
                break;
            }

            case Protocol.MSG_KEY_EVENT: {
                if (length >= 8) {
                    int winVk = bb.getShort() & 0xFFFF;
                    int androidKc = bb.getShort() & 0xFFFF;
                    byte state = bb.get();
                    byte modifiers = bb.get();
                    char unicodeChar = (char) (bb.getShort() & 0xFFFF);

                    if (state == Protocol.STATE_DOWN) {
                        // 1. Direct Input IME Check (for tricky apps, Termux, and games)
                        LapdroidInputMethodService ime = LapdroidInputMethodService.getInstance();
                        if (ime != null && ime.forwardKey(androidKc, unicodeChar, modifiers)) {
                            AppLogger.d("BT-Bridge", "Key forwarded directly via Lapdroid IME: kc=" + androidKc);
                        } else {
                            InputAccessibilityService accessService = InputAccessibilityService.getInstance();
                            BridgeSettings settings = BridgeSettings.getInstance(this);

                            // 2. Alt + Tab (Recent Apps Switcher)
                            if (settings.isAltTabEnabled() && (modifiers & Protocol.MOD_ALT) != 0 && (androidKc == 61 || winVk == 9)) {
                                if (accessService != null) accessService.performAction(Protocol.ACT_RECENTS);
                            }
                            // 3. PrintScreen (Screenshot)
                            else if (androidKc == 120 || winVk == 44) {
                                if (accessService != null) accessService.performAction(Protocol.ACT_SCREENSHOT);
                            }
                            // 4. Volume & Audio Controls
                            else if (androidKc == 24) { // KEYCODE_VOLUME_UP
                                if (accessService != null) accessService.performAction(Protocol.ACT_VOLUME_UP);
                            } else if (androidKc == 25) { // KEYCODE_VOLUME_DOWN
                                if (accessService != null) accessService.performAction(Protocol.ACT_VOLUME_DOWN);
                            } else if (androidKc == 164) { // KEYCODE_VOLUME_MUTE
                                if (accessService != null) accessService.performAction(Protocol.ACT_VOLUME_MUTE);
                            }
                            // 5. Media Playback Controls
                            else if (androidKc == 85) { // KEYCODE_MEDIA_PLAY_PAUSE
                                if (accessService != null) accessService.performAction(Protocol.ACT_MEDIA_PLAY_PAUSE);
                            } else if (androidKc == 87) { // KEYCODE_MEDIA_NEXT
                                if (accessService != null) accessService.performAction(Protocol.ACT_MEDIA_NEXT);
                            } else if (androidKc == 88) { // KEYCODE_MEDIA_PREVIOUS
                                if (accessService != null) accessService.performAction(Protocol.ACT_MEDIA_PREV);
                            }
                            // 6. Navigation System Keys
                            else if (androidKc == 111 || androidKc == 4) { // Esc / Back
                                if (accessService != null) accessService.performAction(Protocol.ACT_BACK);
                            } else if (androidKc == 3) { // Windows / Meta Key
                                int winAction = settings.getWinAction();
                                if (winAction == BridgeSettings.WIN_ACTION_NOTIF) {
                                    if (accessService != null) accessService.performAction(Protocol.ACT_NOTIFICATIONS);
                                } else if (winAction == BridgeSettings.WIN_ACTION_RECENTS) {
                                    if (accessService != null) accessService.performAction(Protocol.ACT_RECENTS);
                                } else {
                                    if (accessService != null) accessService.performAction(Protocol.ACT_HOME);
                                }
                            } else if (androidKc == 187) { // Recents
                                if (accessService != null) accessService.performAction(Protocol.ACT_RECENTS);
                            }
                            // 7. General Text & Key Injection
                            else {
                                if (accessService != null) {
                                    accessService.injectTextOrKey(androidKc, unicodeChar, modifiers);
                                }
                            }
                        }

                        if (mStatusListener != null) {
                            final int finalKc = androidKc;
                            final char finalCh = unicodeChar;
                            mMainHandler.post(() -> mStatusListener.onKeyInputReceived(finalKc, finalCh));
                        }
                    }
                }
                break;
            }

            case Protocol.MSG_SYSTEM_ACTION: {
                if (length >= 1) {
                    byte action = bb.get();
                    InputAccessibilityService accessService = InputAccessibilityService.getInstance();
                    if (accessService != null) {
                        accessService.performAction(action);
                    }
                }
                break;
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Lapdroid Bridge Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background Bluetooth RFCOMM listener for Windows keyboard and mouse");
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 1, openIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent restartIntent = new Intent(this, BluetoothBridgeService.class);
        restartIntent.setAction(ACTION_RESTART);
        PendingIntent restartPi = PendingIntent.getService(this, 2, restartIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(this, BluetoothBridgeService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 3, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        builder.setContentTitle("Lapdroid")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(openPi)
                .setOngoing(true);

        Icon actionIcon = Icon.createWithResource(this, R.drawable.ic_launcher);
        builder.addAction(new Notification.Action.Builder(actionIcon, "Open", openPi).build());
        builder.addAction(new Notification.Action.Builder(actionIcon, "Restart", restartPi).build());
        builder.addAction(new Notification.Action.Builder(actionIcon, "Stop", stopPi).build());

        return builder.build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            try {
                nm.notify(NOTIFICATION_ID, buildNotification(text));
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onDestroy() {
        if (sInstance == this) sInstance = null;
        if (mAcceptThread != null) mAcceptThread.cancel();
        if (mConnectedThread != null) mConnectedThread.cancel();
        if (mCursorOverlay != null) mCursorOverlay.hide();
        super.onDestroy();
    }
}
