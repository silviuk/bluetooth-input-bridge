#ifndef STYLUS_INJECTOR_H
#define STYLUS_INJECTOR_H

#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0A00
#endif
#include <winsock2.h>
#include <windows.h>
#include <winuser.h>
#include <cstdint>
#include "protocol.h"

class StylusInjector {
public:
    StylusInjector();
    ~StylusInjector();

    bool Initialize();
    void Cleanup();

    void ProcessStylusPacket(const StylusInputPayload& payload);

    bool IsPenDeviceActive() const { return m_hPenDevice != NULL; }

private:
    HSYNTHETICPOINTERDEVICE m_hPenDevice;
    bool m_isContact;
    bool m_inRange;
    int m_lastPixelX;
    int m_lastPixelY;

    void InjectPenInput(int pixelX, int pixelY, uint8_t action, uint8_t flags, uint16_t pressure, int8_t tiltX, int8_t tiltY);
    void FallbackMouseInput(int pixelX, int pixelY, uint8_t action, uint8_t flags);
};

#endif // STYLUS_INJECTOR_H
