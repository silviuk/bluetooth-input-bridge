# Lapdroid (Laptop to Android Bluetooth Input Bridge)

Control your **Android phone** using your Windows laptop or PC keyboard and touchpad over **Bluetooth**.

[![Website](https://img.shields.io/badge/Website-silviuk.github.io%2Flapdroid-blue)](https://silviuk.github.io/lapdroid/)
[![WinGet PR](https://img.shields.io/badge/WinGet-silviuk.Lapdroid-blue)](https://github.com/microsoft/winget-pkgs/pull/449745)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Offline](https://img.shields.io/badge/Offline-100%25%20Bluetooth%20RFCOMM-success)](#)

> **Official Website**: [https://silviuk.github.io/lapdroid/](https://silviuk.github.io/lapdroid/)  
> **Offline Bluetooth Only**: Operates strictly over **Bluetooth RFCOMM**. No Wi-Fi, internet, or local area network connection is used or required.

---

## 1. Quick Install

### Windows (via WinGet)
```powershell
winget install silviuk.Lapdroid
```

### Windows (Direct Download)
- [**Lapdroid-Setup.exe**](https://github.com/silviuk/lapdroid/releases/download/v0.3/Lapdroid-Setup.exe) (Setup Installer)
- [**Lapdroid.exe**](https://github.com/silviuk/lapdroid/releases/download/v0.3/Lapdroid.exe) (Portable Executable)

### Android
- [**Lapdroid.apk**](https://github.com/silviuk/lapdroid/releases/download/v0.3/Lapdroid.apk) (Android APK)
- [**Lapdroid.aab**](https://github.com/silviuk/lapdroid/releases/download/v0.3/Lapdroid.aab) (Android App Bundle for Play Store)

---

## 2. Architecture Overview

### Why Lapdroid?
Under Windows 10/11, the native Windows Bluetooth driver stack (`BthModem.sys` / `bthprops.cpl`) only permits Windows to operate as a **Bluetooth HID Host** (receiver), not as a **HID Peripheral** (keyboard/mouse). Furthermore, the Windows Bluetooth Low Energy (BLE) GATT Server API strictly restricts advertising the standard HID over GATT Profile (HOGP, UUID `0x1812`).

**Lapdroid** bridges this gap natively without requiring external microcontrollers or unstable kernel drivers:
- **Windows Host App (`Lapdroid.exe`)**: Built with native Win32, `Winsock 2` Bluetooth, and low-level mouse & keyboard hooks. Captures keystrokes and relative touchpad movements and streams them over a low-latency RFCOMM binary socket. Includes full system tray integration with background execution.
- **Android Companion App (`Lapdroid.apk`)**: Listens over Bluetooth RFCOMM, renders a smooth floating mouse pointer overlay, and dispatches touch taps, drags, scrolls, and key events across any Android app via an `AccessibilityService`.

```
+------------------------------------+               +--------------------------------------+
|       Windows PC (Native Win32)     |               |             Android Phone            |
|                                    |               |                                      |
|  - WH_KEYBOARD_LL / WH_MOUSE_LL    |               |  - Lapdroid BluetoothBridgeService   |
|  - Win32 UI & System Tray Menu     |  Bluetooth    |  - CursorOverlayView (Floating View) |
|  - Winsock 2 RFCOMM (SPP)          | ------------> |  - InputAccessibilityService         |
|  - Low-latency binary protocol     |   (No Wi-Fi)  |    (dispatchGesture, click, type)    |
+------------------------------------+               +--------------------------------------+
```

---

## 3. Release Packages (v0.3.0)

Pre-built release packages are available in [`releases/v0.3/`](https://github.com/silviuk/lapdroid/releases/tag/v0.3) and on [GitHub Releases](https://github.com/silviuk/lapdroid/releases):

| File | Type | Size | Description |
| :--- | :--- | :--- | :--- |
| [**`Lapdroid-Setup.exe`**](https://github.com/silviuk/lapdroid/releases/download/v0.3/Lapdroid-Setup.exe) | Windows Setup Installer | ~830 KB | Professional NSIS installer: installs to Program Files, creates Start Menu & Desktop shortcuts, clean uninstaller. |
| [**`Lapdroid.exe`**](https://github.com/silviuk/lapdroid/releases/download/v0.3/Lapdroid.exe) | Standalone Executable | ~1.1 MB | Portable, fully self-contained 64-bit native Windows executable. Zero external runtime dependencies. |
| [**`Lapdroid.apk`**](https://github.com/silviuk/lapdroid/releases/download/v0.3/Lapdroid.apk) | Android Application (APK) | ~247 KB | Signed release APK with modern Android 14 (API 34) support, offline Bluetooth RFCOMM only. |
| [**`Lapdroid.aab`**](https://github.com/silviuk/lapdroid/releases/download/v0.3/Lapdroid.aab) | Android App Bundle | ~254 KB | Official signed Android App Bundle ready for Google Play Store upload. |
| [**`Lapdroid-v0.3.zip`**](https://github.com/silviuk/lapdroid/releases/download/v0.3/Lapdroid-v0.3.zip) | Complete Release Bundle | ~1.4 MB | Complete release ZIP bundle containing Windows installer, portable EXE, Android APK, and checksums. |
| [**`SHA256SUMS.txt`**](https://github.com/silviuk/lapdroid/releases/download/v0.3/SHA256SUMS.txt) | Verification Checksums | ~327 B | SHA-256 cryptographic hashes for integrity verification. |


---

## 3. Features & Improvements in v0.2

### Android App
- **Modern Security Compliance**:
  - Targets Android 14 (`targetSdkVersion 34`).
  - Uses `neverForLocation` flag on Bluetooth scan permission to eliminate location privacy warnings.
  - Signed with formal release certificate using APK Signature Scheme v2 and v3.
  - Zero internet permissions (`NO android.permission.INTERNET`).
- **Interactive First-Run Permission Setup**:
  - Automatically prompts runtime permissions (Bluetooth & Notifications) on initial startup.
  - Guided cards for Floating Overlay and Accessibility Service.
  - Defensive error handling prevents crashes if permissions have not yet been granted.
- **Gesture & Input Engine**:
  - Floating mouse pointer tracking trackpad swipes smoothly.
  - Left click -> tap / click.
  - Touch & drag support.
  - Right click -> Android **Back** action.
  - Middle click -> Android **Home** action.
  - Two-finger touchpad scroll -> natural page scrolling.
  - Keyboard typing into any focused input box.

### Windows App
- **Native Win32**: Ultra-fast, lightweight 214 KB binary.
- **System Tray Integration**:
  - Closing the window minimizes to tray (`WM_CLOSE`) for continuous background operation.
  - Right-click tray menu for instant controls (Toggle Capture, Home, Back, Recents, Disconnect, Exit).
- **Hotkey Toggle (`F12`)**:
  - Switch input seamlessly between Windows and your phone by tapping `F12`.
  - Mouse cursor trap and center-repositioning ensures touchpad swipes never run off screen boundaries.

---

## 4. Setup Guide

### Step 1: Pair over Bluetooth
1. On your Windows PC, go to **Settings > Bluetooth & devices > Add device**.
2. Pair your Android phone with your Windows PC.

### Step 2: Install & Open Lapdroid on Android
1. Download and install [`Lapdroid.apk`](https://github.com/silviuk/lapdroid/releases/download/v0.3/Lapdroid.apk).
2. Open the app: it will immediately prompt for Bluetooth permission.
3. Tap the prompts to enable **Floating Overlay** and **Accessibility Service**.
4. The status will display: **"Listening for Windows PC..."**.

### Step 3: Run Lapdroid on Windows
1. Install via WinGet (`winget install silviuk.Lapdroid`) or download [`Lapdroid-Setup.exe`](https://github.com/silviuk/lapdroid/releases/download/v0.3/Lapdroid-Setup.exe).
2. Click **Start Bluetooth Server**.
3. Status changes to **"Connected to Android"**.

### Step 4: Control Your Phone
1. Press **`F12`** to capture input.
2. Move your laptop touchpad and type on your physical keyboard to control your phone!
3. Press **`F12`** again anytime to release control back to Windows.

---

## 5. Building from Source

```bash
# Build both Windows and Android
./build_all.sh

# Or build Windows only:
make -C windows

# Or build Android APK & AAB only:
./android/build.sh
```

---

## 6. App Stores & Distribution

- **Official Website**: [https://silviuk.github.io/lapdroid/](https://silviuk.github.io/lapdroid/)
- **WinGet**: [PR #449745](https://github.com/microsoft/winget-pkgs/pull/449745) (`winget install silviuk.Lapdroid`)
- **F-Droid**: Recipe configured at [`metadata/com.antigravity.btbridge.yml`](metadata/com.antigravity.btbridge.yml) & [F-Droid Guide](docs/store-submissions/FDROID_SUBMISSION_GUIDE.md)
- **Google Play Store**: Bundle ready at [`bin/Lapdroid.aab`](https://github.com/silviuk/lapdroid/releases/download/v0.3/Lapdroid.aab) & [Play Store Guide](docs/store-submissions/GOOGLE_PLAY_STORE_GUIDE.md)
- **Microsoft Store**: [Windows Store Guide](docs/store-submissions/WINDOWS_STORE_GUIDE.md)

