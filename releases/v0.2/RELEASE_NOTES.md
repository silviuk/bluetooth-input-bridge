# Lapdroid v0.2.0 Release Notes

## What's New in v0.2.0

### 1. Renamed to Lapdroid
The project and all apps are now officially named **Lapdroid** (Laptop + Android).
- Windows application: `Lapdroid.exe`
- Windows installer: `Lapdroid-Setup.exe`
- Android application: `Lapdroid.apk`

### 2. Modernized to Avoid Security & Play Protect Warnings
- **Android 14 (API 34) Native**: Explicitly targets API 34 with `minSdkVersion=26`.
- **Bluetooth Permissions with `neverForLocation`**: Declares `android.permission.BLUETOOTH_SCAN` with `android:usesPermissionFlags="neverForLocation"`, informing Android that Bluetooth is strictly for device communication and not location tracking (eliminating location security warnings).
- **Formal Release Keystore**: Signed with a formal release certificate (`CN=Lapdroid, OU=Mobile, O=Lapdroid Open Source, L=Mountain View, ST=California, C=US`) using modern APK Signature Scheme v2 and v3 instead of generic test/debug keys.
- **Strict Network Isolation**: Declares zero internet permissions (`NO android.permission.INTERNET`).

### 3. First-Run Permission Flow & Zero-Crash Architecture
- **Automatic First-Run Prompt**: Asks for Bluetooth and Notification permissions immediately upon first launch.
- **Guided Setup Cards**: Step-by-step guidance for granting:
  1. Bluetooth & Notifications
  2. Floating Mouse Cursor Overlay
  3. Accessibility Service Engine
- **Crash Prevention**: Completely eliminated crashes that previously occurred if the app was launched without permissions:
  - `CursorOverlayView` checks `Settings.canDrawOverlays` before calling `WindowManager.addView`.
  - `BluetoothBridgeService` checks `BLUETOOTH_CONNECT` before initiating RFCOMM listening.
  - Safe exception wrappers throughout the lifecycle.

---

### 4. Fully Static Windows Binaries (Zero Missing DLL Errors)
- Windows binaries are now compiled with `-static -static-libgcc -static-libstdc++`, bundling all runtime routines directly into the executable.
- Eliminates any dependency on `libgcc_s_seh-1.dll`, `libstdc++-6.dll`, or `libwinpthread-1.dll`. Runs out-of-the-box on any standard Windows PC.

---

## Release Assets

| Asset | Format | Size | SHA256 Checksum |
| :--- | :--- | :--- | :--- |
| **`Lapdroid-Setup.exe`** | Windows Setup Installer | ~503 KB | `b08e26a492081cd7b55405187fa59b61eae8b1a9a3f907c39407459e74c91389` |
| **`Lapdroid.exe`** | Standalone Windows Executable | ~1.1 MB | `9a986184284d7bbde935de7e2a20242afcf7104bde03a470e631e7d971d9e8c4` |
| **`Lapdroid.apk`** | Signed Android Application (v2/v3) | ~29 KB | `5545f746642ab28986ecddc44fe17bdb4f7cc2bdfafb2ec1df76a1223d01406c` |
| **`Lapdroid-v0.2.zip`** | All-in-one Release Archive | ~813 KB | `2cf8345983e845c8a63966e61462762fc9fd46178fe8ff0b9362aab9bb7ed6f7` |
| **`SHA256SUMS.txt`** | Integrity Hashes | ~1 KB | Verification checksums for all release binaries |
