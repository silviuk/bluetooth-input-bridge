#ifndef UI_WINDOW_H
#define UI_WINDOW_H

#include <winsock2.h>
#include <windows.h>
#include <commctrl.h>
#include <string>
#include <vector>
#include "bluetooth_manager.h"
#include "input_capture.h"

class MainWindow {
public:
    MainWindow(HINSTANCE hInstance, BluetoothManager* btManager, InputCapture* inputCapture);
    ~MainWindow();

    bool Create();
    void Show(int nCmdShow);
    HWND GetHwnd() const { return m_hWnd; }

    static LRESULT CALLBACK WndProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam);

private:
    LRESULT HandleMessage(UINT msg, WPARAM wParam, LPARAM lParam);

    // Initialization & UI creation
    void CreateControls();
    void ApplyModernFonts();
    void RefreshDeviceList();
    void SetupTrayIcon();
    void RemoveTrayIcon();
    void UpdateTrayTooltip();
    void ShowTrayMenu();
    void ShowTrayNotification(const std::wstring& title, const std::wstring& msg);

    // Command handlers
    void OnConnectButtonClicked();
    void OnToggleCaptureClicked();
    void OnModeChanged();
    void OnSendAction(uint8_t action);
    void UpdateStatusUI(ConnectionStatus status, const std::wstring& msg);
    void UpdateCaptureUI(bool isCapturing);

    HINSTANCE m_hInstance;
    HWND m_hWnd;
    BluetoothManager* m_btManager;
    InputCapture* m_inputCapture;

    // Controls
    HWND m_hRadioServer;
    HWND m_hRadioClient;
    HWND m_hComboDevices;
    HWND m_hBtnRefresh;
    HWND m_hBtnConnect;
    HWND m_hBtnToggleCapture;
    HWND m_hSliderSensitivity;
    HWND m_hLabelSensitivity;
    HWND m_hStatusText;
    HWND m_hBtnMinimizeTray;

    // Action buttons
    HWND m_hBtnBack;
    HWND m_hBtnHome;
    HWND m_hBtnRecents;
    HWND m_hBtnNotif;
    HWND m_hBtnVolUp;
    HWND m_hBtnVolDown;
    HWND m_hBtnLock;

    // Fonts & Graphics
    HFONT m_hFontTitle;
    HFONT m_hFontNormal;
    HFONT m_hFontBold;
    HFONT m_hFontStatus;
    HBRUSH m_hBgBrush;
    HBRUSH m_hCardBrush;

    // Tray icon data
    NOTIFYICONDATAW m_nid;
    bool m_trayAdded;
    bool m_reallyClosing;

    std::vector<BluetoothDeviceInfo> m_cachedDevices;
};

#endif // UI_WINDOW_H
