package com.antigravity.btbridge;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements BluetoothBridgeService.StatusListener {
    private static final int REQUEST_PERMS = 101;
    private static final int REQUEST_OVERLAY_PERM = 102;

    private TextView mTvStatus;
    private TextView mTvPermBt;
    private TextView mTvPermOverlay;
    private TextView mTvPermAccess;
    private Button mBtnGrantBt;
    private Button mBtnGrantOverlay;
    private Button mBtnGrantAccess;
    private Button mBtnStartServer;
    private Button mBtnRestartServer;
    private Button mBtnStopServer;
    private EditText mEtTestInput;

    private BluetoothBridgeService mService;
    private boolean mBound = false;
    private boolean mHasAutoRequested = false;

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
        mBtnStartServer = findViewById(R.id.btn_start_server);
        mBtnRestartServer = findViewById(R.id.btn_restart_server);
        mBtnStopServer = findViewById(R.id.btn_stop_server);
        mEtTestInput = findViewById(R.id.et_test_input);

        mBtnGrantBt.setOnClickListener(v -> requestMissingRuntimePermissions());
        mBtnGrantOverlay.setOnClickListener(v -> requestOverlayPermission());
        mBtnGrantAccess.setOnClickListener(v -> checkAndPromptAccessibility());

        mBtnStartServer.setOnClickListener(v -> {
            if (mBound && mService != null) {
                mService.startServer();
                Toast.makeText(this, "Bluetooth listener started", Toast.LENGTH_SHORT).show();
            } else if (hasBluetoothPermissions()) {
                startBridgeServiceSafe();
            } else {
                Toast.makeText(this, "Please grant Bluetooth permission first", Toast.LENGTH_SHORT).show();
                requestMissingRuntimePermissions();
            }
        });

        mBtnRestartServer.setOnClickListener(v -> {
            if (mBound && mService != null) {
                mService.startServer();
                Toast.makeText(this, "Bluetooth listener restarted", Toast.LENGTH_SHORT).show();
            } else if (hasBluetoothPermissions()) {
                startBridgeServiceSafe();
            } else {
                requestMissingRuntimePermissions();
            }
        });

        mBtnStopServer.setOnClickListener(v -> {
            if (mBound && mService != null) {
                mService.stopServiceInternal();
                Toast.makeText(this, "Bluetooth listener stopped", Toast.LENGTH_SHORT).show();
            } else {
                Intent stopIntent = new Intent(this, BluetoothBridgeService.class);
                stopIntent.setAction(BluetoothBridgeService.ACTION_STOP);
                startService(stopIntent);
            }
        });

        // First-run automatic permission prompt
        if (!hasBluetoothPermissions()) {
            mHasAutoRequested = true;
            mTvStatus.setText("Waiting for permissions...");
            mTvStatus.setTextColor(0xFFE0AF68); // Amber warning
            requestMissingRuntimePermissions();
        } else {
            // Permissions already granted, start service safely
            startBridgeServiceSafe();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatuses();

        // If Bluetooth permission was granted and service not yet started, start it
        if (hasBluetoothPermissions() && !mBound) {
            startBridgeServiceSafe();
        }

        if (mBound && mService != null) {
            mService.setStatusListener(this);
            if (hasOverlayPermission()) {
                mService.startServer();
            }
        }
    }

    private void startBridgeServiceSafe() {
        if (!hasBluetoothPermissions()) {
            return; // Prevent crash when permissions not granted
        }

        try {
            Intent intent = new Intent(this, BluetoothBridgeService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            e.printStackTrace();
            mTvStatus.setText("Service startup waiting for permissions");
        }
    }

    private void updatePermissionStatuses() {
        // Bluetooth
        boolean btOk = hasBluetoothPermissions();
        mTvPermBt.setText(btOk ? "Permission Granted" : "Required for RFCOMM connection");
        mBtnGrantBt.setEnabled(!btOk);
        mBtnGrantBt.setText(btOk ? "Active" : "Grant");

        // Overlay
        boolean overlayOk = hasOverlayPermission();
        mTvPermOverlay.setText(overlayOk ? "Permission Granted" : "Draws mouse cursor on screen");
        mBtnGrantOverlay.setEnabled(!overlayOk);
        mBtnGrantOverlay.setText(overlayOk ? "Active" : "Grant");

        // Accessibility
        boolean accessOk = (InputAccessibilityService.getInstance() != null);
        mTvPermAccess.setText(accessOk ? "Service Active" : "Injects taps, clicks, and keystrokes");
        mBtnGrantAccess.setEnabled(!accessOk);
        mBtnGrantAccess.setText(accessOk ? "Active" : "Enable");

        // Guide dialog if overlay is missing after Bluetooth is granted
        if (btOk && !overlayOk && !mHasAutoRequested) {
            // Prompt user about overlay
        }
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestMissingRuntimePermissions() {
        List<String> perms = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!perms.isEmpty()) {
            requestPermissions(perms.toArray(new String[0]), REQUEST_PERMS);
        } else {
            // Check overlay next
            checkAndPromptOverlay();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            updatePermissionStatuses();

            if (allGranted || hasBluetoothPermissions()) {
                startBridgeServiceSafe();
                checkAndPromptOverlay();
            } else {
                Toast.makeText(this, "Bluetooth permission is required for Lapdroid", Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean hasOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void checkAndPromptOverlay() {
        if (!hasOverlayPermission() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            new AlertDialog.Builder(this)
                    .setTitle("Step 2: Floating Mouse Cursor")
                    .setMessage("Lapdroid needs permission to display the mouse pointer cursor over apps while controlling your phone.")
                    .setPositiveButton("Grant Permission", (dialog, which) -> requestOverlayPermission())
                    .setNegativeButton("Later", null)
                    .show();
        } else {
            checkAndPromptAccessibility();
        }
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERM);
        }
    }

    private void checkAndPromptAccessibility() {
        if (InputAccessibilityService.getInstance() == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Step 3: Accessibility Engine")
                    .setMessage("Lapdroid needs Accessibility access to inject keyboard typing and touchpad gestures.\n\n" +
                            "ℹ️ If Android 13/14 blocks this with 'Restricted setting':\n" +
                            "1. Tap 'App Info' below\n" +
                            "2. Tap the three dots (⋮) in the top right corner\n" +
                            "3. Tap 'Allow restricted settings'\n" +
                            "4. Return and tap 'Accessibility Settings'")
                    .setPositiveButton("Accessibility Settings", (dialog, which) -> openAccessibilitySettings())
                    .setNeutralButton("Open App Info", (dialog, which) -> openAppInfoSettings())
                    .setNegativeButton("Later", null)
                    .show();
        }
    }

    private void openAppInfoSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open App Info", Toast.LENGTH_SHORT).show();
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
        if (mEtTestInput != null && mEtTestInput.hasFocus()) {
            mEtTestInput.append(info.replace("Key: ", ""));
        }
    }

    @Override
    protected void onDestroy() {
        if (mBound) {
            try {
                unbindService(mConnection);
            } catch (Exception ignored) {}
            mBound = false;
        }
        super.onDestroy();
    }
}
