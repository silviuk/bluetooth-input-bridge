# Google Play Store Submission Guide for Lapdroid

This guide outlines the complete step-by-step procedure to publish **Lapdroid** to the Google Play Store using the prepared release artifacts and metadata.

---

## 1. Prerequisites

- **Google Play Developer Account**: Registered at [Google Play Console](https://play.google.com/console) ($25 one-time registration fee).
- **Application Package**: `Lapdroid.aab` (Android App Bundle), generated and signed under `bin/Lapdroid.aab` and available in [Release v0.3](https://github.com/silviuk/lapdroid/releases/tag/v0.3/Lapdroid.aab).
- **Fastlane Metadata**: Pre-formatted under `fastlane/metadata/android/en-US/`.

---

## 2. Store Listing Details

| Field | Content |
|---|---|
| **App Name** | `Lapdroid` |
| **Short Description** | `Turn your laptop into a Bluetooth keyboard, trackpad & stylus for Android.` (67 / 80 chars) |
| **Full Description** | See `fastlane/metadata/android/en-US/full_description.txt` |
| **App Category** | Tools / Productivity |
| **Tags** | Tools, Remote Control, Bluetooth, Keyboard, Productivity |
| **App Icon** | 512x512 PNG, provided at `fastlane/metadata/android/en-US/images/icon.png` |
| **Feature Graphic** | 1024x500 PNG |
| **Screenshots** | Phone screenshots located in `fastlane/metadata/android/en-US/images/phoneScreenshots/` |

---

## 3. Privacy Policy & App Content Declarations

### Privacy Policy URL
```
https://silviuk.github.io/lapdroid/#privacy
```

### Data Safety Form Answers
- **Does your app collect or share user data?** Select **No**.
- Lapdroid does not request `android.permission.INTERNET`, cannot access the network, and does not store or transmit any personal data or analytics.

### Accessibility Service Declaration (Crucial for Play Store Review)
Google Play requires a prominent in-app disclosure and declaration form for apps using `AccessibilityService`:

- **Why does your app use AccessibilityService?**
  > *"Lapdroid uses the AccessibilityService API exclusively as a local input bridge to perform user-requested input actions (mouse click/tap, gestures, text entry, and hotkeys) forwarded in real-time from the user's paired Windows laptop over Bluetooth RFCOMM. Lapdroid does not collect, log, store, or transmit any user data or on-screen content."*
- **Video Demonstration URL**: Provide a short 30-second unlisted YouTube clip showing the user typing on their laptop keyboard and moving the touchpad to control their phone screen over Bluetooth.

---

## 4. Release Track Deployment

1. In Google Play Console, go to **Testing > Production** (or **Closed testing** for new developer accounts).
2. Click **Create new release**.
3. Upload `bin/Lapdroid.aab` (or download it directly from [v0.3 Release](https://github.com/silviuk/lapdroid/releases/tag/v0.3/Lapdroid.aab)).
4. Release Name: `0.3.0 (4)`.
5. Release Notes: Copy from `fastlane/metadata/android/en-US/changelogs/4.txt`.
6. Click **Review Release** and **Roll out to Production**.
