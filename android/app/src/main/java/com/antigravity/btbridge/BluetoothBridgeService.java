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
    private StatusListener mStatusListener;

    public interface StatusListener {
        void onStatusChanged(String status, boolean isConnected);
        void onInputReceived(String info);
    }

    public class LocalBinder extends Binder {
        public BluetoothBridgeService getService() {
            return BluetoothBridgeService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
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
                notifyStatus("Waiting for Bluetooth permission...", false);
                return;
            }
        }

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            notifyStatus("Bluetooth is disabled", false);
            return;
        }

        try {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
            notifyStatus("Listening for Windows PC...", false);
        } catch (Exception e) {
            notifyStatus("Bluetooth error: " + e.getMessage(), false);
        }

        // Refresh overlay view
        if (mCursorOverlay != null) {
            mCursorOverlay.show();
        }
    }

    public synchronized void connectToDevice(BluetoothDevice device) {
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        new Thread(() -> {
            String devName = "Device";
            try {
                devName = device.getName();
            } catch (SecurityException ignored) {}

            notifyStatus("Connecting to " + devName + "...", false);
            try {
                BluetoothSocket socket = device.createRfcommSocketToServiceRecord(Protocol.SPP_UUID);
                socket.connect();
                manageConnectedSocket(socket);
            } catch (Exception e) {
                notifyStatus("Connection failed: " + e.getMessage(), false);
                startServer();
            }
        }).start();
    }

    private synchronized void manageConnectedSocket(BluetoothSocket socket) {
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
        }
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
            } catch (Exception e) {
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
                    break;
                }

                if (socket != null) {
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
                startServer();
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
                        InputAccessibilityService accessService = InputAccessibilityService.getInstance();
                        if (androidKc == 111 || androidKc == 4) { // Esc / Back
                            if (accessService != null) accessService.performAction(Protocol.ACT_BACK);
                        } else if (androidKc == 3) { // Win / Home
                            if (accessService != null) accessService.performAction(Protocol.ACT_HOME);
                        } else if (androidKc == 187) { // Recents
                            if (accessService != null) accessService.performAction(Protocol.ACT_RECENTS);
                        } else {
                            if (accessService != null) {
                                accessService.injectTextOrKey(androidKc, unicodeChar);
                            }
                        }

                        if (mStatusListener != null && unicodeChar != 0) {
                            mMainHandler.post(() -> mStatusListener.onInputReceived("Key: " + unicodeChar));
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
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder.setContentTitle("Lapdroid")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
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
        if (mAcceptThread != null) mAcceptThread.cancel();
        if (mConnectedThread != null) mConnectedThread.cancel();
        if (mCursorOverlay != null) mCursorOverlay.hide();
        super.onDestroy();
    }
}
