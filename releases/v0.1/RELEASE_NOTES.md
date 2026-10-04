# Release Notes - v0.1

## Overview
**Bluetooth Input Bridge v0.1** is the first official release enabling direct control of a Samsung Galaxy S24 Ultra (or other Android device) using a Windows PC or laptop keyboard and touchpad over Bluetooth.

Strictly communicates over **Bluetooth RFCOMM** with **zero Wi-Fi networking required or used**.

---

## Release Assets

| Asset | Format | Size | SHA256 Checksum |
| :--- | :--- | :--- | :--- |
| **`BluetoothInputBridge-Setup.exe`** | Windows Setup Installer (NSIS) | ~216 KB | `95c5830119dcee22707863904802d7afe0f54b9f2e5b282bb5644a7f39c56c3d` |
| **`BluetoothInputBridge.exe`** | Standalone Native Executable | ~214 KB | `39c08b7392bf570f559e9d4c33139931aebde01275dc6dc2782f110c309e9c1d` |
| **`S24InputBridge.apk`** | Signed Android Application (v1/v2/v3) | ~34 KB | `d6eb6646d1b23ed5cd2dd5a8533b45eb865f4d743d82d891f67f7578b61a1cc5` |
| **`BluetoothInputBridge-v0.1.zip`** | All-in-one ZIP Archive | ~254 KB | `311c5a8eb8de6fd3c73dfd9e4e9c94f9ace4c846055a7c8c591ba39bf6d87baa` |

---

## Key Features in v0.1

### Windows Application
- **100% Native Win32 Architecture**: Built purely with C++, Win32, and Winsock 2 Bluetooth APIs. No .NET runtime, Electron, or heavy framework required.
- **System Tray Integration**:
  - Closing the application window minimizes it to the system tray (`WM_CLOSE`), ensuring uninterrupted background operation.
  - Custom right-click context menu (Open, Toggle Capture, Navigation, Connect/Disconnect, Exit).
  - Tray notification balloons indicating connection state and capture status.
- **Input Capture Engine**:
  - Global low-level mouse hook (`WH_MOUSE_LL`) and keyboard hook (`WH_KEYBOARD_LL`).
  - Relative touchpad delta tracking with seamless center-trapping so trackpad swipes never hit monitor edges.
  - Hotkey toggle (`F12`): Instantly switch control between Windows and the phone.
  - Real-time sensitivity slider (0.5x to 3.0x).
  - Quick action buttons (Home, Back, Recents, Notifications, Volume, Lock).
- **Setup Installer**:
  - Professional NSIS installer supporting Program Files installation, Start Menu shortcuts, Desktop shortcut, and clean uninstallation.

### Android Companion App (`S24InputBridge.apk`)
- **Zero Internet Permissions**: Does not request or declare `android.permission.INTERNET`.
- **Floating Mouse Cursor Overlay**: Smooth vector pointer tracking touchpad movements across any app.
- **Accessibility Service Integration**:
  - Dispatches taps and clicks at cursor coordinates (`dispatchGesture`).
  - Drag and swipe support.
  - Right-click mapped to Android **Back**.
  - Middle-click mapped to Android **Home**.
  - Touchpad two-finger scroll mapped to fling/scroll gestures.
  - Physical keyboard typing injected into active input fields.
- **Foreground Service**: Ensures background persistence without suspension by Samsung One UI battery optimization.

---

## Quick Start
1. Pair your Galaxy S24 Ultra and Windows PC in **Windows Settings > Bluetooth & devices**.
2. Install [`S24InputBridge.apk`](./S24InputBridge.apk) on your phone and enable the 3 permissions shown.
3. Install [`BluetoothInputBridge-Setup.exe`](./BluetoothInputBridge-Setup.exe) on Windows and launch the app.
4. Click **Start Bluetooth Server**, then press **F12** to capture/release input.
