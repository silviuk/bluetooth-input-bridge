# Bluetooth Input Bridge for Samsung Galaxy S24 Ultra

Directly control your **Samsung Galaxy S24 Ultra** using your Windows laptop or PC keyboard and touchpad over **Bluetooth**.

> **Note on Connectivity**: Strictly operates over **Bluetooth RFCOMM**. No Wi-Fi or local area network connection is required or used.

---

## 1. Architecture Overview

### Why a Companion Android App?
Under Windows 10/11, the native Windows Bluetooth driver stack (`BthModem.sys` / `bthprops.cpl`) only permits Windows to operate as a **Bluetooth HID Host** (receiver), not as a **HID Peripheral** (keyboard/mouse). Furthermore, the Windows Bluetooth Low Energy (BLE) GATT Server API strictly restricts advertising the standard HID over GATT Profile (HOGP, UUID `0x1812`), rejecting user-mode registration.

To solve this natively without requiring specialized external microcontroller dongles or unstable kernel-mode filter drivers, this solution pairs a **native Win32 Windows application** with a high-performance **Android companion service**:
- **Windows App**: Built with pure native Windows APIs (`Win32`, `Winsock 2 Bluetooth`, `Shell_NotifyIcon`, low-level hooks). Captures keyboard keystrokes and relative touchpad movements and streams them over a low-latency RFCOMM binary socket.
- **Android App (`S24InputBridge.apk`)**: Listens over Bluetooth RFCOMM, renders a smooth floating mouse pointer overlay, and dispatches touch taps, drags, scrolls, and key events across any Android app via an `AccessibilityService`.

```
+------------------------------------+               +--------------------------------------+
|       Windows PC (Native Win32)     |               |    Samsung Galaxy S24 Ultra (Android)|
|                                    |               |                                      |
|  - WH_KEYBOARD_LL / WH_MOUSE_LL    |               |  - BluetoothBridgeService (RFCOMM)   |
|  - Win32 UI & System Tray Menu     |  Bluetooth    |  - CursorOverlayView (Floating View) |
|  - Winsock 2 RFCOMM (SPP)          | ------------> |  - InputAccessibilityService         |
|  - Low-latency binary protocol     |   (No Wi-Fi)  |    (dispatchGesture, click, type)    |
+------------------------------------+               +--------------------------------------+
```

---

## 2. Release Packages (v0.1)

Download pre-built release packages from [`releases/v0.1/`](file:///root/.gemini/antigravity-cli/scratch/bluetooth-input-bridge/releases/v0.1) or [`bin/`](file:///root/.gemini/antigravity-cli/scratch/bluetooth-input-bridge/bin):

| File | Type | Size | Description |
| :--- | :--- | :--- | :--- |
| [**`BluetoothInputBridge-Setup.exe`**](file:///root/.gemini/antigravity-cli/scratch/bluetooth-input-bridge/releases/v0.1/BluetoothInputBridge-Setup.exe) | Windows Setup Installer | ~216 KB | Full installer for Windows: installs to `Program Files`, adds Start Menu shortcuts, Desktop shortcut, and uninstaller. |
| [**`BluetoothInputBridge.exe`**](file:///root/.gemini/antigravity-cli/scratch/bluetooth-input-bridge/releases/v0.1/BluetoothInputBridge.exe) | Standalone Executable | ~214 KB | Portable, self-contained 64-bit native Windows executable. No .NET runtime or dependencies required. |
| [**`S24InputBridge.apk`**](file:///root/.gemini/antigravity-cli/scratch/bluetooth-input-bridge/releases/v0.1/S24InputBridge.apk) | Android Application (APK) | ~34 KB | Signed APK for Galaxy S24 Ultra (Android 14 / One UI compatible). |
| [**`BluetoothInputBridge-v0.1.zip`**](file:///root/.gemini/antigravity-cli/scratch/bluetooth-input-bridge/releases/v0.1/BluetoothInputBridge-v0.1.zip) | Complete Release Bundle | ~254 KB | ZIP archive containing Windows installer, portable exe, Android apk, and checksums. |
| [**`SHA256SUMS.txt`**](file:///root/.gemini/antigravity-cli/scratch/bluetooth-input-bridge/releases/v0.1/SHA256SUMS.txt) | Verification Checksums | ~1 KB | SHA-256 cryptographic hashes for integrity verification. |

See [**Release Notes v0.1**](file:///root/.gemini/antigravity-cli/scratch/bluetooth-input-bridge/releases/v0.1/RELEASE_NOTES.md) for full release details.

---

## 3. Features

### Windows Application
- **100% Native Windows APIs**:
  - `Winsock 2` Bluetooth RFCOMM (`AF_BTH`, `BTHPROTO_RFCOMM`, `WSASetService`).
  - Native Win32 windowing with high-DPI awareness (ComCtl32 v6).
  - Modern dark-themed dashboard.
- **System Tray Integration**:
  - Minimizes to system tray when the window is closed (`WM_CLOSE`), ensuring continuous operation.
  - Notification bubble alerts when minimized or connected.
  - **Right-Click Context Menu**:
    - *Open Window*
    - *Capture Input (Toggle - F12)*
    - *Android Home / Back / Recents*
    - *Connect / Disconnect*
    - *Exit Application*
- **Seamless Input Redirection**:
  - **Hotkey Toggle (`F12`)**: Instantly toggle control between Windows and Galaxy S24 Ultra.
  - **Mouse Trapping & Delta Tracking**: Centers mouse cursor smoothly during capture so trackpad swipes never hit screen edges.
  - **Sensitivity Adjustment**: On-the-fly touchpad sensitivity slider (0.5x to 3.0x).
  - **Direct Remote Actions**: One-click buttons for *Back*, *Home*, *Recent Apps*, *Notifications*, *Volume Up/Down*, and *Screen Lock*.

### Android Companion App
- **Zero Wi-Fi Permissions**: Does not request `android.permission.INTERNET`. 100% offline Bluetooth communication.
- **Floating Mouse Cursor**: Draws an arrow pointer overlay that tracks touchpad movements with precision.
- **System-Wide Input**:
  - **Left Click**: Taps / clicks buttons, icons, or links.
  - **Touch & Drag**: Supports dragging, selecting, and swiping.
  - **Right Click**: Acts as the Android **Back** button.
  - **Middle Click**: Acts as the Android **Home** button.
  - **Touchpad Two-Finger Scroll / Wheel**: Dispatches natural scroll gestures.
  - **Physical Keyboard Typing**: Injects characters, numbers, symbols, Enter, and Backspace directly into focused text fields.
  - **Foreground Service**: Ensures background persistence without being killed by One UI battery management.

---

## 4. Setup and Usage Instructions

### Step 1: Pair Devices over Bluetooth
1. On your Windows PC, go to **Settings > Bluetooth & devices > Add device**.
2. Put your **Galaxy S24 Ultra** into Bluetooth pairing mode and pair it with your Windows PC.

### Step 2: Install and Configure the Android App
1. Transfer [`S24InputBridge.apk`](file:///root/.gemini/antigravity-cli/scratch/bluetooth-input-bridge/bin/S24InputBridge.apk) to your S24 Ultra and install it.
2. Launch **S24 Input Bridge**.
3. Complete the quick 3-step permission setup on the main screen:
   - **Bluetooth**: Grant permission to connect.
   - **Floating Cursor Overlay**: Grant permission to draw over other apps.
   - **Accessibility Service**: Enable `S24 Input Bridge` in *Settings > Accessibility > Installed apps*.
4. The status will show **"Listening for Windows PC..."**.

### Step 3: Install and Run the Windows App
1. Run [`BluetoothInputBridge-Setup.exe`](file:///root/.gemini/antigravity-cli/scratch/bluetooth-input-bridge/bin/BluetoothInputBridge-Setup.exe) to install, or directly run [`BluetoothInputBridge.exe`](file:///root/.gemini/antigravity-cli/scratch/bluetooth-input-bridge/bin/BluetoothInputBridge.exe).
2. Choose **Server Mode** (default) or **Client Mode**:
   - In **Server Mode**: Click **"Start Bluetooth Server"**.
   - In **Client Mode**: Select your Galaxy S24 Ultra from the paired device list and click **"Connect"**.
3. Once connected, the status turns green: **"Connected to Galaxy S24 Ultra"**.

### Step 4: Control Your Phone
1. Press **`F12`** (or click **"Capture Input for S24 Ultra"**).
2. Your touchpad will now move the cursor on your S24 Ultra screen, and your laptop keyboard will type directly on your phone!
3. Press **`F12`** at any time to instantly release capture and return full control to Windows.
4. Closing the window minimizes it to the system tray so it remains active in the background.

---

## 5. Building from Source

To build both the Windows installer and Android APK from source on Linux:

```bash
# Build everything (Windows exe, setup installer, Android APK)
./build_all.sh

# Or build Windows only:
make -C windows

# Or build Android APK only:
./android/build.sh
```
