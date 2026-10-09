# Microsoft Store (Windows Store) Submission Guide for Lapdroid

This guide details how to publish **Lapdroid** for Windows 10/11 to the **Microsoft Store** via Microsoft Partner Center.

---

## 1. Prerequisites

- **Microsoft Partner Center Account**: Registered at [Partner Center](https://partner.microsoft.com/dashboard) ($19 individual developer registration).
- **Installer URL**: Direct HTTPS link to `Lapdroid-Setup.exe` hosted on GitHub Releases:
  ```
  https://github.com/silviuk/lapdroid/releases/download/v0.3/Lapdroid-Setup.exe
  ```
- **Silent Install Parameters**: `/S` (standard NSIS silent switch).

---

## 2. Store Submission Steps in Partner Center

### Step 1: Create New App
1. Go to **Apps and games > Overview**.
2. Click **New product** -> Select **MSIX or PWA app** or **Traditional desktop app (Win32)**.
3. Reserve product name: `Lapdroid`.

### Step 2: Package / Installer Information (Win32 App Submission)
- **Installer Type**: EXE (`Nullsoft NSIS`)
- **Download URL**: `https://github.com/silviuk/lapdroid/releases/download/v0.3/Lapdroid-Setup.exe`
- **Silent Install Argument**: `/S`
- **Architectures**: `x64`
- **Minimum OS Version**: `Windows 10, version 1809 (Build 17763) or higher`

### Step 3: Properties & Category
- **Category**: `Developer tools` -> `Utilities & tools`
- **Display name**: `Lapdroid`
- **Publisher**: `Silviu Vlasceanu`
- **Support Contact URL**: `https://github.com/silviuk/lapdroid/issues`
- **Privacy Policy URL**: `https://silviuk.github.io/lapdroid/#privacy`
- **Website**: `https://silviuk.github.io/lapdroid/`

### Step 4: Store Listing & Assets
- **Short Description**:
  `Turn your laptop keyboard, touchpad, and stylus into an ultra-low latency Bluetooth controller for Android phones and tablets.`
- **Full Description**:
  ```
  Lapdroid bridges your Windows laptop and Android device over pure Bluetooth RFCOMM without requiring local Wi-Fi or internet connectivity.

  Features:
  - Universal keyboard forwarding (letters, digits, F1-F12, numpad, navigation keys)
  - Synchronized modifier states (Shift, AltGr, Control, CapsLock)
  - Desktop text editing shortcuts (Ctrl+A, Ctrl+C, Ctrl+V, Ctrl+X, Ctrl+Z, Ctrl+Y, word jumping/deletion)
  - High-precision touchpad cursor control with left, right, middle click and smooth scroll
  - Active stylus and digitizer pen forwarding
  - System tray execution with configurable hotkeys
  - 100% offline, privacy-first architecture
  ```
- **Logos & Screenshots**:
  - App Logo: `windows/res/app.ico` or `docs/icon.svg`
  - Screenshots: Phone / Desktop screenshots from `fastlane/metadata/android/en-US/images/`

### Step 5: Submit for Certification
Click **Submit to the Store**. Microsoft Store automated scans verify installer integrity and publish within 24-48 hours.
