# F-Droid Submission Guide for Lapdroid

Lapdroid is 100% open-source, contains zero proprietary tracking libraries, and requests **zero internet permissions**, making it an ideal candidate for official inclusion in the **F-Droid repository**.

---

## 1. Submission Artifacts Ready in Repository

1. **F-Droid Metadata Recipe**:
   Located at `metadata/com.antigravity.btbridge.yml`:
   ```yaml
   Categories:
     - Connectivity
     - System
   License: MIT
   AuthorName: Silviu Vlasceanu
   AuthorEmail: 2750371+silviuk@users.noreply.github.com
   SourceCode: https://github.com/silviuk/lapdroid
   IssueTracker: https://github.com/silviuk/lapdroid/issues
   WebSite: https://silviuk.github.io/lapdroid/
   AutoName: Lapdroid
   Summary: Laptop keyboard, trackpad & stylus input bridge for Android over Bluetooth
   ...
   ```

2. **Fastlane Metadata Structure**:
   Located at `fastlane/metadata/android/en-US/`:
   - `title.txt`
   - `short_description.txt`
   - `full_description.txt`
   - `changelogs/4.txt`
   - `images/phoneScreenshots/`

---

## 2. Steps to Submit to F-Droid

There are two official methods to submit Lapdroid to F-Droid:

### Option A: Open an RFP (Request For Packaging) Issue on GitLab (Easiest)
1. Go to the F-Droid issue tracker: [https://gitlab.com/fdroid/fdroiddata/-/issues/new](https://gitlab.com/fdroid/fdroiddata/-/issues/new).
2. Set issue template to: **New App Request (RFP)**.
3. Title: `RFP: com.antigravity.btbridge (Lapdroid)`
4. Body:
   ```markdown
   ### App Information
   - App Name: Lapdroid
   - Package Name: com.antigravity.btbridge
   - Source Code: https://github.com/silviuk/lapdroid
   - Website: https://silviuk.github.io/lapdroid/
   - License: MIT
   - Summary: Turn your laptop into a Bluetooth keyboard, trackpad & stylus for Android
   - Description: Low-latency Bluetooth input bridge between Windows PC and Android. 100% offline, zero network permissions.
   - Build Recipe: Ready in repository at `metadata/com.antigravity.btbridge.yml`
   - Fastlane Metadata: Present in repository at `fastlane/metadata/android/en-US/`
   ```
5. Click **Create issue**. F-Droid maintainers will review the recipe and add it to `fdroiddata`.

### Option B: Fork `fdroid/fdroiddata` and Submit a Merge Request
1. Fork [https://gitlab.com/fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata).
2. Add `metadata/com.antigravity.btbridge.yml` to the `metadata/` directory.
3. Commit and open a Merge Request titled: `New app: com.antigravity.btbridge (Lapdroid)`.
