#ifndef UI_WINDOW_H
#define UI_WINDOW_H

#include <winsock2.h>
#include <windows.h>
#include <commctrl.h>
#include <string>
#include <vector>
#include "bluetooth_manager.h"
#include "input_capture.h"

struct ThemeColors {
    bool isDark;
    COLORREF bg;
    COLORREF cardBg;
    COLORREF inputBg;
    COLORREF text;
    COLORREF textMuted;
    COLORREF textTitle;
    COLORREF accent;
    COLORREF statusSuccess;
    COLORREF statusError;
};

class MainWindow {
public:
    MainWindow(HINSTANCE hInstance, BluetoothManager* btManager, InputCapture* inputCapture);
    ~MainWindow();

    bool Create();
    void Show(int nCmdShow);
    HWND GetHwnd() const { return m_hWnd; }

    static LRESULT CALLBACK WndProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam);

private:
    LRESULT HandleMessage(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam);

    // Theme support
    bool DetectWindowsDarkMode();
    void ApplyTheme(bool isDark);
    void ApplyDwmDarkMode(bool isDark);

    // Initialization & UI creation
    void CreateControls();
    void ApplyModernFonts();
    void RefreshDeviceList();
    void SetupTrayIcon();
    void RemoveTrayIcon();
    void UpdateTrayTooltip();
    void ShowTrayMenu();
    void ShowTrayNotification(const std::wstring& title, const std::wstring& msg);

    // Tab & Log management
    void SwitchTab(int tabIndex);
    void OnCopyLogs();
    void OnClearLogs();
    void UpdateLogView();

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

    // Tabs
    HWND m_hTabMain;
    std::vector<HWND> m_controlsTabHwnds;
    std::vector<HWND> m_logsTabHwnds;

    // Controls tab elements
    HWND m_hRadioServer;
    HWND m_hRadioClient;
    HWND m_hLabelRadioServer;
    HWND m_hLabelRadioClient;
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

    // Logs tab elements
    HWND m_hEditLogs;
    HWND m_hBtnCopyLogs;
    HWND m_hBtnClearLogs;

    // Fonts & Graphics
    HFONT m_hFontTitle;
    HFONT m_hFontNormal;
    HFONT m_hFontBold;
    HFONT m_hFontStatus;
    HFONT m_hFontLog;
    HBRUSH m_hBgBrush;
    HBRUSH m_hCardBrush;
    HBRUSH m_hInputBrush;
    HBRUSH m_hLogBgBrush;
    HICON m_hAppIcon;
    HICON m_hTrayIcon;

    // Theme state
    ThemeColors m_theme;
    bool m_isDark;

    // Tray icon data
    NOTIFYICONDATAW m_nid;
    bool m_trayAdded;
    bool m_reallyClosing;
    UINT m_uTaskbarRestartMsg;

    std::vector<BluetoothDeviceInfo> m_cachedDevices;
};

#endif // UI_WINDOW_H
