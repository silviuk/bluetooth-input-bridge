#include "ui_window.h"
#include "protocol.h"
#include "resource.h"
#include <windowsx.h>
#include <commctrl.h>
#include <sstream>
#include <iomanip>

#define CLR_BG          RGB(26, 27, 38)
#define CLR_CARD        RGB(36, 40, 59)
#define CLR_TEXT        RGB(192, 202, 245)
#define CLR_TEXT_MUTED  RGB(122, 132, 172)
#define CLR_ACCENT      RGB(122, 162, 247)
#define CLR_SUCCESS     RGB(158, 206, 106)
#define CLR_ERROR       RGB(247, 118, 142)

static const wchar_t* WINDOW_CLASS_NAME = L"Lapdroid_Class";

MainWindow::MainWindow(HINSTANCE hInstance, BluetoothManager* btManager, InputCapture* inputCapture)
    : m_hInstance(hInstance)
    , m_hWnd(NULL)
    , m_btManager(btManager)
    , m_inputCapture(inputCapture)
    , m_hRadioServer(NULL)
    , m_hRadioClient(NULL)
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
    , m_hBtnLock(NULL)
    , m_hFontTitle(NULL)
    , m_hFontNormal(NULL)
    , m_hFontBold(NULL)
    , m_hFontStatus(NULL)
    , m_hBgBrush(NULL)
    , m_hCardBrush(NULL)
    , m_trayAdded(false)
    , m_reallyClosing(false)
{
    ZeroMemory(&m_nid, sizeof(m_nid));
    m_hBgBrush = CreateSolidBrush(CLR_BG);
    m_hCardBrush = CreateSolidBrush(CLR_CARD);
}

MainWindow::~MainWindow() {
    RemoveTrayIcon();
    if (m_hBgBrush) DeleteObject(m_hBgBrush);
    if (m_hCardBrush) DeleteObject(m_hCardBrush);
    if (m_hFontTitle) DeleteObject(m_hFontTitle);
    if (m_hFontNormal) DeleteObject(m_hFontNormal);
    if (m_hFontBold) DeleteObject(m_hFontBold);
    if (m_hFontStatus) DeleteObject(m_hFontStatus);
}

bool MainWindow::Create() {
    INITCOMMONCONTROLSEX icex;
    icex.dwSize = sizeof(INITCOMMONCONTROLSEX);
    icex.dwICC = ICC_STANDARD_CLASSES | ICC_BAR_CLASSES | ICC_WIN95_CLASSES;
    InitCommonControlsEx(&icex);

    WNDCLASSEXW wc;
    ZeroMemory(&wc, sizeof(wc));
    wc.cbSize = sizeof(WNDCLASSEXW);
    wc.style = CS_HREDRAW | CS_VREDRAW;
    wc.lpfnWndProc = MainWindow::WndProc;
    wc.hInstance = m_hInstance;
    wc.hIcon = LoadIcon(NULL, IDI_APPLICATION);
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    wc.hbrBackground = m_hBgBrush;
    wc.lpszClassName = WINDOW_CLASS_NAME;

    RegisterClassExW(&wc);

    int winW = 560;
    int winH = 650;
    int screenW = GetSystemMetrics(SM_CXSCREEN);
    int screenH = GetSystemMetrics(SM_CYSCREEN);
    int startX = (screenW - winW) / 2;
    int startY = (screenH - winH) / 2;

    m_hWnd = CreateWindowExW(
        0,
        WINDOW_CLASS_NAME,
        L"Lapdroid - Bluetooth Keyboard & Touchpad",
        WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
        startX, startY, winW, winH,
        NULL, NULL, m_hInstance, this
    );

    if (!m_hWnd) return false;

    CreateControls();
    ApplyModernFonts();
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
}

void MainWindow::CreateControls() {
    int padX = 25;
    int curY = 20;

    // Title label
    HWND hTitle = CreateWindowExW(0, L"STATIC", L"Lapdroid",
        WS_CHILD | WS_VISIBLE | SS_LEFT,
        padX, curY, 500, 28, m_hWnd, NULL, m_hInstance, NULL);
    SendMessageW(hTitle, WM_SETFONT, (WPARAM)m_hFontTitle, TRUE);
    curY += 32;

    // Subtitle label
    HWND hSub = CreateWindowExW(0, L"STATIC", L"Control your phone with your laptop/PC keyboard and touchpad over Bluetooth",
        WS_CHILD | WS_VISIBLE | SS_LEFT,
        padX, curY, 500, 20, m_hWnd, NULL, m_hInstance, NULL);
    SendMessageW(hSub, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    curY += 32;

    // Card 1: Connection Mode
    HWND hModeLabel = CreateWindowExW(0, L"STATIC", L"1. Connection Mode & Pairing",
        WS_CHILD | WS_VISIBLE | SS_LEFT,
        padX, curY, 500, 20, m_hWnd, NULL, m_hInstance, NULL);
    SendMessageW(hModeLabel, WM_SETFONT, (WPARAM)m_hFontBold, TRUE);
    curY += 24;

    m_hRadioServer = CreateWindowExW(0, L"BUTTON", L"Server Mode (Wait for S24 Ultra to connect)",
        WS_CHILD | WS_VISIBLE | BS_AUTORADIOBUTTON | WS_GROUP,
        padX + 10, curY, 480, 22, m_hWnd, (HMENU)IDC_RADIO_SERVER, m_hInstance, NULL);
    SendMessageW(m_hRadioServer, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    curY += 26;

    m_hRadioClient = CreateWindowExW(0, L"BUTTON", L"Client Mode (Connect to paired S24 Ultra)",
        WS_CHILD | WS_VISIBLE | BS_AUTORADIOBUTTON,
        padX + 10, curY, 480, 22, m_hWnd, (HMENU)IDC_RADIO_CLIENT, m_hInstance, NULL);
    SendMessageW(m_hRadioClient, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    Button_SetCheck(m_hRadioServer, BST_CHECKED);
    curY += 30;

    // Device dropdown and refresh button
    m_hComboDevices = CreateWindowExW(0, L"COMBOBOX", L"",
        WS_CHILD | WS_VISIBLE | CBS_DROPDOWNLIST | WS_VSCROLL | WS_TABSTOP,
        padX + 10, curY, 360, 200, m_hWnd, (HMENU)IDC_COMBO_DEVICES, m_hInstance, NULL);
    SendMessageW(m_hComboDevices, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);

    m_hBtnRefresh = CreateWindowExW(0, L"BUTTON", L"Refresh Devices",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        padX + 380, curY - 1, 110, 28, m_hWnd, (HMENU)IDC_BTN_REFRESH, m_hInstance, NULL);
    SendMessageW(m_hBtnRefresh, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    curY += 36;

    // Connect / Disconnect button
    m_hBtnConnect = CreateWindowExW(0, L"BUTTON", L"Start Bluetooth Server",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        padX + 10, curY, 480, 36, m_hWnd, (HMENU)IDC_BTN_CONNECT, m_hInstance, NULL);
    SendMessageW(m_hBtnConnect, WM_SETFONT, (WPARAM)m_hFontBold, TRUE);
    curY += 46;

    // Status label
    m_hStatusText = CreateWindowExW(0, L"STATIC", L"Status: Ready (Disconnected)",
        WS_CHILD | WS_VISIBLE | SS_LEFT,
        padX + 10, curY, 480, 22, m_hWnd, (HMENU)IDC_STATUS_TEXT, m_hInstance, NULL);
    SendMessageW(m_hStatusText, WM_SETFONT, (WPARAM)m_hFontStatus, TRUE);
    curY += 32;

    // Card 2: Input Redirection
    HWND hInputLabel = CreateWindowExW(0, L"STATIC", L"2. Input Redirection",
        WS_CHILD | WS_VISIBLE | SS_LEFT,
        padX, curY, 500, 20, m_hWnd, NULL, m_hInstance, NULL);
    SendMessageW(hInputLabel, WM_SETFONT, (WPARAM)m_hFontBold, TRUE);
    curY += 24;

    m_hBtnToggleCapture = CreateWindowExW(0, L"BUTTON", L"Capture Input for S24 Ultra (Hotkey: F12)",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        padX + 10, curY, 480, 42, m_hWnd, (HMENU)IDC_BTN_TOGGLE_CAPTURE, m_hInstance, NULL);
    SendMessageW(m_hBtnToggleCapture, WM_SETFONT, (WPARAM)m_hFontBold, TRUE);
    curY += 50;

    // Sensitivity slider
    m_hLabelSensitivity = CreateWindowExW(0, L"STATIC", L"Touchpad Sensitivity: 1.0x",
        WS_CHILD | WS_VISIBLE | SS_LEFT,
        padX + 10, curY, 200, 20, m_hWnd, (HMENU)IDC_LABEL_SENSITIVITY, m_hInstance, NULL);
    SendMessageW(m_hLabelSensitivity, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);

    m_hSliderSensitivity = CreateWindowExW(0, TRACKBAR_CLASSW, L"",
        WS_CHILD | WS_VISIBLE | TBS_AUTOTICKS | TBS_ENABLESELRANGE,
        padX + 220, curY - 4, 270, 30, m_hWnd, (HMENU)IDC_SLIDER_SENSITIVITY, m_hInstance, NULL);
    SendMessageW(m_hSliderSensitivity, TBM_SETRANGE, TRUE, MAKELPARAM(5, 30)); // 0.5x to 3.0x
    SendMessageW(m_hSliderSensitivity, TBM_SETPOS, TRUE, 10); // 1.0x default
    curY += 36;

    // Hint label
    HWND hHint = CreateWindowExW(0, L"STATIC", L"Tip: While capturing, mouse moves the phone pointer and keys type on your phone. Press F12 to immediately return control to Windows.",
        WS_CHILD | WS_VISIBLE | SS_LEFT,
        padX + 10, curY, 480, 36, m_hWnd, NULL, m_hInstance, NULL);
    SendMessageW(hHint, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    curY += 44;

    // Card 3: Phone Remote Quick Actions
    HWND hActionLabel = CreateWindowExW(0, L"STATIC", L"3. Quick Phone Actions",
        WS_CHILD | WS_VISIBLE | SS_LEFT,
        padX, curY, 500, 20, m_hWnd, NULL, m_hInstance, NULL);
    SendMessageW(hActionLabel, WM_SETFONT, (WPARAM)m_hFontBold, TRUE);
    curY += 24;

    int btnW = 66;
    int gap = 3;
    int actX = padX + 5;

    m_hBtnBack = CreateWindowExW(0, L"BUTTON", L"Back", WS_CHILD | WS_VISIBLE, actX, curY, btnW, 28, m_hWnd, (HMENU)IDC_BTN_ACT_BACK, m_hInstance, NULL);
    actX += btnW + gap;
    m_hBtnHome = CreateWindowExW(0, L"BUTTON", L"Home", WS_CHILD | WS_VISIBLE, actX, curY, btnW, 28, m_hWnd, (HMENU)IDC_BTN_ACT_HOME, m_hInstance, NULL);
    actX += btnW + gap;
    m_hBtnRecents = CreateWindowExW(0, L"BUTTON", L"Recents", WS_CHILD | WS_VISIBLE, actX, curY, btnW, 28, m_hWnd, (HMENU)IDC_BTN_ACT_RECENTS, m_hInstance, NULL);
    actX += btnW + gap;
    m_hBtnNotif = CreateWindowExW(0, L"BUTTON", L"Notif", WS_CHILD | WS_VISIBLE, actX, curY, btnW, 28, m_hWnd, (HMENU)IDC_BTN_ACT_NOTIF, m_hInstance, NULL);
    actX += btnW + gap;
    m_hBtnVolDown = CreateWindowExW(0, L"BUTTON", L"Vol -", WS_CHILD | WS_VISIBLE, actX, curY, btnW, 28, m_hWnd, (HMENU)IDC_BTN_ACT_VOLDOWN, m_hInstance, NULL);
    actX += btnW + gap;
    m_hBtnVolUp = CreateWindowExW(0, L"BUTTON", L"Vol +", WS_CHILD | WS_VISIBLE, actX, curY, btnW, 28, m_hWnd, (HMENU)IDC_BTN_ACT_VOLUP, m_hInstance, NULL);
    actX += btnW + gap;
    m_hBtnLock = CreateWindowExW(0, L"BUTTON", L"Lock", WS_CHILD | WS_VISIBLE, actX, curY, btnW, 28, m_hWnd, (HMENU)IDC_BTN_ACT_LOCK, m_hInstance, NULL);

    SendMessageW(m_hBtnBack, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    SendMessageW(m_hBtnHome, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    SendMessageW(m_hBtnRecents, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    SendMessageW(m_hBtnNotif, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    SendMessageW(m_hBtnVolDown, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    SendMessageW(m_hBtnVolUp, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    SendMessageW(m_hBtnLock, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);
    curY += 38;

    // Minimize to System Tray button
    m_hBtnMinimizeTray = CreateWindowExW(0, L"BUTTON", L"Hide to System Tray (Keep Running)",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        padX + 10, curY, 480, 32, m_hWnd, (HMENU)IDC_BTN_MINIMIZE_TRAY, m_hInstance, NULL);
    SendMessageW(m_hBtnMinimizeTray, WM_SETFONT, (WPARAM)m_hFontNormal, TRUE);

    OnModeChanged();
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

        // Check if device name contains S24 or Galaxy
        if (displayName.find(L"S24") != std::wstring::npos ||
            displayName.find(L"Galaxy") != std::wstring::npos ||
            displayName.find(L"Samsung") != std::wstring::npos) {
            defaultIndex = (int)i;
        }
    }

    if (m_cachedDevices.empty()) {
        ComboBox_AddString(m_hComboDevices, L"No paired devices found (pair S24 in Windows Settings)");
        ComboBox_SetCurSel(m_hComboDevices, 0);
    } else {
        ComboBox_SetCurSel(m_hComboDevices, (defaultIndex >= 0) ? defaultIndex : 0);
    }
}

void MainWindow::SetupTrayIcon() {
    ZeroMemory(&m_nid, sizeof(m_nid));
    m_nid.cbSize = sizeof(NOTIFYICONDATAW);
    m_nid.hWnd = m_hWnd;
    m_nid.uID = 1;
    m_nid.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP | NIF_INFO;
    m_nid.uCallbackMessage = WM_TRAYNOTIFY;
    m_nid.hIcon = LoadIcon(NULL, IDI_APPLICATION);
    wcscpy(m_nid.szTip, L"Lapdroid - Bluetooth Input Bridge");

    Shell_NotifyIconW(NIM_ADD, &m_nid);
    m_trayAdded = true;
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
        tip += L"Connected";
    } else if (m_btManager->GetStatus() == ConnectionStatus::Listening) {
        tip += L"Listening";
    } else {
        tip += L"Disconnected";
    }

    tip += m_inputCapture->IsCapturing() ? L" [Capture: ACTIVE]" : L" [Capture: OFF]";

    wcsncpy(m_nid.szTip, tip.c_str(), sizeof(m_nid.szTip) / sizeof(wchar_t) - 1);
    m_nid.uFlags = NIF_TIP;
    Shell_NotifyIconW(NIM_MODIFY, &m_nid);
}

void MainWindow::ShowTrayNotification(const std::wstring& title, const std::wstring& msg) {
    if (!m_trayAdded) return;

    wcsncpy(m_nid.szInfoTitle, title.c_str(), sizeof(m_nid.szInfoTitle) / sizeof(wchar_t) - 1);
    wcsncpy(m_nid.szInfo, msg.c_str(), sizeof(m_nid.szInfo) / sizeof(wchar_t) - 1);
    m_nid.dwInfoFlags = NIIF_INFO;
    m_nid.uFlags = NIF_INFO;
    Shell_NotifyIconW(NIM_MODIFY, &m_nid);
}

void MainWindow::ShowTrayMenu() {
    HMENU hMenu = CreatePopupMenu();
    if (!hMenu) return;

    InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_STRING | MF_DEFAULT, IDM_TRAY_OPEN, L"Open Window");
    InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_TRAY_TOGGLE_CAPTURE,
        m_inputCapture->IsCapturing() ? L"Release Capture (F12)" : L"Capture Input (F12)");
    InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_SEPARATOR, 0, NULL);
    InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_TRAY_HOME, L"Android Home");
    InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_TRAY_BACK, L"Android Back");
    InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_TRAY_RECENTS, L"Android Recents");
    InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_SEPARATOR, 0, NULL);

    if (m_btManager->IsConnected()) {
        InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_TRAY_DISCONNECT, L"Disconnect");
    } else {
        InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_TRAY_CONNECT, L"Connect / Start Server");
    }

    InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_SEPARATOR, 0, NULL);
    InsertMenuW(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_TRAY_EXIT, L"Exit Application");

    POINT pt;
    GetCursorPos(&pt);
    SetForegroundWindow(m_hWnd);
    TrackPopupMenu(hMenu, TPM_BOTTOMALIGN | TPM_LEFTALIGN, pt.x, pt.y, 0, m_hWnd, NULL);
    DestroyMenu(hMenu);
}

void MainWindow::OnModeChanged() {
    bool isServer = (Button_GetCheck(m_hRadioServer) == BST_CHECKED);
    EnableWindow(m_hComboDevices, !isServer);
    EnableWindow(m_hBtnRefresh, !isServer);

    if (!m_btManager->IsConnected() && m_btManager->GetStatus() != ConnectionStatus::Listening) {
        if (isServer) {
            Button_SetText(m_hBtnConnect, L"Start Bluetooth Server (Listen)");
        } else {
            Button_SetText(m_hBtnConnect, L"Connect to Selected Device");
        }
    }
}

void MainWindow::OnConnectButtonClicked() {
    if (m_btManager->IsConnected() || m_btManager->GetStatus() == ConnectionStatus::Listening) {
        m_btManager->Disconnect();
        OnModeChanged();
        return;
    }

    bool isServer = (Button_GetCheck(m_hRadioServer) == BST_CHECKED);
    if (isServer) {
        m_btManager->StartServer();
        Button_SetText(m_hBtnConnect, L"Stop Server");
    } else {
        int sel = ComboBox_GetCurSel(m_hComboDevices);
        if (sel >= 0 && sel < (int)m_cachedDevices.size()) {
            BTH_ADDR addr = m_cachedDevices[sel].address;
            m_btManager->ConnectToDevice(addr, 1);
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
        MessageBoxW(m_hWnd, L"Bluetooth is not connected to S24 Ultra.", L"Not Connected", MB_OK | MB_ICONINFORMATION);
        return;
    }

    SystemActionPayload payload;
    payload.action = action;
    m_btManager->SendPacket(MSG_SYSTEM_ACTION, &payload, sizeof(payload));
}

void MainWindow::UpdateStatusUI(ConnectionStatus status, const std::wstring& msg) {
    std::wstring displayText = L"Status: " + msg;
    Static_SetText(m_hStatusText, displayText.c_str());

    if (status == ConnectionStatus::Connected) {
        Button_SetText(m_hBtnConnect, L"Disconnect");
        ShowTrayNotification(L"Connected to S24 Ultra", L"Bluetooth input link established. Press F12 to capture input.");
    } else if (status == ConnectionStatus::Listening) {
        Button_SetText(m_hBtnConnect, L"Stop Server");
    } else {
        OnModeChanged();
    }
    UpdateTrayTooltip();
}

void MainWindow::UpdateCaptureUI(bool isCapturing) {
    if (isCapturing) {
        Button_SetText(m_hBtnToggleCapture, L">> CAPTURING INPUT FOR S24 ULTRA (Press F12 to Stop) <<");
    } else {
        Button_SetText(m_hBtnToggleCapture, L"Capture Input for S24 Ultra (Hotkey: F12)");
    }
    UpdateTrayTooltip();
}

LRESULT CALLBACK MainWindow::WndProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    MainWindow* pThis = nullptr;
    if (msg == WM_NCCREATE) {
        CREATESTRUCT* pCS = (CREATESTRUCT*)lParam;
        pThis = (MainWindow*)pCS->lpCreateParams;
        if (pThis) {
            pThis->m_hWnd = hWnd;
            SetWindowLongPtr(hWnd, GWLP_USERDATA, (LONG_PTR)pThis);
        }
        return DefWindowProcW(hWnd, msg, wParam, lParam);
    } else {
        pThis = (MainWindow*)GetWindowLongPtr(hWnd, GWLP_USERDATA);
    }

    if (pThis) {
        return pThis->HandleMessage(hWnd, msg, wParam, lParam);
    }
    return DefWindowProcW(hWnd, msg, wParam, lParam);
}

LRESULT MainWindow::HandleMessage(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
        case WM_COMMAND: {
            int wmId = LOWORD(wParam);
            int wmEvent = HIWORD(wParam);

            switch (wmId) {
                case IDC_RADIO_SERVER:
                case IDC_RADIO_CLIENT:
                    if (wmEvent == BN_CLICKED) OnModeChanged();
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

                case IDC_BTN_MINIMIZE_TRAY:
                    ShowWindow(m_hWnd, SW_HIDE);
                    ShowTrayNotification(L"S24 Ultra Input Bridge", L"Running in system tray. Right-click icon for quick actions.");
                    break;

                case IDC_BTN_ACT_BACK:     OnSendAction(ACT_BACK); break;
                case IDC_BTN_ACT_HOME:     OnSendAction(ACT_HOME); break;
                case IDC_BTN_ACT_RECENTS:  OnSendAction(ACT_RECENTS); break;
                case IDC_BTN_ACT_NOTIF:    OnSendAction(ACT_NOTIFICATIONS); break;
                case IDC_BTN_ACT_VOLDOWN:  OnSendAction(ACT_VOLUME_DOWN); break;
                case IDC_BTN_ACT_VOLUP:    OnSendAction(ACT_VOLUME_UP); break;
                case IDC_BTN_ACT_LOCK:     OnSendAction(ACT_LOCK_SCREEN); break;

                // Tray Menu Items
                case IDM_TRAY_OPEN:
                    ShowWindow(m_hWnd, SW_SHOW);
                    SetForegroundWindow(m_hWnd);
                    break;

                case IDM_TRAY_TOGGLE_CAPTURE:
                    m_inputCapture->ToggleCapture();
                    break;

                case IDM_TRAY_CONNECT:
                case IDM_TRAY_DISCONNECT:
                    OnConnectButtonClicked();
                    break;

                case IDM_TRAY_HOME:    OnSendAction(ACT_HOME); break;
                case IDM_TRAY_BACK:    OnSendAction(ACT_BACK); break;
                case IDM_TRAY_RECENTS: OnSendAction(ACT_RECENTS); break;

                case IDM_TRAY_EXIT:
                    m_reallyClosing = true;
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

        case WM_TRAYNOTIFY: {
            if (lParam == WM_RBUTTONUP) {
                ShowTrayMenu();
            } else if (lParam == WM_LBUTTONDBLCLK || lParam == WM_LBUTTONUP) {
                ShowWindow(m_hWnd, SW_SHOW);
                SetForegroundWindow(m_hWnd);
            }
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
            SetBkMode(hdcStatic, TRANSPARENT);
            SetTextColor(hdcStatic, CLR_TEXT);
            return (INT_PTR)m_hBgBrush;
        }

        case WM_CLOSE: {
            if (!m_reallyClosing) {
                // Minimize to tray instead of closing
                ShowWindow(m_hWnd, SW_HIDE);
                ShowTrayNotification(L"S24 Ultra Input Bridge", L"App is still active in the system tray. Right-click the tray icon to exit.");
                return 0;
            }
            break;
        }

        case WM_DESTROY: {
            PostQuitMessage(0);
            return 0;
        }
    }

    return DefWindowProcW(hWnd, msg, wParam, lParam);
}
