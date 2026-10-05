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

## Release Assets

| Asset | Format | Size | SHA256 Checksum |
| :--- | :--- | :--- | :--- |
| **`Lapdroid-Setup.exe`** | Windows Setup Installer | ~216 KB | `83f56a5a475c5fc76fc61e05819c227585162bb2ff2c7ce079d882897552d422` |
| **`Lapdroid.exe`** | Standalone Windows Executable | ~214 KB | `9e2d9235e6ad2fc2041d72a111401bf6f20d700a5b65f81aa510a3acbd4af6ab` |
| **`Lapdroid.apk`** | Signed Android Application (v2/v3) | ~29 KB | `5545f746642ab28986ecddc44fe17bdb4f7cc2bdfafb2ec1df76a1223d01406c` |
| **`Lapdroid-v0.2.zip`** | All-in-one Release Archive | ~250 KB | `e9983740df33beedbf0e8cef04e39748300f587f950f8308dceb5399fad02483` |
| **`SHA256SUMS.txt`** | Integrity Hashes | ~1 KB | Verification checksums for all release binaries |
