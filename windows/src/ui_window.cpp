#include "ui_window.h"
#include "protocol.h"
#include "resource.h"
#include "logger.h"
#include <windowsx.h>
#include <commctrl.h>
#include <uxtheme.h>
#include <dwmapi.h>
#include <sstream>
#include <iomanip>

static const wchar_t* WINDOW_CLASS_NAME = L"Lapdroid_Class";

MainWindow::MainWindow(HINSTANCE hInstance, BluetoothManager* btManager, InputCapture* inputCapture)
    : m_hInstance(hInstance)
    , m_hWnd(NULL)
    , m_btManager(btManager)
    , m_inputCapture(inputCapture)
    , m_hTabMain(NULL)
    , m_hRadioServer(NULL)
    , m_hRadioClient(NULL)
    , m_hLabelRadioServer(NULL)
    , m_hLabelRadioClient(NULL)
    , m_hComboDevices(NULL)
    , m_hBtnRefresh(NULL)
    , m_hBtnConnect(NULL)
    , m_hBtnToggleCapture(NULL)
    , m_hSliderSensitivity(NULL)
    , m_hLabelSensitivity(NULL)
    , m_hStatusText(NULL)
    , m_hBtnMinimizeTray(NULL)
    , m_hBtnBack(NULL)
    , m_hBtnHome(NULL)
    , m_hBtnRecents(NULL)
    , m_hBtnNotif(NULL)
    , m_hBtnVolUp(NULL)
    , m_hBtnVolDown(NULL)
    , m_hBtnMute(NULL)
    , m_hBtnPlay(NULL)
    , m_hBtnLock(NULL)
    , m_hBtnScreenshot(NULL)
    , m_hEditLogs(NULL)
    , m_hBtnCopyLogs(NULL)
    , m_hBtnClearLogs(NULL)
    , m_hFontTitle(NULL)
    , m_hFontNormal(NULL)
    , m_hFontBold(NULL)
    , m_hFontStatus(NULL)
    , m_hFontLog(NULL)
    , m_hBgBrush(NULL)
    , m_hCardBrush(NULL)
    , m_hInputBrush(NULL)
    , m_hLogBgBrush(NULL)
    , m_hAppIcon(NULL)
    , m_hTrayIcon(NULL)
    , m_isDark(false)
    , m_trayAdded(false)
    , m_reallyClosing(false)
    , m_uTaskbarRestartMsg(0)
{
    ZeroMemory(&m_nid, sizeof(m_nid));
    ZeroMemory(&m_theme, sizeof(m_theme));
    m_isDark = DetectWindowsDarkMode();
    ApplyTheme(m_isDark);
}

MainWindow::~MainWindow() {
    RemoveTrayIcon();
    if (m_hBgBrush) DeleteObject(m_hBgBrush);
    if (m_hCardBrush) DeleteObject(m_hCardBrush);
    if (m_hInputBrush) DeleteObject(m_hInputBrush);
    if (m_hLogBgBrush) DeleteObject(m_hLogBgBrush);
    if (m_hFontTitle) DeleteObject(m_hFontTitle);
    if (m_hFontNormal) DeleteObject(m_hFontNormal);
    if (m_hFontBold) DeleteObject(m_hFontBold);
    if (m_hFontStatus) DeleteObject(m_hFontStatus);
    if (m_hFontLog) DeleteObject(m_hFontLog);
    if (m_hAppIcon) DestroyIcon(m_hAppIcon);
    if (m_hTrayIcon) DestroyIcon(m_hTrayIcon);
}

bool MainWindow::DetectWindowsDarkMode() {
    DWORD value = 1; // Default: Light Mode (1 = Light, 0 = Dark)
    DWORD size = sizeof(value);
    HKEY hKey;
    if (RegOpenKeyExW(HKEY_CURRENT_USER,
        L"Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
        0, KEY_READ, &hKey) == ERROR_SUCCESS) {
        RegQueryValueExW(hKey, L"AppsUseLightTheme", NULL, NULL, (LPBYTE)&value, &size);
        RegCloseKey(hKey);
    }
    return (value == 0);
}

void MainWindow::ApplyDwmDarkMode(bool isDark) {
    if (!m_hWnd) return;
    BOOL useDark = isDark ? TRUE : FALSE;
    // DWMWA_USE_IMMERSIVE_DARK_MODE (20 on Win10 2004+ and Win11; 19 on older Win10)
    DwmSetWindowAttribute(m_hWnd, 20, &useDark, sizeof(useDark));
    DwmSetWindowAttribute(m_hWnd, 19, &useDark, sizeof(useDark));
}

void MainWindow::ApplyTheme(bool isDark) {
    m_isDark = isDark;
    if (m_isDark) {
        m_theme.isDark = true;
        m_theme.bg = RGB(32, 32, 32);            // #202020 Modern Windows 11 Dark
        m_theme.cardBg = RGB(45, 45, 45);        // #2D2D2D Surface
        m_theme.inputBg = RGB(22, 27, 34);       // Deep Dark Terminal #161B22
        m_theme.text = RGB(242, 242, 242);       // #F2F2F2 Crisp Off-white
        m_theme.textMuted = RGB(160, 160, 160);  // #A0A0A0
        m_theme.textTitle = RGB(255, 255, 255);  // #FFFFFF
        m_theme.accent = RGB(96, 205, 255);      // #60CDFF Fluent Light Blue
        m_theme.statusSuccess = RGB(108, 203, 95); // #6CCB5F
        m_theme.statusError = RGB(255, 120, 130);  // #FF7882
    } else {
        m_theme.isDark = false;
        m_theme.bg = RGB(243, 243, 243);          // #F3F3F3 Modern Windows 11 Light
        m_theme.cardBg = RGB(255, 255, 255);      // #FFFFFF
        m_theme.inputBg = RGB(246, 248, 250);     // Light terminal bg
        m_theme.text = RGB(30, 30, 30);           // #1E1E1E Charcoal dark text
        m_theme.textMuted = RGB(100, 100, 100);   // #646464
        m_theme.textTitle = RGB(0, 0, 0);         // #000000
        m_theme.accent = RGB(0, 103, 192);        // #0067C0 Windows Fluent Blue
        m_theme.statusSuccess = RGB(16, 124, 16); // #107C10 Crisp Green
        m_theme.statusError = RGB(196, 43, 28);   // #C42B1C
    }

    if (m_hBgBrush) DeleteObject(m_hBgBrush);
    if (m_hCardBrush) DeleteObject(m_hCardBrush);
    if (m_hInputBrush) DeleteObject(m_hInputBrush);
    if (m_hLogBgBrush) DeleteObject(m_hLogBgBrush);

    m_hBgBrush = CreateSolidBrush(m_theme.bg);
    m_hCardBrush = CreateSolidBrush(m_theme.cardBg);
    m_hInputBrush = CreateSolidBrush(m_theme.inputBg);
    m_hLogBgBrush = CreateSolidBrush(m_theme.inputBg);

    if (m_hWnd) {
        SetClassLongPtrW(m_hWnd, GCLP_HBRBACKGROUND, (LONG_PTR)m_hBgBrush);
        ApplyDwmDarkMode(m_isDark);

        const wchar_t* themeName = m_isDark ? L"DarkMode_Explorer" : L"Explorer";
        const wchar_t* cfdTheme = m_isDark ? L"DarkMode_CFD" : L"Explorer";

        if (m_hTabMain) SetWindowTheme(m_hTabMain, themeName, NULL);
        if (m_hRadioServer) SetWindowTheme(m_hRadioServer, themeName, NULL);
        if (m_hRadioClient) SetWindowTheme(m_hRadioClient, themeName, NULL);
        if (m_hComboDevices) SetWindowTheme(m_hComboDevices, cfdTheme, NULL);
        if (m_hBtnRefresh) SetWindowTheme(m_hBtnRefresh, themeName, NULL);
        if (m_hBtnConnect) SetWindowTheme(m_hBtnConnect, themeName, NULL);
        if (m_hBtnToggleCapture) SetWindowTheme(m_hBtnToggleCapture, themeName, NULL);
        if (m_hSliderSensitivity) SetWindowTheme(m_hSliderSensitivity, themeName, NULL);
        if (m_hBtnMinimizeTray) SetWindowTheme(m_hBtnMinimizeTray, themeName, NULL);

        if (m_hBtnBack) SetWindowTheme(m_hBtnBack, themeName, NULL);
        if (m_hBtnHome) SetWindowTheme(m_hBtnHome, themeName, NULL);
        if (m_hBtnRecents) SetWindowTheme(m_hBtnRecents, themeName, NULL);
        if (m_hBtnNotif) SetWindowTheme(m_hBtnNotif, themeName, NULL);
        if (m_hBtnVolDown) SetWindowTheme(m_hBtnVolDown, themeName, NULL);
        if (m_hBtnVolUp) SetWindowTheme(m_hBtnVolUp, themeName, NULL);
        if (m_hBtnMute) SetWindowTheme(m_hBtnMute, themeName, NULL);
        if (m_hBtnPlay) SetWindowTheme(m_hBtnPlay, themeName, NULL);
        if (m_hBtnLock) SetWindowTheme(m_hBtnLock, themeName, NULL);
        if (m_hBtnScreenshot) SetWindowTheme(m_hBtnScreenshot, themeName, NULL);

        if (m_hBtnCopyLogs) SetWindowTheme(m_hBtnCopyLogs, themeName, NULL);
        if (m_hBtnClearLogs) SetWindowTheme(m_hBtnClearLogs, themeName, NULL);

        RedrawWindow(m_hWnd, NULL, NULL, RDW_INVALIDATE | RDW_ERASE | RDW_ALLCHILDREN | RDW_UPDATENOW);
    }
}

bool MainWindow::Create() {
    INITCOMMONCONTROLSEX icex;
    icex.dwSize = sizeof(INITCOMMONCONTROLSEX);
    icex.dwICC = ICC_STANDARD_CLASSES | ICC_BAR_CLASSES | ICC_TAB_CLASSES | ICC_WIN95_CLASSES;
    InitCommonControlsEx(&icex);

    // Register taskbar restart notification to recover tray icon if Explorer restarts
    m_uTaskbarRestartMsg = RegisterWindowMessageW(L"TaskbarCreated");

    // Load application and tray icons
    m_hAppIcon = (HICON)LoadImageW(m_hInstance, MAKEINTRESOURCEW(IDI_APP_ICON),
        IMAGE_ICON, GetSystemMetrics(SM_CXICON), GetSystemMetrics(SM_CYICON), LR_DEFAULTCOLOR);
    m_hTrayIcon = (HICON)LoadImageW(m_hInstance, MAKEINTRESOURCEW(IDI_APP_ICON),
        IMAGE_ICON, GetSystemMetrics(SM_CXSMICON), GetSystemMetrics(SM_CYSMICON), LR_DEFAULTCOLOR);

    WNDCLASSEXW wc;
    ZeroMemory(&wc, sizeof(wc));
    wc.cbSize = sizeof(WNDCLASSEXW);
    wc.style = CS_HREDRAW | CS_VREDRAW;
    wc.lpfnWndProc = MainWindow::WndProc;
    wc.hInstance = m_hInstance;
    wc.hIcon = m_hAppIcon ? m_hAppIcon : LoadIcon(NULL, IDI_APPLICATION);
    wc.hIconSm = m_hTrayIcon ? m_hTrayIcon : LoadIcon(NULL, IDI_APPLICATION);
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    wc.hbrBackground = m_hBgBrush;
    wc.lpszClassName = WINDOW_CLASS_NAME;

    RegisterClassExW(&wc);

    int winW = 560;
    int winH = 710;

    m_hWnd = CreateWindowExW(
        WS_EX_APPWINDOW,
        WINDOW_CLASS_NAME,
        L"Lapdroid - Bluetooth Input Bridge",
        WS_OVERLAPPEDWINDOW & ~WS_MAXIMIZEBOX & ~WS_THICKFRAME,
        CW_USEDEFAULT, CW_USEDEFAULT, winW, winH,
        NULL, NULL, m_hInstance, this
    );

    if (!m_hWnd) return false;

    // Set large and small window icons
    if (m_hAppIcon) SendMessageW(m_hWnd, WM_SETICON, ICON_BIG, (LPARAM)m_hAppIcon);
    if (m_hTrayIcon) SendMessageW(m_hWnd, WM_SETICON, ICON_SMALL, (LPARAM)m_hTrayIcon);

    // Register logger target window
    Logger::Instance().SetHwnd(m_hWnd);
    LOG_INFO(L"App", L"Lapdroid Windows v0.3.0 initialized");

    ApplyModernFonts();
    CreateControls();
    ApplyTheme(m_isDark);
    SetupTrayIcon();
    RefreshDeviceList();

    // Setup callbacks
    m_btManager->SetStatusCallback([this](ConnectionStatus status, const std::wstring& msg) {
        PostMessageW(m_hWnd, WM_APP + 20, (WPARAM)status, 0);
    });

    m_inputCapture->SetToggleCallback([this](bool isCapturing) {
        PostMessageW(m_hWnd, WM_APP + 21, (WPARAM)isCapturing, 0);
    });

    return true;
}

void MainWindow::Show(int nCmdShow) {
    ShowWindow(m_hWnd, nCmdShow);
    UpdateWindow(m_hWnd);
}

void MainWindow::ApplyModernFonts() {
    m_hFontTitle = CreateFontW(-20, 0, 0, 0, FW_BOLD, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
        DEFAULT_PITCH | FF_SWISS, L"Segoe UI");

    m_hFontBold = CreateFontW(-14, 0, 0, 0, FW_BOLD, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
        DEFAULT_PITCH | FF_SWISS, L"Segoe UI");

    m_hFontNormal = CreateFontW(-13, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
        DEFAULT_PITCH | FF_SWISS, L"Segoe UI");

    m_hFontStatus = CreateFontW(-14, 0, 0, 0, FW_SEMIBOLD, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
        DEFAULT_PITCH | FF_SWISS, L"Segoe UI");

    m_hFontLog = CreateFontW(-12, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
        FIXED_PITCH | FF_MODERN, L"Consolas");
}

void MainWindow::CreateControls() {
    int padX = 25;
    int curY = 16;

    // Title label
    HWND hTitle = CreateWindowExW(0, L"STATIC", L"Lapdroid",
        WS_CHILD | WS_VISIBLE | SS_LEFT,
        padX, curY, 500, 26, m_hWnd, NULL, m_hInstance, NULL);
    SendMessageW(hTitle, WM_SETFONT, (WPARAM)m_hFontTitle, TRUE);
    curY += 28;

    // Subtitle label
    HWND hSub = CreateWindowExW(0, L"STATIC", L"Control your Android phone with laptop keyboard & touchpad over Bluetooth",
        WS_CHILD | WS_VISIBLE | SS_LEFT,
        padX, curY, 500, 18, m_hWnd, NULL, m_hInstance, NULL);
    SendMessageW(hSub, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    curY += 26;

    // Top Tab Control
    m_hTabMain = CreateWindowExW(0, WC_TABCONTROLW, L"",
        WS_CHILD | WS_VISIBLE | WS_CLIPSIBLINGS | TCS_TABS,
        padX, curY, 495, 30, m_hWnd, (HMENU)IDC_TAB_MAIN, m_hInstance, NULL);
    SendMessageW(m_hTabMain, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);

    TCITEMW tie;
    tie.mask = TCIF_TEXT;
    tie.pszText = (LPWSTR)L"Controls";
    TabCtrl_InsertItem(m_hTabMain, 0, &tie);
    tie.pszText = (LPWSTR)L"Activity & Logs";
    TabCtrl_InsertItem(m_hTabMain, 1, &tie);

    curY += 38;
    int contentStartY = curY;

    // ================= CONTROLS TAB CONTENT =================
    // Card 1: Connection Mode
    HWND hModeLabel = CreateWindowExW(0, L"STATIC", L"1. Connection Mode & Pairing",
        WS_CHILD | WS_VISIBLE | SS_LEFT,
        padX, curY, 495, 20, m_hWnd, NULL, m_hInstance, NULL);
    SendMessageW(hModeLabel, WM_SETFONT, (WPARAM)m_hFontBold, TRUE);
    m_controlsTabHwnds.push_back(hModeLabel);
    curY += 24;

    m_hRadioServer = CreateWindowExW(0, L"BUTTON", L"",
        WS_CHILD | WS_VISIBLE | BS_AUTORADIOBUTTON | WS_GROUP,
        padX + 10, curY + 2, 20, 20, m_hWnd, (HMENU)IDC_RADIO_SERVER, m_hInstance, NULL);
    m_hLabelRadioServer = CreateWindowExW(0, L"STATIC", L"Server Mode (Wait for Android phone to connect)",
        WS_CHILD | WS_VISIBLE | SS_NOTIFY,
        padX + 34, curY + 2, 450, 20, m_hWnd, (HMENU)IDC_LABEL_RADIO_SERVER, m_hInstance, NULL);
    SendMessageW(m_hLabelRadioServer, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    m_controlsTabHwnds.push_back(m_hRadioServer);
    m_controlsTabHwnds.push_back(m_hLabelRadioServer);
    curY += 26;

    m_hRadioClient = CreateWindowExW(0, L"BUTTON", L"",
        WS_CHILD | WS_VISIBLE | BS_AUTORADIOBUTTON,
        padX + 10, curY + 2, 20, 20, m_hWnd, (HMENU)IDC_RADIO_CLIENT, m_hInstance, NULL);
    m_hLabelRadioClient = CreateWindowExW(0, L"STATIC", L"Client Mode (Connect to paired Android phone)",
        WS_CHILD | WS_VISIBLE | SS_NOTIFY,
        padX + 34, curY + 2, 450, 20, m_hWnd, (HMENU)IDC_LABEL_RADIO_CLIENT, m_hInstance, NULL);
    SendMessageW(m_hLabelRadioClient, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    Button_SetCheck(m_hRadioServer, BST_CHECKED);
    m_controlsTabHwnds.push_back(m_hRadioClient);
    m_controlsTabHwnds.push_back(m_hLabelRadioClient);
    curY += 30;

    // Device dropdown and refresh button
    m_hComboDevices = CreateWindowExW(0, L"COMBOBOX", L"",
        WS_CHILD | WS_VISIBLE | CBS_DROPDOWNLIST | WS_VSCROLL | WS_TABSTOP,
        padX + 10, curY, 360, 200, m_hWnd, (HMENU)IDC_COMBO_DEVICES, m_hInstance, NULL);
    SendMessageW(m_hComboDevices, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);

    m_hBtnRefresh = CreateWindowExW(0, L"BUTTON", L"Refresh Devices",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        padX + 380, curY - 1, 105, 28, m_hWnd, (HMENU)IDC_BTN_REFRESH, m_hInstance, NULL);
    SendMessageW(m_hBtnRefresh, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    m_controlsTabHwnds.push_back(m_hComboDevices);
    m_controlsTabHwnds.push_back(m_hBtnRefresh);
    curY += 36;

    // Connect / Disconnect button
    m_hBtnConnect = CreateWindowExW(0, L"BUTTON", L"Start Bluetooth Server",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        padX + 10, curY, 475, 36, m_hWnd, (HMENU)IDC_BTN_CONNECT, m_hInstance, NULL);
    SendMessageW(m_hBtnConnect, WM_SETFONT, (WPARAM)m_hFontBold, TRUE);
    m_controlsTabHwnds.push_back(m_hBtnConnect);
    curY += 44;

    // Status label
    m_hStatusText = CreateWindowExW(0, L"STATIC", L"Status: Ready (Disconnected)",
        WS_CHILD | WS_VISIBLE | SS_LEFT,
        padX + 10, curY, 475, 22, m_hWnd, (HMENU)IDC_STATUS_TEXT, m_hInstance, NULL);
    SendMessageW(m_hStatusText, WM_SETFONT, (WPARAM)m_hFontStatus, TRUE);
    m_controlsTabHwnds.push_back(m_hStatusText);
    curY += 30;

    // Card 2: Input Redirection
    HWND hInputLabel = CreateWindowExW(0, L"STATIC", L"2. Input Redirection",
        WS_CHILD | WS_VISIBLE | SS_LEFT,
        padX, curY, 495, 20, m_hWnd, NULL, m_hInstance, NULL);
    SendMessageW(hInputLabel, WM_SETFONT, (WPARAM)m_hFontBold, TRUE);
    m_controlsTabHwnds.push_back(hInputLabel);
    curY += 24;

    m_hBtnToggleCapture = CreateWindowExW(0, L"BUTTON", L"Capture Input for Android Phone (Hotkey: F12)",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        padX + 10, curY, 475, 40, m_hWnd, (HMENU)IDC_BTN_TOGGLE_CAPTURE, m_hInstance, NULL);
    SendMessageW(m_hBtnToggleCapture, WM_SETFONT, (WPARAM)m_hFontBold, TRUE);
    m_controlsTabHwnds.push_back(m_hBtnToggleCapture);
    curY += 48;

    // Sensitivity slider
    m_hLabelSensitivity = CreateWindowExW(0, L"STATIC", L"Touchpad Sensitivity: 1.0x",
        WS_CHILD | WS_VISIBLE | SS_LEFT,
        padX + 10, curY, 200, 20, m_hWnd, (HMENU)IDC_LABEL_SENSITIVITY, m_hInstance, NULL);
    SendMessageW(m_hLabelSensitivity, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);

    m_hSliderSensitivity = CreateWindowExW(0, TRACKBAR_CLASSW, L"",
        WS_CHILD | WS_VISIBLE | TBS_AUTOTICKS | TBS_ENABLESELRANGE,
        padX + 220, curY - 4, 265, 30, m_hWnd, (HMENU)IDC_SLIDER_SENSITIVITY, m_hInstance, NULL);
    SendMessageW(m_hSliderSensitivity, TBM_SETRANGE, TRUE, MAKELPARAM(5, 30));
    SendMessageW(m_hSliderSensitivity, TBM_SETPOS, TRUE, 10);
    m_controlsTabHwnds.push_back(m_hLabelSensitivity);
    m_controlsTabHwnds.push_back(m_hSliderSensitivity);
    curY += 34;

    // Hint label
    HWND hHint = CreateWindowExW(0, L"STATIC",
        L"Tip: Press F12 to capture/release. Tricky keys forwarded: Esc (Back), Win (Home), Alt+Tab (Recents), PrintScreen (Screenshot), Ctrl+C/V/A/X/Z (Clipboard), and Volume/Media keys.",
        WS_CHILD | WS_VISIBLE | SS_LEFT,
        padX + 10, curY, 475, 34, m_hWnd, NULL, m_hInstance, NULL);
    SendMessageW(hHint, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    m_controlsTabHwnds.push_back(hHint);
    curY += 40;

    // Card 3: Phone Remote Quick Actions
    HWND hActionLabel = CreateWindowExW(0, L"STATIC", L"3. Quick Phone Actions",
        WS_CHILD | WS_VISIBLE | SS_LEFT,
        padX, curY, 495, 20, m_hWnd, NULL, m_hInstance, NULL);
    SendMessageW(hActionLabel, WM_SETFONT, (WPARAM)m_hFontBold, TRUE);
    m_controlsTabHwnds.push_back(hActionLabel);
    curY += 24;

    int btnW = 90;
    int gap = 6;
    int row1X = padX + 10;

    // Row 1: System Navigation & Lock
    m_hBtnBack = CreateWindowExW(0, L"BUTTON", L"Back", WS_CHILD | WS_VISIBLE, row1X, curY, btnW, 28, m_hWnd, (HMENU)IDC_BTN_ACT_BACK, m_hInstance, NULL);
    row1X += btnW + gap;
    m_hBtnHome = CreateWindowExW(0, L"BUTTON", L"Home", WS_CHILD | WS_VISIBLE, row1X, curY, btnW, 28, m_hWnd, (HMENU)IDC_BTN_ACT_HOME, m_hInstance, NULL);
    row1X += btnW + gap;
    m_hBtnRecents = CreateWindowExW(0, L"BUTTON", L"Recents", WS_CHILD | WS_VISIBLE, row1X, curY, btnW, 28, m_hWnd, (HMENU)IDC_BTN_ACT_RECENTS, m_hInstance, NULL);
    row1X += btnW + gap;
    m_hBtnNotif = CreateWindowExW(0, L"BUTTON", L"Notif", WS_CHILD | WS_VISIBLE, row1X, curY, btnW, 28, m_hWnd, (HMENU)IDC_BTN_ACT_NOTIF, m_hInstance, NULL);
    row1X += btnW + gap;
    m_hBtnLock = CreateWindowExW(0, L"BUTTON", L"Lock", WS_CHILD | WS_VISIBLE, row1X, curY, btnW, 28, m_hWnd, (HMENU)IDC_BTN_ACT_LOCK, m_hInstance, NULL);
    curY += 32;

    int row2X = padX + 10;
    // Row 2: Media, Volume & Screenshot
    m_hBtnVolDown = CreateWindowExW(0, L"BUTTON", L"Vol -", WS_CHILD | WS_VISIBLE, row2X, curY, btnW, 28, m_hWnd, (HMENU)IDC_BTN_ACT_VOLDOWN, m_hInstance, NULL);
    row2X += btnW + gap;
    m_hBtnVolUp = CreateWindowExW(0, L"BUTTON", L"Vol +", WS_CHILD | WS_VISIBLE, row2X, curY, btnW, 28, m_hWnd, (HMENU)IDC_BTN_ACT_VOLUP, m_hInstance, NULL);
    row2X += btnW + gap;
    m_hBtnMute = CreateWindowExW(0, L"BUTTON", L"Mute", WS_CHILD | WS_VISIBLE, row2X, curY, btnW, 28, m_hWnd, (HMENU)IDC_BTN_ACT_MUTE, m_hInstance, NULL);
    row2X += btnW + gap;
    m_hBtnPlay = CreateWindowExW(0, L"BUTTON", L"Play/Pause", WS_CHILD | WS_VISIBLE, row2X, curY, btnW, 28, m_hWnd, (HMENU)IDC_BTN_ACT_PLAY, m_hInstance, NULL);
    row2X += btnW + gap;
    m_hBtnScreenshot = CreateWindowExW(0, L"BUTTON", L"Screenshot", WS_CHILD | WS_VISIBLE, row2X, curY, btnW, 28, m_hWnd, (HMENU)IDC_BTN_ACT_SCREENSHOT, m_hInstance, NULL);
    curY += 36;

    SendMessageW(m_hBtnBack, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    SendMessageW(m_hBtnHome, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    SendMessageW(m_hBtnRecents, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    SendMessageW(m_hBtnNotif, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    SendMessageW(m_hBtnLock, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    SendMessageW(m_hBtnVolDown, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    SendMessageW(m_hBtnVolUp, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    SendMessageW(m_hBtnMute, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    SendMessageW(m_hBtnPlay, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    SendMessageW(m_hBtnScreenshot, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);

    m_controlsTabHwnds.push_back(m_hBtnBack);
    m_controlsTabHwnds.push_back(m_hBtnHome);
    m_controlsTabHwnds.push_back(m_hBtnRecents);
    m_controlsTabHwnds.push_back(m_hBtnNotif);
    m_controlsTabHwnds.push_back(m_hBtnLock);
    m_controlsTabHwnds.push_back(m_hBtnVolDown);
    m_controlsTabHwnds.push_back(m_hBtnVolUp);
    m_controlsTabHwnds.push_back(m_hBtnMute);
    m_controlsTabHwnds.push_back(m_hBtnPlay);
    m_controlsTabHwnds.push_back(m_hBtnScreenshot);

    // Minimize to System Tray button
    m_hBtnMinimizeTray = CreateWindowExW(0, L"BUTTON", L"Hide to System Tray (Keep Running)",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        padX + 10, curY, 475, 32, m_hWnd, (HMENU)IDC_BTN_MINIMIZE_TRAY, m_hInstance, NULL);
    SendMessageW(m_hBtnMinimizeTray, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    m_controlsTabHwnds.push_back(m_hBtnMinimizeTray);

    // ================= LOGS TAB CONTENT =================
    int logY = contentStartY;
    m_hEditLogs = CreateWindowExW(
        WS_EX_CLIENTEDGE, L"EDIT", L"",
        WS_CHILD | ES_MULTILINE | ES_AUTOVSCROLL | ES_READONLY | WS_VSCROLL | WS_HSCROLL,
        padX, logY, 495, 485, m_hWnd, (HMENU)IDC_EDIT_LOGS, m_hInstance, NULL
    );
    SendMessageW(m_hEditLogs, WM_SETFONT, (WPARAM)m_hFontLog, TRUE);
    m_logsTabHwnds.push_back(m_hEditLogs);
    logY += 495;

    m_hBtnCopyLogs = CreateWindowExW(0, L"BUTTON", L"Copy Logs to Clipboard",
        WS_CHILD | BS_PUSHBUTTON,
        padX, logY, 240, 32, m_hWnd, (HMENU)IDC_BTN_COPY_LOGS, m_hInstance, NULL);
    SendMessageW(m_hBtnCopyLogs, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    m_logsTabHwnds.push_back(m_hBtnCopyLogs);

    m_hBtnClearLogs = CreateWindowExW(0, L"BUTTON", L"Clear Logs",
        WS_CHILD | BS_PUSHBUTTON,
        padX + 255, logY, 240, 32, m_hWnd, (HMENU)IDC_BTN_CLEAR_LOGS, m_hInstance, NULL);
    SendMessageW(m_hBtnClearLogs, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    m_logsTabHwnds.push_back(m_hBtnClearLogs);

    // Initial state: show Controls tab, hide Logs tab
    SwitchTab(0);
    OnModeChanged();
}

void MainWindow::SwitchTab(int tabIndex) {
    bool showControls = (tabIndex == 0);
    for (HWND h : m_controlsTabHwnds) {
        if (h && IsWindow(h)) ShowWindow(h, showControls ? SW_SHOW : SW_HIDE);
    }
    for (HWND h : m_logsTabHwnds) {
        if (h && IsWindow(h)) ShowWindow(h, showControls ? SW_HIDE : SW_SHOW);
    }
    if (!showControls) {
        UpdateLogView();
    }
}

void MainWindow::OnCopyLogs() {
    std::wstring logs = Logger::Instance().GetAllLogs();
    if (logs.empty()) {
        MessageBoxW(m_hWnd, L"Log buffer is currently empty.", L"Lapdroid Logs", MB_OK | MB_ICONINFORMATION);
        return;
    }
    if (OpenClipboard(m_hWnd)) {
        EmptyClipboard();
        size_t bytes = (logs.size() + 1) * sizeof(wchar_t);
        HGLOBAL hMem = GlobalAlloc(GMEM_MOVEABLE, bytes);
        if (hMem) {
            memcpy(GlobalLock(hMem), logs.c_str(), bytes);
            GlobalUnlock(hMem);
            SetClipboardData(CF_UNICODETEXT, hMem);
        }
        CloseClipboard();
        MessageBoxW(m_hWnd, L"Logs copied to clipboard!", L"Lapdroid", MB_OK | MB_ICONINFORMATION);
    }
}

void MainWindow::OnClearLogs() {
    Logger::Instance().Clear();
    if (m_hEditLogs && IsWindow(m_hEditLogs)) {
        SetWindowTextW(m_hEditLogs, L"");
    }
}

void MainWindow::UpdateLogView() {
    if (m_hEditLogs && IsWindow(m_hEditLogs)) {
        std::wstring logs = Logger::Instance().GetAllLogs();
        SetWindowTextW(m_hEditLogs, logs.c_str());
        SendMessageW(m_hEditLogs, EM_SETSEL, (WPARAM)-1, (LPARAM)-1);
        SendMessageW(m_hEditLogs, EM_SCROLLCARET, 0, 0);
    }
}

void MainWindow::RefreshDeviceList() {
    ComboBox_ResetContent(m_hComboDevices);
    m_cachedDevices = m_btManager->GetPairedDevices();

    int defaultIndex = -1;
    for (size_t i = 0; i < m_cachedDevices.size(); ++i) {
        std::wstring displayName = m_cachedDevices[i].name;
        if (displayName.empty()) {
            displayName = L"Unknown Device (" + m_cachedDevices[i].addressStr + L")";
        } else {
            displayName += L" [" + m_cachedDevices[i].addressStr + L"]";
        }

        ComboBox_AddString(m_hComboDevices, displayName.c_str());

        // Prefer paired device that looks like an Android phone / mobile device
        std::wstring lowerName = displayName;
        for (auto& c : lowerName) c = towlower(c);
        if (lowerName.find(L"phone") != std::wstring::npos ||
            lowerName.find(L"android") != std::wstring::npos ||
            lowerName.find(L"pixel") != std::wstring::npos ||
            lowerName.find(L"galaxy") != std::wstring::npos ||
            lowerName.find(L"samsung") != std::wstring::npos ||
            lowerName.find(L"oneplus") != std::wstring::npos ||
            lowerName.find(L"xiaomi") != std::wstring::npos) {
            if (defaultIndex < 0) defaultIndex = (int)i;
        }
    }

    if (m_cachedDevices.empty()) {
        ComboBox_AddString(m_hComboDevices, L"No paired devices found (pair Android phone in Windows Settings)");
        ComboBox_SetCurSel(m_hComboDevices, 0);
    } else {
        ComboBox_SetCurSel(m_hComboDevices, (defaultIndex >= 0) ? defaultIndex : 0);
    }
}

void MainWindow::SetupTrayIcon() {
    if (!m_hWnd) return;

    Shell_NotifyIconW(NIM_DELETE, &m_nid);

    ZeroMemory(&m_nid, sizeof(m_nid));
    m_nid.cbSize = sizeof(NOTIFYICONDATAW);
    m_nid.hWnd = m_hWnd;
    m_nid.uID = 1;
    m_nid.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP;
    m_nid.uCallbackMessage = WM_TRAYNOTIFY;

    if (!m_hTrayIcon) {
        m_hTrayIcon = (HICON)LoadImageW(m_hInstance, MAKEINTRESOURCEW(IDI_APP_ICON),
            IMAGE_ICON, GetSystemMetrics(SM_CXSMICON), GetSystemMetrics(SM_CYSMICON), LR_DEFAULTCOLOR);
    }
    m_nid.hIcon = m_hTrayIcon ? m_hTrayIcon : LoadIcon(NULL, IDI_APPLICATION);
    wcsncpy(m_nid.szTip, L"Lapdroid - Bluetooth Input Bridge", sizeof(m_nid.szTip) / sizeof(wchar_t) - 1);

    if (Shell_NotifyIconW(NIM_ADD, &m_nid)) {
        m_trayAdded = true;
        m_nid.uVersion = NOTIFYICON_VERSION_4;
        Shell_NotifyIconW(NIM_SETVERSION, &m_nid);
    }
}

void MainWindow::RemoveTrayIcon() {
    if (m_trayAdded) {
        Shell_NotifyIconW(NIM_DELETE, &m_nid);
        m_trayAdded = false;
    }
}

void MainWindow::UpdateTrayTooltip() {
    if (!m_trayAdded) return;

    std::wstring tip = L"Lapdroid - ";
    if (m_btManager->IsConnected()) {
        tip += L"Connected (" + m_btManager->GetConnectedDeviceName() + L")";
    } else if (m_btManager->GetStatus() == ConnectionStatus::Listening) {
        tip += L"Listening for Android Phone...";
    } else if (m_btManager->GetStatus() == ConnectionStatus::Connecting) {
        tip += L"Connecting...";
    } else {
        tip += L"Ready";
    }

    wcsncpy(m_nid.szTip, tip.c_str(), sizeof(m_nid.szTip) / sizeof(wchar_t) - 1);
    Shell_NotifyIconW(NIM_MODIFY, &m_nid);
}

void MainWindow::ShowTrayNotification(const std::wstring& title, const std::wstring& msg) {
    if (!m_trayAdded) return;

    m_nid.uFlags |= NIF_INFO;
    wcsncpy(m_nid.szInfoTitle, title.c_str(), sizeof(m_nid.szInfoTitle) / sizeof(wchar_t) - 1);
    wcsncpy(m_nid.szInfo, msg.c_str(), sizeof(m_nid.szInfo) / sizeof(wchar_t) - 1);
    m_nid.dwInfoFlags = NIIF_INFO;

    Shell_NotifyIconW(NIM_MODIFY, &m_nid);
    m_nid.uFlags &= ~NIF_INFO;
}

void MainWindow::ShowTrayMenu() {
    HMENU hMenu = CreatePopupMenu();
    if (!hMenu) return;

    InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_TRAY_OPEN, L"Open Lapdroid (Show UI)");
    InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_SEPARATOR, 0, NULL);

    if (m_btManager->IsConnected()) {
        InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_TRAY_DISCONNECT, L"Disconnect Bluetooth");
    } else if (m_btManager->GetStatus() == ConnectionStatus::Listening) {
        InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_TRAY_DISCONNECT, L"Stop Bluetooth Server");
    } else {
        InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_TRAY_CONNECT, L"Start Bluetooth Server");
        InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_TRAY_CONNECT_DEVICE, L"Connect to Selected Device");
    }

    InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_TRAY_RESTART, L"Restart Connection / Server");
    InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_SEPARATOR, 0, NULL);

    UINT captureFlags = MF_BYPOSITION | MF_STRING;
    if (m_inputCapture->IsCapturing()) {
        captureFlags |= MF_CHECKED;
    }
    InsertMenuW(hMenu, -1, captureFlags, IDM_TRAY_TOGGLE_CAPTURE, L"Toggle Input Capture (F12)");

    InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_SEPARATOR, 0, NULL);
    InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_TRAY_HOME, L"Phone: Home");
    InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_TRAY_BACK, L"Phone: Back");
    InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_TRAY_RECENTS, L"Phone: Recent Apps");

    InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_SEPARATOR, 0, NULL);
    InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_TRAY_EXIT, L"Exit Lapdroid");

    POINT pt;
    GetCursorPos(&pt);

    SetForegroundWindow(m_hWnd);
    TrackPopupMenu(hMenu, TPM_RIGHTBUTTON | TPM_BOTTOMALIGN, pt.x, pt.y, 0, m_hWnd, NULL);
    PostMessageW(m_hWnd, WM_NULL, 0, 0);

    DestroyMenu(hMenu);
}

void MainWindow::OnModeChanged() {
    bool isServer = (Button_GetCheck(m_hRadioServer) == BST_CHECKED);
    LOG_INFO(L"UI", isServer ? L"Mode changed to Server Mode (listening)" : L"Mode changed to Client Mode (connecting)");

    EnableWindow(m_hComboDevices, !isServer);
    EnableWindow(m_hBtnRefresh, !isServer);

    if (m_btManager->IsConnected()) {
        Button_SetText(m_hBtnConnect, L"Disconnect");
    } else if (m_btManager->GetStatus() == ConnectionStatus::Listening) {
        Button_SetText(m_hBtnConnect, L"Stop Server");
    } else if (m_btManager->GetStatus() == ConnectionStatus::Connecting) {
        Button_SetText(m_hBtnConnect, L"Cancel Connection");
    } else {
        Button_SetText(m_hBtnConnect, isServer ? L"Start Bluetooth Server" : L"Connect to Selected Device");
    }
}

void MainWindow::OnConnectButtonClicked() {
    if (m_btManager->IsConnected() ||
        m_btManager->GetStatus() == ConnectionStatus::Listening ||
        m_btManager->GetStatus() == ConnectionStatus::Connecting) {
        LOG_INFO(L"UI", L"User requested Disconnect / Cancel");
        m_btManager->Disconnect();
        OnModeChanged();
        return;
    }

    bool isServer = (Button_GetCheck(m_hRadioServer) == BST_CHECKED);
    if (isServer) {
        LOG_INFO(L"UI", L"User requested Start Server");
        m_btManager->StartServer();
        Button_SetText(m_hBtnConnect, L"Stop Server");
    } else {
        int sel = ComboBox_GetCurSel(m_hComboDevices);
        if (sel >= 0 && sel < (int)m_cachedDevices.size()) {
            BTH_ADDR addr = m_cachedDevices[sel].address;
            LOG_INFO(L"UI", L"User requested Connect to " + m_cachedDevices[sel].name + L" [" + m_cachedDevices[sel].addressStr + L"]");
            m_btManager->ConnectToDevice(addr);
            Button_SetText(m_hBtnConnect, L"Cancel / Disconnect");
        } else {
            MessageBoxW(m_hWnd, L"Please select a paired Bluetooth device first.", L"No Device Selected", MB_OK | MB_ICONWARNING);
        }
    }
}

void MainWindow::OnToggleCaptureClicked() {
    m_inputCapture->ToggleCapture();
}

void MainWindow::OnSendAction(uint8_t action) {
    if (!m_btManager->IsConnected()) {
        MessageBoxW(m_hWnd, L"Bluetooth is not connected to Android phone.", L"Not Connected", MB_OK | MB_ICONINFORMATION);
        return;
    }

    SystemActionPayload payload;
    payload.action = action;
    LOG_INFO(L"Action", L"Dispatched remote system action code " + std::to_wstring(action));
    m_btManager->SendPacket(MSG_SYSTEM_ACTION, &payload, sizeof(payload));
}

void MainWindow::UpdateStatusUI(ConnectionStatus status, const std::wstring& msg) {
    std::wstring displayText = L"Status: " + msg;
    Static_SetText(m_hStatusText, displayText.c_str());

    if (status == ConnectionStatus::Connected) {
        Button_SetText(m_hBtnConnect, L"Disconnect");
        ShowTrayNotification(L"Connected to Android Phone", L"Bluetooth input link established. Press F12 to capture input.");
    } else if (status == ConnectionStatus::Listening) {
        Button_SetText(m_hBtnConnect, L"Stop Server");
    } else if (status == ConnectionStatus::Connecting) {
        Button_SetText(m_hBtnConnect, L"Cancel Connection");
    } else {
        OnModeChanged();
    }
    UpdateTrayTooltip();
}

void MainWindow::UpdateCaptureUI(bool isCapturing) {
    if (isCapturing) {
        Button_SetText(m_hBtnToggleCapture, L"Capturing Active! Press F12 to Release Control");
        ShowTrayNotification(L"Input Capture Active", L"Controlling Android phone. Press F12 to return cursor to Windows.");
    } else {
        Button_SetText(m_hBtnToggleCapture, L"Capture Input for Android Phone (Hotkey: F12)");
    }
}

LRESULT CALLBACK MainWindow::WndProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    MainWindow* pThis = nullptr;
    if (msg == WM_NCCREATE) {
        CREATESTRUCT* pCreate = (CREATESTRUCT*)lParam;
        pThis = (MainWindow*)pCreate->lpCreateParams;
        SetWindowLongPtr(hWnd, GWLP_USERDATA, (LONG_PTR)pThis);
        pThis->m_hWnd = hWnd;
    } else {
        pThis = (MainWindow*)GetWindowLongPtr(hWnd, GWLP_USERDATA);
    }

    if (pThis) {
        return pThis->HandleMessage(hWnd, msg, wParam, lParam);
    }
    return DefWindowProcW(hWnd, msg, wParam, lParam);
}

LRESULT MainWindow::HandleMessage(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    if (msg == m_uTaskbarRestartMsg && m_uTaskbarRestartMsg != 0) {
        SetupTrayIcon();
        UpdateTrayTooltip();
        return 0;
    }

    switch (msg) {
        case WM_SETTINGCHANGE: {
            if (lParam && wcscmp((LPCWSTR)lParam, L"ImmersiveColorSet") == 0) {
                bool newDark = DetectWindowsDarkMode();
                if (newDark != m_isDark) {
                    ApplyTheme(newDark);
                }
            }
            break;
        }

        case WM_NOTIFY: {
            NMHDR* pnm = (NMHDR*)lParam;
            if (pnm && pnm->idFrom == IDC_TAB_MAIN && pnm->code == TCN_SELCHANGE) {
                int sel = TabCtrl_GetCurSel(m_hTabMain);
                SwitchTab(sel);
                return 0;
            }
            break;
        }

        case WM_COMMAND: {
            int wmId = LOWORD(wParam);
            int wmEvent = HIWORD(wParam);

            switch (wmId) {
                case IDC_RADIO_SERVER:
                case IDC_RADIO_CLIENT:
                    if (wmEvent == BN_CLICKED) {
                        OnModeChanged();
                    }
                    break;

                case IDC_LABEL_RADIO_SERVER:
                    if (wmEvent == STN_CLICKED) {
                        Button_SetCheck(m_hRadioServer, BST_CHECKED);
                        Button_SetCheck(m_hRadioClient, BST_UNCHECKED);
                        OnModeChanged();
                    }
                    break;

                case IDC_LABEL_RADIO_CLIENT:
                    if (wmEvent == STN_CLICKED) {
                        Button_SetCheck(m_hRadioClient, BST_CHECKED);
                        Button_SetCheck(m_hRadioServer, BST_UNCHECKED);
                        OnModeChanged();
                    }
                    break;

                case IDC_BTN_REFRESH:
                    RefreshDeviceList();
                    break;

                case IDC_BTN_CONNECT:
                    OnConnectButtonClicked();
                    break;

                case IDC_BTN_TOGGLE_CAPTURE:
                    OnToggleCaptureClicked();
                    break;

                case IDC_BTN_COPY_LOGS:
                    OnCopyLogs();
                    break;

                case IDC_BTN_CLEAR_LOGS:
                    OnClearLogs();
                    break;

                case IDC_BTN_MINIMIZE_TRAY:
                    ShowWindow(m_hWnd, SW_HIDE);
                    ShowTrayNotification(L"Lapdroid", L"Running in system tray. Right-click icon for quick actions.");
                    break;

                case IDC_BTN_ACT_BACK:     OnSendAction(ACT_BACK); break;
                case IDC_BTN_ACT_HOME:     OnSendAction(ACT_HOME); break;
                case IDC_BTN_ACT_RECENTS:  OnSendAction(ACT_RECENTS); break;
                case IDC_BTN_ACT_NOTIF:    OnSendAction(ACT_NOTIFICATIONS); break;
                case IDC_BTN_ACT_VOLDOWN:    OnSendAction(ACT_VOLUME_DOWN); break;
                case IDC_BTN_ACT_VOLUP:      OnSendAction(ACT_VOLUME_UP); break;
                case IDC_BTN_ACT_MUTE:       OnSendAction(ACT_VOLUME_MUTE); break;
                case IDC_BTN_ACT_PLAY:       OnSendAction(ACT_MEDIA_PLAY_PAUSE); break;
                case IDC_BTN_ACT_LOCK:       OnSendAction(ACT_LOCK_SCREEN); break;
                case IDC_BTN_ACT_SCREENSHOT: OnSendAction(ACT_SCREENSHOT); break;

                // Tray Menu Items
                case IDM_TRAY_OPEN:
                    ShowWindow(m_hWnd, SW_RESTORE);
                    SetForegroundWindow(m_hWnd);
                    break;

                case IDM_TRAY_TOGGLE_CAPTURE:
                    m_inputCapture->ToggleCapture();
                    break;

                case IDM_TRAY_CONNECT:
                    if (m_btManager->GetStatus() != ConnectionStatus::Listening && !m_btManager->IsConnected()) {
                        m_btManager->StartServer();
                    }
                    OnModeChanged();
                    break;

                case IDM_TRAY_CONNECT_DEVICE: {
                    int sel = ComboBox_GetCurSel(m_hComboDevices);
                    if (sel >= 0 && sel < (int)m_cachedDevices.size()) {
                        m_btManager->ConnectToDevice(m_cachedDevices[sel].address);
                    } else {
                        ShowWindow(m_hWnd, SW_RESTORE);
                        SetForegroundWindow(m_hWnd);
                    }
                    break;
                }

                case IDM_TRAY_DISCONNECT:
                    m_btManager->Disconnect();
                    OnModeChanged();
                    break;

                case IDM_TRAY_RESTART:
                    m_btManager->Disconnect();
                    Sleep(100);
                    if (Button_GetCheck(m_hRadioServer) == BST_CHECKED) {
                        m_btManager->StartServer();
                    } else {
                        int sel = ComboBox_GetCurSel(m_hComboDevices);
                        if (sel >= 0 && sel < (int)m_cachedDevices.size()) {
                            m_btManager->ConnectToDevice(m_cachedDevices[sel].address);
                        }
                    }
                    OnModeChanged();
                    break;

                case IDM_TRAY_HOME:    OnSendAction(ACT_HOME); break;
                case IDM_TRAY_BACK:    OnSendAction(ACT_BACK); break;
                case IDM_TRAY_RECENTS: OnSendAction(ACT_RECENTS); break;

                case IDM_TRAY_EXIT:
                    m_reallyClosing = true;
                    RemoveTrayIcon();
                    DestroyWindow(m_hWnd);
                    break;
            }
            break;
        }

        case WM_HSCROLL: {
            if ((HWND)lParam == m_hSliderSensitivity) {
                int pos = (int)SendMessageW(m_hSliderSensitivity, TBM_GETPOS, 0, 0);
                float sens = (float)pos / 10.0f;
                m_inputCapture->SetSensitivity(sens);

                std::wstringstream ss;
                ss << L"Touchpad Sensitivity: " << std::fixed << std::setprecision(1) << sens << L"x";
                Static_SetText(m_hLabelSensitivity, ss.str().c_str());
            }
            break;
        }

        case WM_CONTEXTMENU: {
            ShowTrayMenu();
            return 0;
        }

        case WM_TRAYNOTIFY: {
            UINT uMsg = LOWORD(lParam);
            if (lParam == WM_RBUTTONUP || lParam == WM_RBUTTONDOWN ||
                uMsg == WM_CONTEXTMENU || uMsg == WM_RBUTTONUP || uMsg == WM_RBUTTONDOWN) {
                ShowTrayMenu();
                return 0;
            } else if (lParam == WM_LBUTTONDBLCLK || lParam == WM_LBUTTONUP ||
                       uMsg == NIN_SELECT || uMsg == WM_LBUTTONUP || uMsg == WM_LBUTTONDBLCLK) {
                ShowWindow(m_hWnd, SW_RESTORE);
                SetForegroundWindow(m_hWnd);
                return 0;
            }
            break;
        }

        case WM_APP_LOG_MESSAGE: {
            UpdateLogView();
            break;
        }

        case WM_APP + 20: { // Status update
            UpdateStatusUI(m_btManager->GetStatus(), m_btManager->GetStatusMessage());
            break;
        }

        case WM_APP + 21: { // Capture update
            UpdateCaptureUI((bool)wParam);
            break;
        }

        case WM_CTLCOLORSTATIC: {
            HDC hdcStatic = (HDC)wParam;
            HWND hCtrl = (HWND)lParam;
            SetBkMode(hdcStatic, TRANSPARENT);

            if (hCtrl == m_hStatusText) {
                if (m_btManager->IsConnected()) {
                    SetTextColor(hdcStatic, m_theme.statusSuccess);
                } else if (m_btManager->GetStatus() == ConnectionStatus::Connecting ||
                           m_btManager->GetStatus() == ConnectionStatus::Listening) {
                    SetTextColor(hdcStatic, m_theme.accent);
                } else {
                    SetTextColor(hdcStatic, m_theme.textMuted);
                }
            } else {
                SetTextColor(hdcStatic, m_theme.text);
            }
            return (INT_PTR)m_hBgBrush;
        }

        case WM_CTLCOLORDLG:
            return (INT_PTR)m_hBgBrush;

        case WM_CTLCOLORBTN: {
            HDC hdc = (HDC)wParam;
            SetBkMode(hdc, TRANSPARENT);
            SetTextColor(hdc, m_theme.text);
            return (INT_PTR)m_hBgBrush;
        }

        case WM_CTLCOLOREDIT: {
            HDC hdc = (HDC)wParam;
            HWND hCtrl = (HWND)lParam;
            if (hCtrl == m_hEditLogs) {
                SetBkMode(hdc, OPAQUE);
                SetBkColor(hdc, m_theme.inputBg);
                SetTextColor(hdc, m_isDark ? RGB(126, 231, 135) : RGB(20, 110, 40));
                return (INT_PTR)m_hLogBgBrush;
            }
            SetBkMode(hdc, OPAQUE);
            SetBkColor(hdc, m_theme.inputBg);
            SetTextColor(hdc, m_theme.text);
            return (INT_PTR)m_hInputBrush;
        }

        case WM_CTLCOLORLISTBOX: {
            HDC hdc = (HDC)wParam;
            SetBkMode(hdc, OPAQUE);
            SetBkColor(hdc, m_theme.inputBg);
            SetTextColor(hdc, m_theme.text);
            return (INT_PTR)m_hInputBrush;
        }

        case WM_CLOSE: {
            if (!m_reallyClosing) {
                ShowWindow(m_hWnd, SW_HIDE);
                ShowTrayNotification(L"Lapdroid", L"App is still active in the system tray. Right-click the tray icon to exit.");
                return 0;
            }
            break;
        }

        case WM_DESTROY: {
            RemoveTrayIcon();
            PostQuitMessage(0);
            return 0;
        }
    }

    return DefWindowProcW(hWnd, msg, wParam, lParam);
}
