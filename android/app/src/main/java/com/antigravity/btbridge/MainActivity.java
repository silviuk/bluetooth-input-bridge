package com.antigravity.btbridge;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements BluetoothBridgeService.StatusListener {
    private static final int REQUEST_BT_PERMS = 101;
    private static final int REQUEST_OVERLAY_PERM = 102;

    private TextView mTvStatus;
    private TextView mTvPermBt;
    private TextView mTvPermOverlay;
    private TextView mTvPermAccess;
    private Button mBtnGrantBt;
    private Button mBtnGrantOverlay;
    private Button mBtnGrantAccess;
    private Button mBtnToggleServer;
    private EditText mEtTestInput;

    private BluetoothBridgeService mService;
    private boolean mBound = false;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothBridgeService.LocalBinder binder = (BluetoothBridgeService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.setStatusListener(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            mService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTvStatus = findViewById(R.id.tv_connection_status);
        mTvPermBt = findViewById(R.id.tv_perm_bt);
        mTvPermOverlay = findViewById(R.id.tv_perm_overlay);
        mTvPermAccess = findViewById(R.id.tv_perm_access);

        mBtnGrantBt = findViewById(R.id.btn_grant_bt);
        mBtnGrantOverlay = findViewById(R.id.btn_grant_overlay);
        mBtnGrantAccess = findViewById(R.id.btn_grant_access);
        mBtnToggleServer = findViewById(R.id.btn_toggle_server);
        mEtTestInput = findViewById(R.id.et_test_input);

        mBtnGrantBt.setOnClickListener(v -> requestBluetoothPermissions());
        mBtnGrantOverlay.setOnClickListener(v -> requestOverlayPermission());
        mBtnGrantAccess.setOnClickListener(v -> openAccessibilitySettings());

        mBtnToggleServer.setOnClickListener(v -> {
            if (mBound && mService != null) {
                mService.startServer();
                Toast.makeText(this, "Bluetooth listener restarted", Toast.LENGTH_SHORT).show();
            }
        });

        startBridgeService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatuses();
        if (mBound && mService != null) {
            mService.setStatusListener(this);
        }
    }

    private void startBridgeService() {
        Intent intent = new Intent(this, BluetoothBridgeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private void updatePermissionStatuses() {
        // Bluetooth
        boolean btOk = hasBluetoothPermissions();
        mTvPermBt.setText(btOk ? "Permission Granted" : "Required for RFCOMM connection");
        mBtnGrantBt.setEnabled(!btOk);
        mBtnGrantBt.setText(btOk ? "Granted" : "Grant");

        // Overlay
        boolean overlayOk = hasOverlayPermission();
        mTvPermOverlay.setText(overlayOk ? "Permission Granted" : "Draws mouse cursor on screen");
        mBtnGrantOverlay.setEnabled(!overlayOk);
        mBtnGrantOverlay.setText(overlayOk ? "Granted" : "Grant");

        // Accessibility
        boolean accessOk = (InputAccessibilityService.getInstance() != null);
        mTvPermAccess.setText(accessOk ? "Service Active" : "Injects taps, clicks, and keystrokes");
        mBtnGrantAccess.setEnabled(!accessOk);
        mBtnGrantAccess.setText(accessOk ? "Active" : "Enable");
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.POST_NOTIFICATIONS
            }, REQUEST_BT_PERMS);
        }
    }

    private boolean hasOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERM);
        }
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    @Override
    public void onStatusChanged(String status, boolean isConnected) {
        mTvStatus.setText(status);
        mTvStatus.setTextColor(isConnected ? 0xFF9ECE6A : 0xFF7AA2F7);
    }

    @Override
    public void onInputReceived(String info) {
        // Appends to test input for immediate verification
        if (mEtTestInput != null && mEtTestInput.hasFocus()) {
            mEtTestInput.append(info.replace("Key: ", ""));
        }
    }

    @Override
    protected void onDestroy() {
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        super.onDestroy();
    }
}
