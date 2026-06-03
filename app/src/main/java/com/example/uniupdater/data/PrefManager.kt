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
        private const val KEY_THEME = "selected_theme"
        private const val KEY_WIFI_ONLY = "download_over_wifi"
        private const val KEY_AUTO_INSTALL = "auto_install_root"
        private const val KEY_MOCK_SOURCE = "mock_source"
        private const val KEY_CHECK_APP_UPDATES = "check_app_updates"

        const val DEFAULT_JSON_URL = "https://raw.githubusercontent.com/mikey/uniupdater-ota/main/update.json"
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

    var selectedTheme: String
        get() = prefs.getString(KEY_THEME, "MATERIAL3") ?: "MATERIAL3"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

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
}
