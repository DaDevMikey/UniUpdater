# UniUpdater - Open Source Custom ROM OTA Client

**UniUpdater** is a lightweight, open-source OTA (Over-The-Air) update client for Android custom ROMs, inspired by LineageOS and un1ca updaters. It enables ROM developers to deploy system updates seamlessly without requiring users to manually flash packages in recovery.

UniUpdater supports background download sync (via Foreground Service), automated ZIP package integrity validation (SHA-256), and automated flashing using root scripts.

*Proudly developed and maintained by **DaDevMikey**.*

---

## 📖 How to Deploy for Your Custom ROM

Deploying UniUpdater for your own custom ROM consists of three simple steps:
1. Host an update JSON config file on your server (or GitHub).
2. Configure the default URL in the app code.
3. Compile and sign the APK.

---

### 1. Hosted JSON Schema Format

You must host a JSON file containing the update metadata. The app fetches this file to determine compatibility and update status. 

#### JSON Structure:
```json
{
  "rom_name": "AntigravityOS",
  "rom_device": "socrates",
  "rom_version": "v2.0-Stable",
  "rom_latest": 20260603,
  "banner_img": "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=1080",
  "changelog_md": "# AntigravityOS v2.0-Stable\n\n### System Upgrades\n- Merged June 2026 Security Patch\n- Updated Linux kernel to v5.15.110\n\n### Fixes\n- Fixed Bluetooth audio stuttering on AAC codecs\n- Resolved fingerprint sensor delay",
  "download_url": "https://raw.githubusercontent.com/mikey/uniupdater-ota/main/test.zip",
  "file_size": "15 MB",
  "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "rom_date": "June 3, 2026"
}
```

#### Field Explanations:
- `rom_name`: The display name of your custom ROM.
- `rom_device`: Target device codename (e.g. `socrates`, `alioth`). The client matches this with the local device `ro.product.device` property to prevent cross-flashing.
- `rom_version`: User-facing version string.
- `rom_latest`: Build number or timestamp (represented as a `Long` e.g. `20260603`). The client checks if this value is strictly greater than the local system build date/timestamp to determine update availability.
- `banner_img`: URL to a splash banner (rendered as a cover header in the app).
- `changelog_md`: Changelog text in Markdown format. Renders headings (`#`, `##`, `###`), bullets (`-`, `*`), and bold (`**text**`) natively.
- `download_url`: Direct link to download the ROM flashable ZIP.
- `file_size`: Display size of the package.
- `sha256`: SHA-256 hash of the ZIP file. Leave empty if you wish to bypass post-download checksum verification.
- `rom_date`: Release date text.

---

### 2. Configure the Default URL in Code

To set your default JSON endpoint:
1. Open the [PrefManager.kt](app/src/main/java/com/example/uniupdater/data/PrefManager.kt) file.
2. Locate the `DEFAULT_JSON_URL` constant under the companion object:
   ```kotlin
   const val DEFAULT_JSON_URL = "https://yourdomain.com/ota/update.json"
   ```
3. Replace it with your hosted JSON configuration URL.

---

### 3. Developer Mocking & Local Testing

UniUpdater contains advanced developer mocking tools to test your layout designs, downloading mechanics, and install commands without having to build a custom ROM zip or run a web server.

1. **Launch Settings**: Tap the Settings gear icon in the top right.
2. **Offline Simulation Mode**: Set "Updater Simulation Mode" to **Offline Sim**. This bypasses HTTP queries and loads a local mock package.
3. **Download Simulation**: Clicking download on an Offline Sim update will run a fully animated download loop (speed, ETA, notifications) and create a dummy file on storage.
4. **Spoofing System Values**: Tap the **Spoof Device Properties & URL** button inside the Settings dialog (or tap the **ROM Build Version** row on the main screen **7 times**) to open the hidden **ROM Dev Simulator activity**. Here you can spoof:
   - Device Codename (to test incompatible blocks).
   - Build Version & Date (to toggle between "Up to Date" and "Update Available" states).
   - Custom URL testing.

---

### 4. Root Automated Flashing Details

When root access is available, UniUpdater automates recovery flashing:
1. It creates TWRP / OrangeFox scripting directories:
   `mkdir -p /cache/recovery`
2. It writes commands to the OpenRecoveryScript path `/cache/recovery/openrecoveryscript`:
   ```bash
   install /sdcard/Android/data/com.example.uniupdater/files/Download/ota_update.zip
   reboot
   ```
3. It writes fallback command parameters to `/cache/recovery/command`.
4. It reboots the device directly to recovery via `reboot recovery` shell execution. The recovery then reads the script, flashes the update, and automatically reboots the system.

If root is not available, the app presents a clean popup guide detailing how users can boot to recovery and locate the downloaded ZIP in scoped storage.

---

## 🛠️ Build Requirements

- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: API 36 (Android 16)
- **Tooling**: Gradle 9.x & Kotlin 2.x

To compile a test release package, run:
```bash
./gradlew assembleRelease
```
