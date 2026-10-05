#include "stylus_injector.h"
#include "logger.h"
#include <algorithm>

StylusInjector::StylusInjector()
    : m_hPenDevice(NULL)
    , m_isContact(false)
    , m_inRange(false)
    , m_lastPixelX(0)
    , m_lastPixelY(0)
{
}

StylusInjector::~StylusInjector() {
    Cleanup();
}

bool StylusInjector::Initialize() {
    if (m_hPenDevice != NULL) {
        return true;
    }

    // Create synthetic pen pointer device for Windows Ink (maxCount=1)
    m_hPenDevice = CreateSyntheticPointerDevice(PT_PEN, 1, POINTER_FEEDBACK_DEFAULT);
    if (m_hPenDevice != NULL) {
        LOG_INFO(L"StylusInjector", L"Created synthetic PT_PEN device (Windows Ink pressure & tilt enabled)");
        return true;
    }

    DWORD err = GetLastError();
    LOG_WARN(L"StylusInjector", L"CreateSyntheticPointerDevice failed (err " + std::to_wstring(err) + L"), will use mouse fallback");
    return false;
}

void StylusInjector::Cleanup() {
    if (m_hPenDevice != NULL) {
        if (m_isContact) {
            POINTER_TYPE_INFO info;
            ZeroMemory(&info, sizeof(info));
            info.type = PT_PEN;
            info.penInfo.pointerInfo.pointerType = PT_PEN;
            info.penInfo.pointerInfo.pointerId = 0;
            info.penInfo.pointerInfo.ptPixelLocation.x = m_lastPixelX;
            info.penInfo.pointerInfo.ptPixelLocation.y = m_lastPixelY;
            info.penInfo.pointerInfo.pointerFlags = POINTER_FLAG_INRANGE | POINTER_FLAG_UP;
            InjectSyntheticPointerInput(m_hPenDevice, &info, 1);
            m_isContact = false;
        }

        DestroySyntheticPointerDevice(m_hPenDevice);
        m_hPenDevice = NULL;
        LOG_INFO(L"StylusInjector", L"Synthetic PT_PEN device destroyed");
    }
    m_inRange = false;
    m_isContact = false;
}

void StylusInjector::ProcessStylusPacket(const StylusInputPayload& payload) {
    // 1. Calculate target pixel coordinates on primary monitor
    int screenW = GetSystemMetrics(SM_CXSCREEN);
    int screenH = GetSystemMetrics(SM_CYSCREEN);

    if (screenW <= 0) screenW = 1920;
    if (screenH <= 0) screenH = 1080;

    int pixelX = (int)(((int64_t)payload.norm_x * (screenW - 1)) / 65535);
    int pixelY = (int)(((int64_t)payload.norm_y * (screenH - 1)) / 65535);

    pixelX = std::max(0, std::min(pixelX, screenW - 1));
    pixelY = std::max(0, std::min(pixelY, screenH - 1));

    m_lastPixelX = pixelX;
    m_lastPixelY = pixelY;

    if (m_hPenDevice != NULL) {
        InjectPenInput(pixelX, pixelY, payload.action, payload.flags, payload.pressure, payload.tilt_x, payload.tilt_y);
    } else {
        FallbackMouseInput(pixelX, pixelY, payload.action, payload.flags);
    }
}

void StylusInjector::InjectPenInput(int pixelX, int pixelY, uint8_t action, uint8_t flags, uint16_t pressure, int8_t tiltX, int8_t tiltY) {
    POINTER_TYPE_INFO info;
    ZeroMemory(&info, sizeof(info));

    info.type = PT_PEN;
    info.penInfo.pointerInfo.pointerType = PT_PEN;
    info.penInfo.pointerInfo.pointerId = 0; // Device created with maxCount=1, pointerId must be 0
    info.penInfo.pointerInfo.ptPixelLocation.x = pixelX;
    info.penInfo.pointerInfo.ptPixelLocation.y = pixelY;
    info.penInfo.pointerInfo.dwTime = 0;
    info.penInfo.pointerInfo.PerformanceCount = 0;

    // Pen mask
    info.penInfo.penMask = PEN_MASK_PRESSURE;
    if (tiltX != 0 || tiltY != 0) {
        info.penInfo.penMask |= (PEN_MASK_TILT_X | PEN_MASK_TILT_Y);
        info.penInfo.tiltX = (INT32)tiltX;
        info.penInfo.tiltY = (INT32)tiltY;
    }

    // Pen flags: barrel (side button), eraser/inverted
    UINT32 penFlags = PEN_FLAG_NONE;
    if ((flags & STYLUS_FLAG_BARREL) != 0) {
        penFlags |= PEN_FLAG_BARREL;
    }
    if ((flags & STYLUS_FLAG_ERASER) != 0) {
        penFlags |= (PEN_FLAG_INVERTED | PEN_FLAG_ERASER);
    }
    info.penInfo.penFlags = penFlags;

    UINT32 pointerFlags = 0;

    switch (action) {
        case STYLUS_HOVER: {
            pointerFlags = POINTER_FLAG_INRANGE | POINTER_FLAG_UPDATE;
            info.penInfo.pressure = 0;
            m_inRange = true;
            m_isContact = false;
            break;
        }

        case STYLUS_DOWN: {
            pointerFlags = POINTER_FLAG_INRANGE | POINTER_FLAG_INCONTACT | POINTER_FLAG_DOWN;
            if ((flags & STYLUS_FLAG_BARREL) != 0) {
                pointerFlags |= POINTER_FLAG_SECONDBUTTON;
            }
            // Ensure minimum contact pressure is non-zero (Windows Ink expects 1..1024)
            info.penInfo.pressure = (UINT32)std::max<uint16_t>(1, std::min<uint16_t>(pressure, 1024));
            m_inRange = true;
            m_isContact = true;
            break;
        }

        case STYLUS_MOVE: {
            if (m_isContact) {
                pointerFlags = POINTER_FLAG_INRANGE | POINTER_FLAG_INCONTACT | POINTER_FLAG_UPDATE;
                if ((flags & STYLUS_FLAG_BARREL) != 0) {
                    pointerFlags |= POINTER_FLAG_SECONDBUTTON;
                }
                info.penInfo.pressure = (UINT32)std::max<uint16_t>(1, std::min<uint16_t>(pressure, 1024));
            } else {
                pointerFlags = POINTER_FLAG_INRANGE | POINTER_FLAG_UPDATE;
                info.penInfo.pressure = 0;
            }
            m_inRange = true;
            break;
        }

        case STYLUS_UP: {
            pointerFlags = POINTER_FLAG_INRANGE | POINTER_FLAG_UP;
            info.penInfo.pressure = 0;
            m_isContact = false;
            break;
        }

        default:
            return;
    }

    info.penInfo.pointerInfo.pointerFlags = pointerFlags;

    BOOL ok = InjectSyntheticPointerInput(m_hPenDevice, &info, 1);
    if (!ok) {
        DWORD err = GetLastError();
        static DWORD s_lastLoggedErr = 0;
        if (err != s_lastLoggedErr) {
            s_lastLoggedErr = err;
            LOG_ERROR(L"StylusInjector", L"InjectSyntheticPointerInput error " + std::to_wstring(err) +
                      L" [action=" + std::to_wstring(action) + L" pFlags=0x" + std::to_wstring(pointerFlags) + L"]");
        }
        // Fallback to mouse only if synthetic injection failed
        FallbackMouseInput(pixelX, pixelY, action, flags);
    } else {
        static bool s_firstSuccess = false;
        if (!s_firstSuccess) {
            s_firstSuccess = true;
            LOG_INFO(L"StylusInjector", L"Windows Ink PT_PEN synthetic injection active and working!");
        }
    }
}

void StylusInjector::FallbackMouseInput(int pixelX, int pixelY, uint8_t action, uint8_t flags) {
    INPUT input = {};
    input.type = INPUT_MOUSE;

    int screenW = GetSystemMetrics(SM_CXSCREEN);
    int screenH = GetSystemMetrics(SM_CYSCREEN);
    if (screenW <= 0) screenW = 1920;
    if (screenH <= 0) screenH = 1080;

    input.mi.dx = (LONG)(((int64_t)pixelX * 65535) / (screenW - 1));
    input.mi.dy = (LONG)(((int64_t)pixelY * 65535) / (screenH - 1));
    input.mi.dwFlags = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE;

    bool isRightBtn = ((flags & STYLUS_FLAG_BARREL) != 0) || ((flags & STYLUS_FLAG_ERASER) != 0);

    if (action == STYLUS_DOWN) {
        input.mi.dwFlags |= isRightBtn ? MOUSEEVENTF_RIGHTDOWN : MOUSEEVENTF_LEFTDOWN;
        m_isContact = true;
    } else if (action == STYLUS_UP) {
        input.mi.dwFlags |= isRightBtn ? MOUSEEVENTF_RIGHTUP : MOUSEEVENTF_LEFTUP;
        m_isContact = false;
    }

    SendInput(1, &input, sizeof(INPUT));
}
