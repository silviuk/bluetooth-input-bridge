package com.antigravity.btbridge;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
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
    private Button mBtnTabSetup;
    private Button mBtnTabLogs;
    private ScrollView mScrollControlsTab;
    private ScrollView mScrollSetupTab;
    private LinearLayout mLayoutLogsTab;

    // Status & Mode
    private TextView mTvStatus;
    private TextView mTvConnectionDot;
    private Button mBtnModeServer;
    private Button mBtnModeClient;
    private LinearLayout mLayoutClientControls;
    private boolean mIsServerMode = true;

    // Dynamic Action Buttons (matching notification)
    private Button mBtnActionStart;
    private Button mBtnActionStop;
    private Button mBtnActionRestart;
    private Button mBtnActionExit;

    // Client Controls
    private Spinner mSpPairedDevices;
    private Button mBtnRefreshDevices;
    private Button mBtnConnectClient;
    private final List<BluetoothDevice> mPairedDeviceList = new ArrayList<>();
    private ArrayAdapter<String> mDeviceAdapter;

    // Permissions (in Setup & About Tab)
    private TextView mTvPermBt;
    private TextView mTvPermOverlay;
    private TextView mTvPermAccess;
    private Button mBtnGrantBt;
    private Button mBtnGrantOverlay;
    private Button mBtnGrantAccess;
    private EditText mEtTestInput;

    // Keyboard & Tricky Keys Options (M3 Switches)
    private Spinner mSpEnterMode;
    private Spinner mSpTabMode;
    private Spinner mSpWinAction;
    private Switch mSwCtrlShortcuts;
    private Switch mSwAltTab;
    private Switch mSwIgnorePlaceholder;
    private TextView mTvImeStatus;
    private Button mBtnOpenImeSettings;
    private Button mBtnSwitchIme;
    private Button mBtnOpenDigitizer;

    // About & GitHub
    private Button mBtnGithubRepo;

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
    private boolean mIsExiting = false;
    private int mTestSelAnchor = -1;
    private int mTestSelCaret = -1;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothBridgeService.LocalBinder binder = (BluetoothBridgeService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.setStatusListener(MainActivity.this);
            updateActionButtons(mService.isRunning());
            AppLogger.i("MainActivity", "Connected to BluetoothBridgeService");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            mService = null;
            updateActionButtons(false);
            AppLogger.w("MainActivity", "Disconnected from BluetoothBridgeService");
        }
    };

    private final BroadcastReceiver mExitReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && BluetoothBridgeService.ACTION_EXIT_APP.equals(intent.getAction())) {
                killAppNow();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind tabs
        mBtnTabControls = findViewById(R.id.btn_tab_controls);
        mBtnTabSetup = findViewById(R.id.btn_tab_setup);
        mBtnTabLogs = findViewById(R.id.btn_tab_logs);
        mScrollControlsTab = findViewById(R.id.scroll_controls_tab);
        mScrollSetupTab = findViewById(R.id.scroll_setup_tab);
        mLayoutLogsTab = findViewById(R.id.layout_logs_tab);

        // Bind status and mode
        mTvStatus = findViewById(R.id.tv_connection_status);
        mTvConnectionDot = findViewById(R.id.tv_connection_dot);
        mBtnModeServer = findViewById(R.id.btn_mode_server);
        mBtnModeClient = findViewById(R.id.btn_mode_client);
        mLayoutClientControls = findViewById(R.id.layout_client_controls);

        // Bind Dynamic Action Buttons (matching notification)
        mBtnActionStart = findViewById(R.id.btn_action_start);
        mBtnActionStop = findViewById(R.id.btn_action_stop);
        mBtnActionRestart = findViewById(R.id.btn_action_restart);
        mBtnActionExit = findViewById(R.id.btn_action_exit);

        // Bind client controls
        mSpPairedDevices = findViewById(R.id.sp_paired_devices);
        mBtnRefreshDevices = findViewById(R.id.btn_refresh_devices);
        mBtnConnectClient = findViewById(R.id.btn_connect_client);

        // Bind permissions & test input
        mTvPermBt = findViewById(R.id.tv_perm_bt);
        mTvPermOverlay = findViewById(R.id.tv_perm_overlay);
        mTvPermAccess = findViewById(R.id.tv_perm_access);
        mBtnGrantBt = findViewById(R.id.btn_grant_bt);
        mBtnGrantOverlay = findViewById(R.id.btn_grant_overlay);
        mBtnGrantAccess = findViewById(R.id.btn_grant_access);
        mEtTestInput = findViewById(R.id.et_test_input);

        // Bind Keyboard & Tricky Keys Options (M3 Switches)
        mSpEnterMode = findViewById(R.id.sp_enter_mode);
        mSpTabMode = findViewById(R.id.sp_tab_mode);
        mSpWinAction = findViewById(R.id.sp_win_action);
        mSwCtrlShortcuts = findViewById(R.id.sw_ctrl_shortcuts);
        mSwAltTab = findViewById(R.id.sw_alt_tab);
        mSwIgnorePlaceholder = findViewById(R.id.sw_ignore_placeholder);
        mTvImeStatus = findViewById(R.id.tv_ime_status);
        mBtnOpenImeSettings = findViewById(R.id.btn_open_ime_settings);
        mBtnSwitchIme = findViewById(R.id.btn_switch_ime);
        mBtnOpenDigitizer = findViewById(R.id.btn_open_digitizer);
        initKeyboardOptions();

        // Bind About & GitHub
        mBtnGithubRepo = findViewById(R.id.btn_github_repo);
        if (mBtnGithubRepo != null) {
            mBtnGithubRepo.setOnClickListener(v -> {
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/silviuk/lapdroid"));
                    startActivity(browserIntent);
                } catch (Exception e) {
                    Toast.makeText(this, "Could not open browser: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Bind Logs
        mTvLogContent = findViewById(R.id.tv_log_content);
        mTvLogCount = findViewById(R.id.tv_log_count);
        mBtnCopyLogs = findViewById(R.id.btn_copy_logs);
        mBtnClearLogs = findViewById(R.id.btn_clear_logs);
        mScrollLogs = findViewById(R.id.scroll_logs);

        // Tab switcher (M3 Segmented Pill Tabs)
        mBtnTabControls.setOnClickListener(v -> switchTab(0));
        mBtnTabSetup.setOnClickListener(v -> switchTab(1));
        mBtnTabLogs.setOnClickListener(v -> switchTab(2));

        // M3 Segmented Mode Switcher
        mBtnModeServer.setOnClickListener(v -> setServerMode(true));
        mBtnModeClient.setOnClickListener(v -> setServerMode(false));

        // Device adapter for client mode
        mDeviceAdapter = new ArrayAdapter<>(this, R.layout.m3_spinner_item);
        mDeviceAdapter.setDropDownViewResource(R.layout.m3_spinner_dropdown_item);
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

        // Dynamic Action Button Listeners
        mBtnActionStart.setOnClickListener(v -> {
            if (mBound && mService != null) {
                mService.resumeBridge();
                updateActionButtons(true);
                Toast.makeText(this, "Bluetooth bridge started", Toast.LENGTH_SHORT).show();
            } else if (hasBluetoothPermissions()) {
                startBridgeServiceSafe();
                updateActionButtons(true);
            } else {
                Toast.makeText(this, "Please grant Bluetooth permission first", Toast.LENGTH_SHORT).show();
                requestMissingRuntimePermissions();
            }
        });

        mBtnActionStop.setOnClickListener(v -> {
            if (mBound && mService != null) {
                mService.pauseBridge();
            } else {
                Intent stopIntent = new Intent(this, BluetoothBridgeService.class);
                stopIntent.setAction(BluetoothBridgeService.ACTION_STOP);
                startService(stopIntent);
            }
            updateActionButtons(false);
            Toast.makeText(this, "Bluetooth bridge stopped", Toast.LENGTH_SHORT).show();
        });

        mBtnActionRestart.setOnClickListener(v -> {
            if (mBound && mService != null) {
                mService.restartBridge();
            } else {
                Intent restartIntent = new Intent(this, BluetoothBridgeService.class);
                restartIntent.setAction(BluetoothBridgeService.ACTION_RESTART);
                startService(restartIntent);
            }
            updateActionButtons(true);
            Toast.makeText(this, "Bluetooth bridge restarted", Toast.LENGTH_SHORT).show();
        });

        mBtnActionExit.setOnClickListener(v -> {
            if (mBound && mService != null) {
                mService.exitApplication();
            } else {
                Intent exitIntent = new Intent(this, BluetoothBridgeService.class);
                exitIntent.setAction(BluetoothBridgeService.ACTION_EXIT);
                startService(exitIntent);
            }
            killAppNow();
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

        Switch swEnableLogging = findViewById(R.id.sw_enable_logging);
        if (swEnableLogging != null) {
            swEnableLogging.setChecked(AppLogger.isLoggingEnabled());
            swEnableLogging.setOnCheckedChangeListener((btn, isChecked) -> {
                AppLogger.setLoggingEnabled(isChecked);
                Toast.makeText(this, isChecked ? "Live logging enabled" : "Live logging paused", Toast.LENGTH_SHORT).show();
            });
        }

        // Register logger listener
        AppLogger.addListener(this);
        mTvLogContent.setText(AppLogger.getAllLogs());

        // Register Exit broadcast receiver
        IntentFilter filter = new IntentFilter(BluetoothBridgeService.ACTION_EXIT_APP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mExitReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mExitReceiver, filter);
        }

        AppLogger.i("MainActivity", "Lapdroid Android v0.3.0 initialized");

        // Initial permissions and service start
        if (!hasBluetoothPermissions()) {
            mHasAutoRequested = true;
            mTvStatus.setText("Waiting for permissions...");
            mTvConnectionDot.setText("○ ");
            mTvConnectionDot.setTextColor(getColor(R.color.m3_warning));
            requestMissingRuntimePermissions();
            updateActionButtons(false);
        } else {
            startBridgeServiceSafe();
            refreshPairedDevices();
            updateActionButtons(true);
        }
    }

    private void setServerMode(boolean isServer) {
        mIsServerMode = isServer;
        mLayoutClientControls.setVisibility(isServer ? View.GONE : View.VISIBLE);
        mBtnModeServer.setBackgroundResource(isServer ? R.drawable.m3_pill_active : R.drawable.m3_pill_inactive);
        mBtnModeServer.setTextColor(isServer ? 0xFFFFFFFF : getColor(R.color.m3_on_surface_variant));
        mBtnModeClient.setBackgroundResource(!isServer ? R.drawable.m3_pill_active : R.drawable.m3_pill_inactive);
        mBtnModeClient.setTextColor(!isServer ? 0xFFFFFFFF : getColor(R.color.m3_on_surface_variant));

        if (mService != null) {
            mService.setServerMode(isServer);
        }
        if (!isServer) {
            refreshPairedDevices();
        }
        AppLogger.i("MainActivity", "Mode switched to: " + (isServer ? "Server (Listening)" : "Client (Connect)"));
    }

    private void killAppNow() {
        if (mIsExiting) return;
        mIsExiting = true;
        AppLogger.i("MainActivity", "killAppNow: completely terminating application process");
        if (mBound) {
            try {
                unbindService(mConnection);
            } catch (Exception ignored) {}
            mBound = false;
        }

        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancelAll();
            }
        } catch (Exception ignored) {}

        finishAndRemoveTask();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(0);
            } catch (Exception ignored) {}
        }, 300);
    }

    private void updateActionButtons(boolean isRunning) {
        runOnUiThread(() -> {
            if (isRunning) {
                mBtnActionStart.setVisibility(View.GONE);
                mBtnActionStop.setVisibility(View.VISIBLE);
                mBtnActionRestart.setVisibility(View.VISIBLE);
                mBtnActionExit.setVisibility(View.VISIBLE);
            } else {
                mBtnActionStart.setVisibility(View.VISIBLE);
                mBtnActionStop.setVisibility(View.GONE);
                mBtnActionRestart.setVisibility(View.GONE);
                mBtnActionExit.setVisibility(View.VISIBLE);
            }
        });
    }

    private void switchTab(int tabIndex) {
        mScrollControlsTab.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        mScrollSetupTab.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        mLayoutLogsTab.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);

        mBtnTabControls.setBackgroundResource(tabIndex == 0 ? R.drawable.m3_pill_active : R.drawable.m3_pill_inactive);
        mBtnTabControls.setTextColor(tabIndex == 0 ? 0xFFFFFFFF : getColor(R.color.m3_on_surface_variant));

        mBtnTabSetup.setBackgroundResource(tabIndex == 1 ? R.drawable.m3_pill_active : R.drawable.m3_pill_inactive);
        mBtnTabSetup.setTextColor(tabIndex == 1 ? 0xFFFFFFFF : getColor(R.color.m3_on_surface_variant));

        mBtnTabLogs.setBackgroundResource(tabIndex == 2 ? R.drawable.m3_pill_active : R.drawable.m3_pill_inactive);
        mBtnTabLogs.setTextColor(tabIndex == 2 ? 0xFFFFFFFF : getColor(R.color.m3_on_surface_variant));

        if (tabIndex == 2 && mScrollLogs != null) {
            mScrollLogs.post(() -> mScrollLogs.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void initKeyboardOptions() {
        BridgeSettings settings = BridgeSettings.getInstance(this);

        // 1. Enter Mode
        String[] enterModes = {
            "Smart (Newline in multiline, Action/Click in single-line)",
            "Always Insert Newline (\\n)",
            "Always Submit / Send Action"
        };
        ArrayAdapter<String> enterAdapter = new ArrayAdapter<>(this, R.layout.m3_spinner_item, enterModes);
        enterAdapter.setDropDownViewResource(R.layout.m3_spinner_dropdown_item);
        mSpEnterMode.setAdapter(enterAdapter);
        mSpEnterMode.setSelection(settings.getEnterMode());
        mSpEnterMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                settings.setEnterMode(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 2. Tab Mode
        String[] tabModes = {
            "Navigate Focus to Next Field (Shift+Tab for Prev)",
            "Insert 4 Spaces",
            "Insert Tab Character (\\t)"
        };
        ArrayAdapter<String> tabAdapter = new ArrayAdapter<>(this, R.layout.m3_spinner_item, tabModes);
        tabAdapter.setDropDownViewResource(R.layout.m3_spinner_dropdown_item);
        mSpTabMode.setAdapter(tabAdapter);
        mSpTabMode.setSelection(settings.getTabMode());
        mSpTabMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                settings.setTabMode(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 3. Windows / Meta Key Action
        String[] winActions = {
            "Open Android Home Screen",
            "Open Notification Shade",
            "Open Recent Apps Switcher"
        };
        ArrayAdapter<String> winAdapter = new ArrayAdapter<>(this, R.layout.m3_spinner_item, winActions);
        winAdapter.setDropDownViewResource(R.layout.m3_spinner_dropdown_item);
        mSpWinAction.setAdapter(winAdapter);
        mSpWinAction.setSelection(settings.getWinAction());
        mSpWinAction.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                settings.setWinAction(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 4. M3 Switch Toggles
        mSwCtrlShortcuts.setChecked(settings.isCtrlShortcutsEnabled());
        mSwCtrlShortcuts.setOnCheckedChangeListener((btn, isChecked) -> settings.setCtrlShortcutsEnabled(isChecked));

        mSwAltTab.setChecked(settings.isAltTabEnabled());
        mSwAltTab.setOnCheckedChangeListener((btn, isChecked) -> settings.setAltTabEnabled(isChecked));

        mSwIgnorePlaceholder.setChecked(settings.isIgnorePlaceholdersEnabled());
        mSwIgnorePlaceholder.setOnCheckedChangeListener((btn, isChecked) -> settings.setIgnorePlaceholdersEnabled(isChecked));

        // 5. IME Buttons
        mBtnOpenImeSettings.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Could not open Keyboard Settings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        mBtnSwitchIme.setOnClickListener(v -> {
            try {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showInputMethodPicker();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Could not open Keyboard Picker: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // 6. Stylus & Graphics Tablet Pad
        if (mBtnOpenDigitizer != null) {
            mBtnOpenDigitizer.setOnClickListener(v -> {
                startActivity(new Intent(MainActivity.this, DigitizerActivity.class));
            });
        }

        updateImeStatus();
    }

    private void updateImeStatus() {
        if (mTvImeStatus == null) return;
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            boolean isEnabled = false;
            if (imm != null) {
                List<android.view.inputmethod.InputMethodInfo> list = imm.getEnabledInputMethodList();
                for (android.view.inputmethod.InputMethodInfo info : list) {
                    if (info.getPackageName().equals(getPackageName())) {
                        isEnabled = true;
                        break;
                    }
                }
            }

            String currentIme = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            boolean isCurrent = currentIme != null && currentIme.contains(getPackageName());

            if (isCurrent) {
                mTvImeStatus.setText("● Active: Pure Key Sending Mode (Bypasses Accessibility)");
                mTvImeStatus.setTextColor(getColor(R.color.m3_success));
            } else if (isEnabled) {
                mTvImeStatus.setText("○ Enabled in Settings (Tap Button 2 to Select as Active Keyboard)");
                mTvImeStatus.setTextColor(getColor(R.color.m3_warning));
            } else {
                mTvImeStatus.setText("○ Disabled in Settings (Tap Button 1 to Enable)");
                mTvImeStatus.setTextColor(getColor(R.color.m3_on_surface_variant));
            }
        } catch (Exception e) {
            mTvImeStatus.setText("Status: Ready");
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
            mDeviceAdapter.add("Bluetooth is turned off");
            mDeviceAdapter.notifyDataSetChanged();
            return;
        }

        try {
            Set<BluetoothDevice> paired = adapter.getBondedDevices();
            mPairedDeviceList.clear();
            mDeviceAdapter.clear();

            if (paired != null && !paired.isEmpty()) {
                for (BluetoothDevice dev : paired) {
                    mPairedDeviceList.add(dev);
                    String name = dev.getName();
                    if (name == null || name.isEmpty()) name = "Unknown Device";
                    mDeviceAdapter.add(name + " (" + dev.getAddress() + ")");
                }
            } else {
                mDeviceAdapter.add("No paired Bluetooth devices found");
            }
            mDeviceAdapter.notifyDataSetChanged();
            AppLogger.i("MainActivity", "Found " + mPairedDeviceList.size() + " paired Bluetooth device(s)");
        } catch (SecurityException e) {
            AppLogger.e("MainActivity", "SecurityException reading bonded devices", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mIsExiting) return;
        updatePermissionStatuses();
        updateImeStatus();

        if (hasBluetoothPermissions()) {
            if (!mBound) {
                startBridgeServiceSafe();
            }
            refreshPairedDevices();
        }

        if (mBound && mService != null) {
            mService.setStatusListener(this);
            updateActionButtons(mService.isRunning());
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
        mBtnGrantBt.setBackgroundResource(btOk ? R.drawable.m3_btn_tonal : R.drawable.m3_btn_filled_primary);
        mBtnGrantBt.setTextColor(btOk ? getColor(R.color.m3_on_secondary_container) : 0xFFFFFFFF);

        boolean overlayOk = hasOverlayPermission();
        mTvPermOverlay.setText(overlayOk ? "Permission Granted" : "Draws mouse cursor on screen");
        mBtnGrantOverlay.setEnabled(!overlayOk);
        mBtnGrantOverlay.setText(overlayOk ? "Active" : "Grant");
        mBtnGrantOverlay.setBackgroundResource(overlayOk ? R.drawable.m3_btn_tonal : R.drawable.m3_btn_filled_primary);
        mBtnGrantOverlay.setTextColor(overlayOk ? getColor(R.color.m3_on_secondary_container) : 0xFFFFFFFF);

        boolean accessOk = (InputAccessibilityService.getInstance() != null);
        mTvPermAccess.setText(accessOk ? "Service Active" : "Injects taps, clicks, and keystrokes");
        mBtnGrantAccess.setEnabled(!accessOk);
        mBtnGrantAccess.setText(accessOk ? "Active" : "Grant");
        mBtnGrantAccess.setBackgroundResource(accessOk ? R.drawable.m3_btn_tonal : R.drawable.m3_btn_filled_primary);
        mBtnGrantAccess.setTextColor(accessOk ? getColor(R.color.m3_on_secondary_container) : 0xFFFFFFFF);
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
            Toast.makeText(this, "All runtime permissions granted", Toast.LENGTH_SHORT).show();
            updatePermissionStatuses();
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
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY_PERM);
            } else {
                Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void checkAndPromptAccessibility() {
        if (InputAccessibilityService.getInstance() == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Accessibility Permission")
                    .setMessage("Lapdroid requires Accessibility service to simulate touchpad clicks and keyboard input across apps.\n\nPlease enable 'Lapdroid' under Installed Apps.")
                    .setPositiveButton("Enable in Settings", (d, w) -> openAccessibilitySettings())
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            Toast.makeText(this, "Accessibility Service is already active", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMS) {
            boolean allGranted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "Bluetooth permissions granted", Toast.LENGTH_SHORT).show();
                updatePermissionStatuses();
                startBridgeServiceSafe();
                refreshPairedDevices();
                updateActionButtons(true);
            } else {
                Toast.makeText(this, "Bluetooth permissions are required for wireless connection", Toast.LENGTH_LONG).show();
                showPermissionSettingsDialog();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERM) {
            updatePermissionStatuses();
        }
    }

    private void showPermissionSettingsDialog() {
        if (!isFinishing()) {
            new AlertDialog.Builder(this)
                    .setTitle("Permissions Needed")
                    .setMessage("Lapdroid cannot connect over Bluetooth without Nearby Devices permission. Please allow it in App Settings.")
                    .setPositiveButton("Open Settings", (d, w) -> openAppInfoSettings())
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

        if (isConnected) {
            mTvConnectionDot.setText("● ");
            mTvConnectionDot.setTextColor(getColor(R.color.m3_success));
            mBtnConnectClient.setText("Disconnect");
            mBtnConnectClient.setBackgroundResource(R.drawable.m3_btn_filled_error);
        } else if (status.contains("Listening") || status.contains("Connecting")) {
            mTvConnectionDot.setText("● ");
            mTvConnectionDot.setTextColor(getColor(R.color.m3_primary));
            mBtnConnectClient.setText("Connect to PC");
            mBtnConnectClient.setBackgroundResource(R.drawable.m3_btn_filled_success);
        } else {
            mTvConnectionDot.setText("○ ");
            mTvConnectionDot.setTextColor(getColor(R.color.m3_outline));
            mBtnConnectClient.setText("Connect to PC");
            mBtnConnectClient.setBackgroundResource(R.drawable.m3_btn_filled_success);
        }

        if (status.equalsIgnoreCase("Stopped") || status.equalsIgnoreCase("Service Stopped")) {
            updateActionButtons(false);
        } else if (isConnected || status.contains("Listening") || status.contains("Connected")) {
            updateActionButtons(true);
        }
    }

    @Override
    public void onKeyInputReceived(int androidKeycode, char unicodeChar, byte modifiers) {
        // Only fallback to manual text mutation if Accessibility service is not active
        if (InputAccessibilityService.getInstance() == null && mEtTestInput != null && mEtTestInput.hasFocus()) {
            boolean isCtrl = (modifiers & Protocol.MOD_CTRL) != 0;
            boolean isShift = (modifiers & Protocol.MOD_SHIFT) != 0;
            int start = mEtTestInput.getSelectionStart();
            int end = mEtTestInput.getSelectionEnd();
            android.text.Editable editable = mEtTestInput.getText();
            if (editable == null) return;

            if (isCtrl) {
                if (androidKeycode == 29) { // Ctrl+A (Select All)
                    mTestSelAnchor = 0;
                    mTestSelCaret = editable.length();
                    mEtTestInput.selectAll();
                    return;
                } else if (androidKeycode == 31) { // Ctrl+C (Copy)
                    int min = Math.min(start, end);
                    int max = Math.max(start, end);
                    if (min != max) {
                        String text = editable.subSequence(min, max).toString();
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("text", text));
                    }
                    return;
                } else if (androidKeycode == 52) { // Ctrl+X (Cut)
                    int min = Math.min(start, end);
                    int max = Math.max(start, end);
                    if (min != max) {
                        String text = editable.subSequence(min, max).toString();
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("text", text));
                        editable.delete(min, max);
                    }
                    mTestSelAnchor = -1;
                    mTestSelCaret = -1;
                    return;
                } else if (androidKeycode == 50) { // Ctrl+V (Paste)
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                        CharSequence clip = cm.getPrimaryClip().getItemAt(0).getText();
                        if (clip != null) {
                            editable.replace(Math.min(start, end), Math.max(start, end), clip);
                        }
                    }
                    mTestSelAnchor = -1;
                    mTestSelCaret = -1;
                    return;
                }
                return;
            }

            if (isShift) {
                if (androidKeycode == 21) { // Shift+Left
                    if (mTestSelAnchor < 0) {
                        mTestSelAnchor = Math.max(start, end);
                        mTestSelCaret = Math.min(start, end);
                    }
                    mTestSelCaret = Math.max(0, mTestSelCaret - 1);
                    mEtTestInput.setSelection(Math.min(mTestSelAnchor, mTestSelCaret), Math.max(mTestSelAnchor, mTestSelCaret));
                    return;
                } else if (androidKeycode == 22) { // Shift+Right
                    if (mTestSelAnchor < 0) {
                        mTestSelAnchor = Math.min(start, end);
                        mTestSelCaret = Math.max(start, end);
                    }
                    mTestSelCaret = Math.min(editable.length(), mTestSelCaret + 1);
                    mEtTestInput.setSelection(Math.min(mTestSelAnchor, mTestSelCaret), Math.max(mTestSelAnchor, mTestSelCaret));
                    return;
                }
            } else {
                mTestSelAnchor = -1;
                mTestSelCaret = -1;
            }

            if (androidKeycode == 67) { // Backspace (KEYCODE_DEL)
                if (start != end) {
                    editable.delete(Math.min(start, end), Math.max(start, end));
                } else if (start > 0) {
                    editable.delete(start - 1, start);
                }
            } else if (androidKeycode == 112) { // Forward Delete (KEYCODE_FORWARD_DEL)
                if (start != end) {
                    editable.delete(Math.min(start, end), Math.max(start, end));
                } else if (start < editable.length()) {
                    editable.delete(start, start + 1);
                }
            } else if (androidKeycode == 66) { // Enter (KEYCODE_ENTER)
                if (start != end) {
                    editable.replace(Math.min(start, end), Math.max(start, end), "\n");
                } else {
                    editable.insert(start, "\n");
                }
            } else if (!isCtrl && unicodeChar != 0 && !Character.isISOControl(unicodeChar)) {
                String str = String.valueOf(unicodeChar);
                if (start != end) {
                    editable.replace(Math.min(start, end), Math.max(start, end), str);
                } else {
                    editable.insert(start, str);
                }
            }
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
        try {
            unregisterReceiver(mExitReceiver);
        } catch (Exception ignored) {}
        if (mBound) {
            try {
                unbindService(mConnection);
            } catch (Exception ignored) {}
            mBound = false;
        }
        super.onDestroy();
    }
}
