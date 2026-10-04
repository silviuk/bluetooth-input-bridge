#include <winsock2.h>
#include <windows.h>
#include "bluetooth_manager.h"
#include "input_capture.h"
#include "ui_window.h"

int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPWSTR lpCmdLine, int nCmdShow) {
    // Single instance check
    HANDLE hMutex = CreateMutexW(NULL, TRUE, L"Global\\S24_Bluetooth_Input_Bridge_Mutex");
    if (GetLastError() == ERROR_ALREADY_EXISTS) {
        HWND existingWnd = FindWindowW(L"S24_Input_Bridge_Class", NULL);
        if (existingWnd) {
            ShowWindow(existingWnd, SW_SHOW);
            SetForegroundWindow(existingWnd);
        }
        return 0;
    }

    BluetoothManager btManager;
    if (!btManager.Initialize()) {
        MessageBoxW(NULL, L"Failed to initialize Bluetooth stack. Please ensure Bluetooth is enabled on this PC.",
            L"Bluetooth Initialization Error", MB_OK | MB_ICONERROR);
        return 1;
    }

    InputCapture inputCapture(&btManager);
    MainWindow mainWindow(hInstance, &btManager, &inputCapture);

    if (!mainWindow.Create()) {
        MessageBoxW(NULL, L"Failed to create main application window.", L"Error", MB_OK | MB_ICONERROR);
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
