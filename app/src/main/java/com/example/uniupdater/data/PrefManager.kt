package com.example.uniupdater.data

import android.content.Context
import android.content.SharedPreferences

class PrefManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "uniupdater_preferences"
        private const val KEY_JSON_URL = "custom_json_url"
        private const val KEY_SIM_ENABLED = "is_simulation_enabled"
        private const val KEY_SIM_DEVICE = "sim_device"
        private const val KEY_SIM_VERSION = "sim_version"
        private const val KEY_SIM_LATEST = "sim_latest"
        private const val KEY_WIFI_ONLY = "download_over_wifi"
        private const val KEY_AUTO_INSTALL = "auto_install_root"
        private const val KEY_MOCK_SOURCE = "mock_source"
        private const val KEY_CHECK_APP_UPDATES = "check_app_updates"
        private const val KEY_UPDATE_CHECK_INTERVAL = "update_check_interval"
        
        private const val KEY_FORCE_UPDATE = "force_update_available"
        private const val KEY_USE_LOCAL_JSON = "use_local_json"
        private const val KEY_LOCAL_JSON_CONTENT = "local_json_content"
        
        private const val KEY_TARGET_ROM_NAME = "target_rom_name"
        private const val KEY_TARGET_ROM_VERSION = "target_rom_version"
        private const val KEY_TARGET_ROM_LATEST = "target_rom_latest"
        private const val KEY_TARGET_CHANGELOG = "target_changelog"
        private const val KEY_TARGET_DOWNLOAD_URL = "target_download_url"
        private const val KEY_TARGET_FILE_SIZE = "target_file_size"
        private const val KEY_TARGET_SHA256 = "target_sha256"
        private const val KEY_TARGET_ROM_DATE = "target_rom_date"

        const val DEFAULT_JSON_URL = "https://raw.githubusercontent.com/mikey/uniupdater-ota/main/update.json"
        
        private const val DEFAULT_LOCAL_JSON = """{
  "rom_name": "UniOS v2.0 Stable",
  "rom_device": "socrates",
  "rom_version": "v2.0-Stable",
  "rom_latest": 20260615,
  "banner_img": "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=1080",
  "changelog_md": "# Local Mock OTA Update\n\nThis update is loaded from a **local JSON mock** stored directly inside the updater app settings!\n\n### What's New\n- Centered all layout components properly\n- Added GPL-3.0 copyleft licenses\n- Isolated simulation tools to ADB/Gestures\n- Removed residual Material 3 layout settings",
  "download_url": "https://raw.githubusercontent.com/mikey/uniupdater-ota/main/test.zip",
  "file_size": "45 MB",
  "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "rom_date": "June 15, 2026"
}"""
    }

    var customJsonUrl: String
        get() = prefs.getString(KEY_JSON_URL, DEFAULT_JSON_URL) ?: DEFAULT_JSON_URL
        set(value) = prefs.edit().putString(KEY_JSON_URL, value).apply()

    var isSimulationEnabled: Boolean
        get() = prefs.getBoolean(KEY_SIM_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SIM_ENABLED, value).apply()

    var simDevice: String
        get() = prefs.getString(KEY_SIM_DEVICE, "socrates") ?: "socrates"
        set(value) = prefs.edit().putString(KEY_SIM_DEVICE, value).apply()

    var simVersion: String
        get() = prefs.getString(KEY_SIM_VERSION, "1.0-Release") ?: "1.0-Release"
        set(value) = prefs.edit().putString(KEY_SIM_VERSION, value).apply()

    var simLatest: Long
        get() = prefs.getLong(KEY_SIM_LATEST, 20260501L)
        set(value) = prefs.edit().putLong(KEY_SIM_LATEST, value).apply()

    var downloadOverWifi: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    var autoInstallRoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_INSTALL, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_INSTALL, value).apply()

    var mockSource: String
        get() = prefs.getString(KEY_MOCK_SOURCE, "REMOTE_URL") ?: "REMOTE_URL"
        set(value) = prefs.edit().putString(KEY_MOCK_SOURCE, value).apply()

    var checkAppUpdates: Boolean
        get() = prefs.getBoolean(KEY_CHECK_APP_UPDATES, true)
        set(value) = prefs.edit().putBoolean(KEY_CHECK_APP_UPDATES, value).apply()

    var updateCheckInterval: String
        get() = prefs.getString(KEY_UPDATE_CHECK_INTERVAL, "DAILY") ?: "DAILY"
        set(value) = prefs.edit().putString(KEY_UPDATE_CHECK_INTERVAL, value).apply()

    var forceUpdateAvailable: Boolean
        get() = prefs.getBoolean(KEY_FORCE_UPDATE, false)
        set(value) = prefs.edit().putBoolean(KEY_FORCE_UPDATE, value).apply()

    var useLocalJsonMock: Boolean
        get() = prefs.getBoolean(KEY_USE_LOCAL_JSON, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_LOCAL_JSON, value).apply()

    var localJsonContent: String
        get() = prefs.getString(KEY_LOCAL_JSON_CONTENT, DEFAULT_LOCAL_JSON) ?: DEFAULT_LOCAL_JSON
        set(value) = prefs.edit().putString(KEY_LOCAL_JSON_CONTENT, value).apply()

    var targetRomName: String
        get() = prefs.getString(KEY_TARGET_ROM_NAME, "UniOS Simulated Update") ?: "UniOS Simulated Update"
        set(value) = prefs.edit().putString(KEY_TARGET_ROM_NAME, value).apply()

    var targetRomVersion: String
        get() = prefs.getString(KEY_TARGET_ROM_VERSION, "2.0-BetaSim") ?: "2.0-BetaSim"
        set(value) = prefs.edit().putString(KEY_TARGET_ROM_VERSION, value).apply()

    var targetRomLatest: Long
        get() = prefs.getLong(KEY_TARGET_ROM_LATEST, 20260603L)
        set(value) = prefs.edit().putLong(KEY_TARGET_ROM_LATEST, value).apply()

    var targetChangelog: String
        get() = prefs.getString(KEY_TARGET_CHANGELOG, "# Mock OTA Update (Local Sim)\n\n### Developer Note\nThis is a **Local Offline Simulated Update**. You don't need a running JSON web server to test this updater client!\n\n### Credits\n- Supercharged & Maintained by **DaDevMikey**!\n\n### Simulated Capabilities\n- Background downloading progress loop.\n- Scoped storage zip simulation.\n- Reboot-to-recovery scripting automation checks.") ?: ""
        set(value) = prefs.edit().putString(KEY_TARGET_CHANGELOG, value).apply()

    var targetDownloadUrl: String
        get() = prefs.getString(KEY_TARGET_DOWNLOAD_URL, "https://raw.githubusercontent.com/mikey/uniupdater-ota/main/test.zip") ?: "https://raw.githubusercontent.com/mikey/uniupdater-ota/main/test.zip"
        set(value) = prefs.edit().putString(KEY_TARGET_DOWNLOAD_URL, value).apply()

    var targetFileSize: String
        get() = prefs.getString(KEY_TARGET_FILE_SIZE, "15 MB") ?: "15 MB"
        set(value) = prefs.edit().putString(KEY_TARGET_FILE_SIZE, value).apply()

    var targetSha256: String
        get() = prefs.getString(KEY_TARGET_SHA256, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TARGET_SHA256, value).apply()

    var targetRomDate: String
        get() = prefs.getString(KEY_TARGET_ROM_DATE, "June 2026") ?: "June 2026"
        set(value) = prefs.edit().putString(KEY_TARGET_ROM_DATE, value).apply()
}
