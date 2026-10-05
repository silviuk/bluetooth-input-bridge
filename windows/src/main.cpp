#include <winsock2.h>
#include <windows.h>
#include <uxtheme.h>
#include "bluetooth_manager.h"
#include "input_capture.h"
#include "ui_window.h"
#include "resource.h"

// SetPreferredAppMode function signature (ordinal 135 in uxtheme.dll)
typedef INT (WINAPI *fnSetPreferredAppMode)(INT);

int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPWSTR lpCmdLine, int nCmdShow) {
    // 1. Single instance enforcement using session-local named mutex
    HANDLE hMutex = CreateMutexW(NULL, TRUE, L"Local\\Lapdroid_SingleInstance_Mutex");
    DWORD dwErr = GetLastError();
    if (hMutex == NULL || dwErr == ERROR_ALREADY_EXISTS || dwErr == ERROR_ACCESS_DENIED) {
        // Another instance is already running; find its window and bring it to front
        for (int i = 0; i < 10; ++i) {
            HWND existingWnd = FindWindowW(L"Lapdroid_Class", NULL);
            if (existingWnd) {
                ShowWindow(existingWnd, SW_RESTORE);
                SetForegroundWindow(existingWnd);
                PostMessageW(existingWnd, WM_COMMAND, MAKEWPARAM(IDM_TRAY_OPEN, 0), 0);
                break;
            }
            Sleep(50);
        }
        if (hMutex) {
            CloseHandle(hMutex);
        }
        return 0;
    }

    // Enable process-wide dark mode support if supported by OS (Windows 10 1903+)
    HMODULE hUxTheme = LoadLibraryW(L"uxtheme.dll");
    if (hUxTheme) {
        fnSetPreferredAppMode pfnSetPreferredAppMode =
            (fnSetPreferredAppMode)GetProcAddress(hUxTheme, MAKEINTRESOURCEA(135));
        if (pfnSetPreferredAppMode) {
            pfnSetPreferredAppMode(1); // 1 = AllowDark
        }
    }

    BluetoothManager btManager;
    if (!btManager.Initialize()) {
        MessageBoxW(NULL, L"Failed to initialize Bluetooth stack. Please ensure Bluetooth is enabled on this PC.",
            L"Bluetooth Initialization Error", MB_OK | MB_ICONERROR);
        if (hMutex) {
            ReleaseMutex(hMutex);
            CloseHandle(hMutex);
        }
        return 1;
    }

    InputCapture inputCapture(&btManager);
    MainWindow mainWindow(hInstance, &btManager, &inputCapture);

    if (!mainWindow.Create()) {
        MessageBoxW(NULL, L"Failed to create main application window.", L"Error", MB_OK | MB_ICONERROR);
        if (hMutex) {
            ReleaseMutex(hMutex);
            CloseHandle(hMutex);
        }
        return 1;
    }

    mainWindow.Show(nCmdShow);

    // Message loop
    MSG msg;
    while (GetMessageW(&msg, NULL, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }

    inputCapture.StopCapture();
    btManager.Cleanup();

    if (hMutex) {
        ReleaseMutex(hMutex);
        CloseHandle(hMutex);
    }

    return (int)msg.wParam;
}
