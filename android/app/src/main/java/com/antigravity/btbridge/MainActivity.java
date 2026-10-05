package com.antigravity.btbridge;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity implements BluetoothBridgeService.StatusListener, AppLogger.LogListener {
    private static final int REQUEST_PERMS = 101;
    private static final int REQUEST_OVERLAY_PERM = 102;

    // Tabs
    private Button mBtnTabControls;
    private Button mBtnTabLogs;
    private ScrollView mScrollControlsTab;
    private LinearLayout mLayoutLogsTab;

    // Status & Mode
    private TextView mTvStatus;
    private RadioGroup mRgMode;
    private RadioButton mRbModeServer;
    private RadioButton mRbModeClient;
    private LinearLayout mLayoutServerControls;
    private LinearLayout mLayoutClientControls;

    // Server Controls
    private Button mBtnStartServer;
    private Button mBtnRestartServer;
    private Button mBtnStopServer;

    // Client Controls
    private Spinner mSpPairedDevices;
    private Button mBtnRefreshDevices;
    private Button mBtnConnectClient;
    private final List<BluetoothDevice> mPairedDeviceList = new ArrayList<>();
    private ArrayAdapter<String> mDeviceAdapter;

    // Permissions
    private TextView mTvPermBt;
    private TextView mTvPermOverlay;
    private TextView mTvPermAccess;
    private Button mBtnGrantBt;
    private Button mBtnGrantOverlay;
    private Button mBtnGrantAccess;
    private EditText mEtTestInput;

    // Logs
    private TextView mTvLogContent;
    private TextView mTvLogCount;
    private Button mBtnCopyLogs;
    private Button mBtnClearLogs;
    private ScrollView mScrollLogs;

    // Service binding
    private BluetoothBridgeService mService;
    private boolean mBound = false;
    private boolean mHasAutoRequested = false;
    private boolean mIsConnected = false;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothBridgeService.LocalBinder binder = (BluetoothBridgeService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.setStatusListener(MainActivity.this);
            AppLogger.i("MainActivity", "Connected to BluetoothBridgeService");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            mService = null;
            AppLogger.w("MainActivity", "Disconnected from BluetoothBridgeService");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind views
        mBtnTabControls = findViewById(R.id.btn_tab_controls);
        mBtnTabLogs = findViewById(R.id.btn_tab_logs);
        mScrollControlsTab = findViewById(R.id.scroll_controls_tab);
        mLayoutLogsTab = findViewById(R.id.layout_logs_tab);

        mTvStatus = findViewById(R.id.tv_connection_status);
        mRgMode = findViewById(R.id.rg_connection_mode);
        mRbModeServer = findViewById(R.id.rb_mode_server);
        mRbModeClient = findViewById(R.id.rb_mode_client);
        mLayoutServerControls = findViewById(R.id.layout_server_controls);
        mLayoutClientControls = findViewById(R.id.layout_client_controls);

        mBtnStartServer = findViewById(R.id.btn_start_server);
        mBtnRestartServer = findViewById(R.id.btn_restart_server);
        mBtnStopServer = findViewById(R.id.btn_stop_server);

        mSpPairedDevices = findViewById(R.id.sp_paired_devices);
        mBtnRefreshDevices = findViewById(R.id.btn_refresh_devices);
        mBtnConnectClient = findViewById(R.id.btn_connect_client);

        mTvPermBt = findViewById(R.id.tv_perm_bt);
        mTvPermOverlay = findViewById(R.id.tv_perm_overlay);
        mTvPermAccess = findViewById(R.id.tv_perm_access);
        mBtnGrantBt = findViewById(R.id.btn_grant_bt);
        mBtnGrantOverlay = findViewById(R.id.btn_grant_overlay);
        mBtnGrantAccess = findViewById(R.id.btn_grant_access);
        mEtTestInput = findViewById(R.id.et_test_input);

        mTvLogContent = findViewById(R.id.tv_log_content);
        mTvLogCount = findViewById(R.id.tv_log_count);
        mBtnCopyLogs = findViewById(R.id.btn_copy_logs);
        mBtnClearLogs = findViewById(R.id.btn_clear_logs);
        mScrollLogs = findViewById(R.id.scroll_logs);

        // Tab switcher
        mBtnTabControls.setOnClickListener(v -> switchTab(true));
        mBtnTabLogs.setOnClickListener(v -> switchTab(false));

        // Mode switcher
        mRgMode.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isServer = (checkedId == R.id.rb_mode_server);
            mLayoutServerControls.setVisibility(isServer ? View.VISIBLE : View.GONE);
            mLayoutClientControls.setVisibility(isServer ? View.GONE : View.VISIBLE);
            if (mService != null) {
                mService.setServerMode(isServer);
            }
            if (!isServer) {
                refreshPairedDevices();
            }
            AppLogger.i("MainActivity", "Mode switched to: " + (isServer ? "Server (Listening)" : "Client (Connect)"));
        });

        // Device adapter for client mode
        mDeviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item);
        mSpPairedDevices.setAdapter(mDeviceAdapter);
        mBtnRefreshDevices.setOnClickListener(v -> refreshPairedDevices());

        // Client Connect / Disconnect button
        mBtnConnectClient.setOnClickListener(v -> {
            if (mIsConnected) {
                if (mBound && mService != null) {
                    mService.disconnect();
                }
            } else {
                int pos = mSpPairedDevices.getSelectedItemPosition();
                if (pos >= 0 && pos < mPairedDeviceList.size()) {
                    BluetoothDevice targetDev = mPairedDeviceList.get(pos);
                    if (mBound && mService != null) {
                        mService.connectToDevice(targetDev);
                    } else if (hasBluetoothPermissions()) {
                        startBridgeServiceSafe();
                    } else {
                        Toast.makeText(this, "Please grant Bluetooth permission first", Toast.LENGTH_SHORT).show();
                        requestMissingRuntimePermissions();
                    }
                } else {
                    Toast.makeText(this, "No paired device selected. Pair your PC in Android Bluetooth Settings first.", Toast.LENGTH_LONG).show();
                    refreshPairedDevices();
                }
            }
        });

        // Server action buttons
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
                Toast.makeText(this, "Bluetooth service stopped", Toast.LENGTH_SHORT).show();
            } else {
                Intent stopIntent = new Intent(this, BluetoothBridgeService.class);
                stopIntent.setAction(BluetoothBridgeService.ACTION_STOP);
                startService(stopIntent);
            }
        });

        // Permission buttons
        mBtnGrantBt.setOnClickListener(v -> requestMissingRuntimePermissions());
        mBtnGrantOverlay.setOnClickListener(v -> requestOverlayPermission());
        mBtnGrantAccess.setOnClickListener(v -> checkAndPromptAccessibility());

        // Logs buttons
        mBtnCopyLogs.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData clip = ClipData.newPlainText("Lapdroid Logs", AppLogger.getAllLogs());
                cm.setPrimaryClip(clip);
                Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });

        mBtnClearLogs.setOnClickListener(v -> {
            AppLogger.clearLogs();
            Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show();
        });

        // Register logger listener
        AppLogger.addListener(this);
        mTvLogContent.setText(AppLogger.getAllLogs());

        AppLogger.i("MainActivity", "Lapdroid Android v0.3.0 initialized");

        // Initial permissions and service start
        if (!hasBluetoothPermissions()) {
            mHasAutoRequested = true;
            mTvStatus.setText("Waiting for permissions...");
            mTvStatus.setTextColor(0xFFE0AF68);
            requestMissingRuntimePermissions();
        } else {
            startBridgeServiceSafe();
            refreshPairedDevices();
        }
    }

    private void switchTab(boolean showControls) {
        mScrollControlsTab.setVisibility(showControls ? View.VISIBLE : View.GONE);
        mLayoutLogsTab.setVisibility(showControls ? View.GONE : View.VISIBLE);

        if (showControls) {
            mBtnTabControls.setBackgroundTintList(getColorStateList(R.color.primary));
            mBtnTabControls.setTextColor(0xFFFFFFFF);
            mBtnTabLogs.setBackgroundTintList(getColorStateList(R.color.card_bg));
            mBtnTabLogs.setTextColor(getColor(R.color.text_secondary));
        } else {
            mBtnTabLogs.setBackgroundTintList(getColorStateList(R.color.primary));
            mBtnTabLogs.setTextColor(0xFFFFFFFF);
            mBtnTabControls.setBackgroundTintList(getColorStateList(R.color.card_bg));
            mBtnTabControls.setTextColor(getColor(R.color.text_secondary));
            // Scroll logs to bottom
            mScrollLogs.post(() -> mScrollLogs.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void refreshPairedDevices() {
        if (!hasBluetoothPermissions()) {
            AppLogger.w("MainActivity", "Cannot refresh devices: Bluetooth permission missing");
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            AppLogger.w("MainActivity", "Bluetooth adapter disabled or unavailable");
            mDeviceAdapter.clear();
            mDeviceAdapter.add("Bluetooth is turned OFF");
            mDeviceAdapter.notifyDataSetChanged();
            return;
        }

        mPairedDeviceList.clear();
        mDeviceAdapter.clear();

        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded != null && !bonded.isEmpty()) {
                for (BluetoothDevice dev : bonded) {
                    String name = dev.getName();
                    if (name == null || name.isEmpty()) name = "Unknown Device";
                    mPairedDeviceList.add(dev);
                    mDeviceAdapter.add(name + " (" + dev.getAddress() + ")");
                }
                AppLogger.i("MainActivity", "Found " + bonded.size() + " paired Bluetooth device(s)");
            } else {
                mDeviceAdapter.add("No paired devices found");
                AppLogger.i("MainActivity", "No paired Bluetooth devices found");
            }
        } catch (SecurityException e) {
            AppLogger.e("MainActivity", "SecurityException querying paired devices: " + e.getMessage());
            mDeviceAdapter.add("Permission denied to query devices");
        }
        mDeviceAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatuses();

        if (hasBluetoothPermissions()) {
            if (!mBound) {
                startBridgeServiceSafe();
            }
            refreshPairedDevices();
        }

        if (mBound && mService != null) {
            mService.setStatusListener(this);
        }
    }

    private void startBridgeServiceSafe() {
        if (!hasBluetoothPermissions()) return;

        try {
            Intent intent = new Intent(this, BluetoothBridgeService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            AppLogger.i("MainActivity", "startBridgeServiceSafe: Service initiated and bind requested");
        } catch (Exception e) {
            AppLogger.e("MainActivity", "startBridgeServiceSafe failed", e);
            mTvStatus.setText("Service startup waiting for permissions");
        }
    }

    private void updatePermissionStatuses() {
        boolean btOk = hasBluetoothPermissions();
        mTvPermBt.setText(btOk ? "Permission Granted" : "Required for RFCOMM connection");
        mBtnGrantBt.setEnabled(!btOk);
        mBtnGrantBt.setText(btOk ? "Active" : "Grant");

        boolean overlayOk = hasOverlayPermission();
        mTvPermOverlay.setText(overlayOk ? "Permission Granted" : "Draws mouse cursor on screen");
        mBtnGrantOverlay.setEnabled(!overlayOk);
        mBtnGrantOverlay.setText(overlayOk ? "Active" : "Grant");

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
                refreshPairedDevices();
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
        mIsConnected = isConnected;
        mTvStatus.setText(status);
        mTvStatus.setTextColor(isConnected ? 0xFF9ECE6A : 0xFF7AA2F7);

        // Update Client Mode Connect button text
        if (isConnected) {
            mBtnConnectClient.setText("Disconnect");
            mBtnConnectClient.setBackgroundTintList(getColorStateList(R.color.status_red));
        } else {
            mBtnConnectClient.setText("Connect to PC");
            mBtnConnectClient.setBackgroundTintList(getColorStateList(R.color.status_green));
        }
    }

    @Override
    public void onInputReceived(String info) {
        if (mEtTestInput != null && mEtTestInput.hasFocus()) {
            mEtTestInput.append(info.replace("Key: ", ""));
        }
    }

    // AppLogger.LogListener
    @Override
    public void onLogAdded(String formattedLine) {
        if (mTvLogContent != null) {
            mTvLogContent.append(formattedLine + "\n");
            if (mScrollLogs != null && mLayoutLogsTab.getVisibility() == View.VISIBLE) {
                mScrollLogs.post(() -> mScrollLogs.fullScroll(View.FOCUS_DOWN));
            }
        }
    }

    @Override
    public void onLogsCleared() {
        if (mTvLogContent != null) {
            mTvLogContent.setText("");
        }
    }

    @Override
    protected void onDestroy() {
        AppLogger.removeListener(this);
        if (mBound) {
            try {
                unbindService(mConnection);
            } catch (Exception ignored) {}
            mBound = false;
        }
        super.onDestroy();
    }
}
