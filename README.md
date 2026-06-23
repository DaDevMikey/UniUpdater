# UniUpdater - Open Source Custom ROM OTA Client

**UniUpdater** is a lightweight, open-source OTA (Over-The-Air) update client for Android custom ROMs, inspired by LineageOS and un1ca updaters. It enables ROM developers to deploy system updates seamlessly without requiring users to manually flash packages in recovery.

UniUpdater supports background download sync (via Foreground Service), automated ZIP package integrity validation (SHA-256), and automated flashing using root scripts.

*Proudly developed and maintained by **DaDevMikey**.*

---

##  How to Deploy for Your Custom ROM

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
  "rom_name": "MonsterRom",
  "rom_device": "Y2S",
  "rom_version": "v2.0-Stable",
  "rom_latest": 20260603,
  "banner_img": "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=1080",
  "changelog_md": "# MonsterROM v2.0-Stable\n\n### System Upgrades\n- Merged June 2026 Security Patch\n- Updated Linux kernel to v5.15.110\n\n### Fixes\n- Fixed Bluetooth audio stuttering on AAC codecs\n- Resolved fingerprint sensor delay",
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

### 2. Customizing App Properties & Settings via config.xml

Downstream ROM developers can customize all branding and behavior by editing a single resource file: [config.xml](file:///c:/Users/Mikey/Downloads/personal-repos/UniUpdater/app/src/main/res/values/config.xml).

#### Configuration Parameters:
- `app_name`: String specifying the application launcher name (e.g. `UniUpdater`).
- `rom_updater_title`: Title shown at the top of the update dashboard (e.g. `Software update`).
- `updater_for_rom`: Subtitle sentence shown under the title (e.g. `Updater for UniOS`).
- `default_json_url`: Default hosted JSON URL checking endpoint for OTA updates.
- `app_release_repo`: The GitHub repository owner/name (e.g., `DaDevMikey/UniUpdater`) where client app updates are queried.
- `enable_advanced_settings`: Boolean flag (`true`/`false`) to toggle visibility of Advanced Settings (custom update server, check frequency, system app flashing).
- `enable_mock_settings`: Boolean flag (`true`/`false`) to enable/disable developer tools (simulation activity access via 7 taps on ROM build version).
- `default_device_codename`: Default fallback device name (e.g. `socrates`).
- `default_rom_version`: Default fallback ROM version (e.g. `1.0-Release`).
- `default_rom_build_date`: Default fallback build date timestamp integer (e.g. `20260501`).

*Note: The developer attribution credit ("DaDevMikey") is locked in the code layouts to preserve credits for downstream forks.*

---

### 3. Deploying Client App Updates via GitHub

UniUpdater can query a GitHub releases API repository dynamically to check for newer client app versions. When an update is available, it is displayed as a premium One UI card at the top of the main dashboard with download buttons and parsed markdown changelogs.

#### Release Guidelines for ROM Developers:
1. **Repository Structure**: Set your fork repository path in `config.xml` under `<string name="app_release_repo">YourOrg/YourRepo</string>`.
2. **Release Tags**: Publish releases using semantic versioning tags prefixed with `v` (e.g., `v1.0.1`, `v1.1.0`). The updater compares the tag against the local version string `v1.0.0` to detect availability.
3. **Attachments**: Compile your customized updater using `./gradlew assembleRelease`, and upload/attach the signed release APK to the GitHub Release assets.
4. **Release Description**: Fill out the release body with markdown notes describing fixes and upgrades. They will render beautifully inside the client updater dashboard.

---

### 4. Developer Mocking & Local Testing

UniUpdater contains advanced developer mocking tools to test your layout designs, downloading mechanics, and install commands without having to build a custom ROM zip or run a web server.

1. **Launch Settings**: Tap the Settings gear icon in the top right.
2. **Offline Simulation Mode**: Set "Updater Simulation Mode" to **Offline Sim**. This bypasses HTTP queries and loads a local mock package.
3. **Download Simulation**: Clicking download on an Offline Sim update will run a fully animated download loop (speed, ETA, notifications) and create a dummy file on storage.
4. **Spoofing System Values**: Tap the **Spoof Device Properties & URL** button inside the Settings dialog (or tap the **ROM Build Version** row on the main screen **7 times**) to open the hidden **ROM Dev Simulator activity**. Here you can spoof:
   - Device Codename (to test incompatible blocks).
   - Build Version & Date (to toggle between "Up to Date" and "Update Available" states).
   - Custom URL testing.
5. **Local ZIP Updates**: You can now use **Install Local ZIP** / **Use Local ZIP** to import a custom ROM package directly from local storage and flash it using the same install flow as downloaded OTA files.

---

### 5. Root Automated Flashing Details

When root access is available, UniUpdater automates recovery flashing:
1. It creates TWRP / OrangeFox scripting directories:
   `mkdir -p /cache/recovery`
2. It writes commands to the OpenRecoveryScript path `/cache/recovery/openrecoveryscript`:
   ```bash
   install /sdcard/Android/data/com.universal.updater/files/Download/ota_update.zip
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
