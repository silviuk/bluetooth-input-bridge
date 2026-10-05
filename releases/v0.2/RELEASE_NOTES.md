# Lapdroid v0.2.0 Release Notes

## What's New in v0.2.0

### 1. Renamed to Lapdroid & Generalized for All Android Phones
The project and all apps are now officially named **Lapdroid** (Laptop + Android).
- Windows application: `Lapdroid.exe`
- Windows installer: `Lapdroid-Setup.exe`
- Android application: `Lapdroid.apk`
- All UI text, tooltips, pairing filters, and notifications generalized to support all Android phones seamlessly.

### 2. Adaptive Dark & Light Theme
- Automatically detects Windows theme via `AppsUseLightTheme` registry setting and dynamically updates when changed in Windows Settings (`WM_SETTINGCHANGE`).
- **Dark Mode**: Fluent Dark `#202020` background, `#2D2D2D` card surfaces, crisp `#F2F2F2` text, `#60CDFF` accent, and DWM immersive dark mode title bar.
- **Light Mode**: Fluent Light `#F3F3F3` background, `#FFFFFF` card surfaces, crisp high-contrast `#1E1E1E` text, and `#0067C0` accent.
- Resolved broken black-on-blue text rendering.

### 3. Reliable System Tray & Single-Instance Service
- High-DPI multi-resolution icon (16x16, 32x32) registered with modern `NOTIFYICON_VERSION_4` shell notification.
- Listens for `TaskbarCreated` message to automatically restore the tray icon if Windows Explorer restarts.
- Strict single-instance enforcement via session-local mutex (`Local\Lapdroid_SingleInstance_Mutex`). Launching a second instance cleanly unhides and brings the existing window to the front, preventing ghost tray icons or duplicate background processes.

### 4. Modernized Android Security & First-Run Permission Flow
- **Android 14 (API 34) Native**: Explicitly targets API 34 with `minSdkVersion=26`.
- **Bluetooth Permissions with `neverForLocation`**: Declares `android.permission.BLUETOOTH_SCAN` with `android:usesPermissionFlags="neverForLocation"`, eliminating location security warnings.
- **Formal Release Keystore**: Signed with a formal release certificate using modern APK Signature Scheme v2 and v3.
- **Strict Network Isolation**: Declares zero internet permissions (`NO android.permission.INTERNET`).
- **Interactive First-Run Permission Setup**: Guided step-by-step setup cards and defensive guards preventing crashes before permissions are granted.

### 5. Fully Static Windows Binaries (Zero Missing DLL Errors)
- Windows binaries are compiled with `-static -static-libgcc -static-libstdc++`, bundling all runtime routines directly into the executable.
- Eliminates any dependency on `libgcc_s_seh-1.dll`, `libstdc++-6.dll`, or `libwinpthread-1.dll`. Runs out-of-the-box on standard Windows installations.

---

## Release Assets

| Asset | Format | Size | SHA256 Checksum |
| :--- | :--- | :--- | :--- |
| **`Lapdroid-Setup.exe`** | Windows Setup Installer | ~516 KB | `94e4120d7ab02356493bc54ef66b2fddeb7fb6bf980eec315f56a70aab739aa6` |
| **`Lapdroid.exe`** | Standalone Windows Executable | ~1.1 MB | `6c40137cc9a947c7ef3ba6921b02b8b69ae05e604daab20376f77bd2962e45f1` |
| **`Lapdroid.apk`** | Signed Android Application (v2/v3) | ~29 KB | `b84fce112e88c71d4c7af42f40894a3557e59be5262094a73f605e9f9f8cc14b` |
| **`Lapdroid-v0.2.zip`** | All-in-one Release Archive | ~813 KB | `ec009efb9f50fa6ea8858068b909f09eb8fbbe11bc5673815a251d506a4dd29f` |
| **`SHA256SUMS.txt`** | Integrity Hashes | ~1 KB | Verification checksums for all release binaries |
