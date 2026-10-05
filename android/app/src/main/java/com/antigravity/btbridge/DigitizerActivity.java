package com.antigravity.btbridge;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class DigitizerActivity extends Activity {

    private DigitizerView mDigitizerView;
    private Button mBtnBack;
    private Button mBtnToggleAspect;
    private TextView mTvConnectionPill;
    private TextView mTvTelemetryHud;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on while drawing
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_digitizer);

        mDigitizerView = findViewById(R.id.digitizer_view);
        mBtnBack = findViewById(R.id.btn_digitizer_back);
        mBtnToggleAspect = findViewById(R.id.btn_toggle_aspect);
        mTvConnectionPill = findViewById(R.id.tv_connection_pill);
        mTvTelemetryHud = findViewById(R.id.tv_telemetry_hud);

        mBtnBack.setOnClickListener(v -> finish());

        mBtnToggleAspect.setOnClickListener(v -> {
            boolean current = mDigitizerView.isLockAspectRatio();
            mDigitizerView.setLockAspectRatio(!current);
            mBtnToggleAspect.setText(!current ? "16:9 PC Fit" : "Full Screen");
        });

        mDigitizerView.setTelemetryListener((pressure, tiltDeg, isBarrel, isEraser, isHover) -> {
            StringBuilder sb = new StringBuilder();
            if (isHover) {
                sb.append("Pen: Hovering (In-Air)");
            } else if (isEraser) {
                sb.append("Pen: Eraser Mode");
            } else {
                sb.append("Pen: Drawing");
            }

            sb.append("  |  Pressure: ").append(pressure).append(" / 1024 (").append((pressure * 100) / 1024).append("%)");
            sb.append("  |  Tilt: ").append(tiltDeg).append("°");

            if (isBarrel) {
                sb.append("  |  [Side Button Pressed]");
            }

            mTvTelemetryHud.setText(sb.toString());
        });

        hideSystemUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        updateConnectionStatus();
    }

    private void updateConnectionStatus() {
        BluetoothBridgeService service = BluetoothBridgeService.getInstance();
        boolean connected = service != null && service.isConnected();
        if (connected) {
            mTvConnectionPill.setText("● Connected to PC");
            mTvConnectionPill.setTextColor(0xFF9ECE6A);
        } else {
            mTvConnectionPill.setText("○ Disconnected (Connect in Lapdroid Main)");
            mTvConnectionPill.setTextColor(0xFFF7768E);
        }
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }
}
