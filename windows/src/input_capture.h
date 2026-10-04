#ifndef INPUT_CAPTURE_H
#define INPUT_CAPTURE_H

#include <winsock2.h>
#include <windows.h>
#include <functional>
#include <atomic>

class BluetoothManager;

class InputCapture {
public:
    using CaptureToggleCallback = std::function<void(bool isCapturing)>;

    InputCapture(BluetoothManager* btManager);
    ~InputCapture();

    bool StartCapture();
    void StopCapture();
    bool ToggleCapture();
    bool IsCapturing() const { return m_capturing; }

    void SetToggleCallback(CaptureToggleCallback cb) { m_toggleCb = cb; }
    void SetSensitivity(float sensitivity) { m_sensitivity = sensitivity; }
    float GetSensitivity() const { return m_sensitivity; }

    // Internal hook handlers
    LRESULT HandleKeyboardHook(int nCode, WPARAM wParam, LPARAM lParam);
    LRESULT HandleMouseHook(int nCode, WPARAM wParam, LPARAM lParam);

private:
    BluetoothManager* m_btManager;
    HHOOK m_keyboardHook;
    HHOOK m_mouseHook;
    std::atomic<bool> m_capturing;
    CaptureToggleCallback m_toggleCb;

    float m_sensitivity;
    POINT m_centerPt;
    bool m_recentering;
    BYTE m_keyState[256];
};

uint16_t WindowsVkToAndroidKeycode(DWORD vk);

#endif // INPUT_CAPTURE_H
