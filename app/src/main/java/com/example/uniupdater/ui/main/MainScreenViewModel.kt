package com.example.uniupdater.ui.main

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uniupdater.data.PrefManager
import com.example.uniupdater.data.model.RomUpdateInfo
import com.example.uniupdater.service.OtaDownloadService
import com.example.uniupdater.utils.RootUtils
import com.example.uniupdater.utils.SystemUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class MainScreenViewModel(context: Context) : ViewModel() {

    private val prefManager = PrefManager(context)
    private val client = OkHttpClient()
    private val jsonParser = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Idle)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _downloadProgressState = MutableStateFlow<OtaDownloadService.DownloadState>(OtaDownloadService.DownloadState.Idle)
    val downloadProgressState: StateFlow<OtaDownloadService.DownloadState> = _downloadProgressState.asStateFlow()

    private val _isRootAvailable = MutableStateFlow(false)
    val isRootAvailable: StateFlow<Boolean> = _isRootAvailable.asStateFlow()

    private val _isSystemApp = MutableStateFlow(RootUtils.isSystemApp(context))
    val isSystemApp: StateFlow<Boolean> = _isSystemApp.asStateFlow()

    private val _customJsonUrl = MutableStateFlow(prefManager.customJsonUrl)
    val customJsonUrl: StateFlow<String> = _customJsonUrl.asStateFlow()

    private val _updateCheckInterval = MutableStateFlow(prefManager.updateCheckInterval)
    val updateCheckInterval: StateFlow<String> = _updateCheckInterval.asStateFlow()

    private val _mockSource = MutableStateFlow(prefManager.mockSource)
    val mockSource: StateFlow<String> = _mockSource.asStateFlow()

    private val _checkAppUpdates = MutableStateFlow(prefManager.checkAppUpdates)
    val checkAppUpdates: StateFlow<Boolean> = _checkAppUpdates.asStateFlow()

    private val _downloadOverWifi = MutableStateFlow(prefManager.downloadOverWifi)
    val downloadOverWifi: StateFlow<Boolean> = _downloadOverWifi.asStateFlow()

    private val _autoInstallRoot = MutableStateFlow(prefManager.autoInstallRoot)
    val autoInstallRoot: StateFlow<Boolean> = _autoInstallRoot.asStateFlow()

    init {
        checkRootAccess()
        checkForUpdates()
        observeDownloadService()
    }

    fun refreshSettings() {
        _customJsonUrl.value = prefManager.customJsonUrl
        _updateCheckInterval.value = prefManager.updateCheckInterval
        _mockSource.value = prefManager.mockSource
        _checkAppUpdates.value = prefManager.checkAppUpdates
        _downloadOverWifi.value = prefManager.downloadOverWifi
        _autoInstallRoot.value = prefManager.autoInstallRoot
        checkForUpdates()
    }

    fun setCustomJsonUrl(value: String) {
        prefManager.customJsonUrl = value
        _customJsonUrl.value = value
        checkForUpdates()
    }

    fun setUpdateCheckInterval(value: String) {
        prefManager.updateCheckInterval = value
        _updateCheckInterval.value = value
    }

    fun setMockSource(value: String) {
        prefManager.mockSource = value
        _mockSource.value = value
        checkForUpdates()
    }

    fun setCheckAppUpdates(value: Boolean) {
        prefManager.checkAppUpdates = value
        _checkAppUpdates.value = value
    }

    fun setDownloadOverWifi(value: Boolean) {
        prefManager.downloadOverWifi = value
        _downloadOverWifi.value = value
    }

    fun setAutoInstallRoot(value: Boolean) {
        prefManager.autoInstallRoot = value
        _autoInstallRoot.value = value
    }

    fun checkAppUpdatesNow(context: Context, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/DaDevMikey/UniUpdater/releases/latest")
                    .header("User-Agent", "UniUpdater-OTA")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val regex = "\"tag_name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                    val match = regex.find(body)
                    val latestVersion = match?.groupValues?.get(1) ?: "v1.0.0"
                    
                    val currentVersion = "v1.0.0"
                    
                    withContext(Dispatchers.Main) {
                        if (latestVersion != currentVersion && latestVersion.isNotEmpty()) {
                            onResult("New UniUpdater version available: $latestVersion\n\nDownload: https://github.com/DaDevMikey/UniUpdater/releases/latest")
                        } else {
                            onResult("UniUpdater app is up to date ($currentVersion).\nCredits: DaDevMikey")
                        }
                    }
                } else {
                    throw Exception("Server returned HTTP ${response.code}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult("Failed to check for app updates: ${e.message}\nCredits: DaDevMikey")
                }
            }
        }
    }

    private fun checkRootAccess() {
        viewModelScope.launch(Dispatchers.IO) {
            val root = RootUtils.isRootAvailable()
            _isRootAvailable.value = root
        }
    }

    fun installAsSystem(context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val apkPath = context.packageCodePath
            val success = RootUtils.installAsSystemApp(apkPath)
            withContext(Dispatchers.Main) {
                if (success) {
                    _isSystemApp.value = true
                    onSuccess()
                } else {
                    onError("Failed to install as system app. Ensure root access is allowed and /system is writeable.")
                }
            }
        }
    }

    fun checkForUpdates() {
        _uiState.value = MainUiState.Checking

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isSim = prefManager.isSimulationEnabled
                val currentDevice = if (isSim) prefManager.simDevice else SystemUtils.getDeviceCodename()
                val currentBuildDate = if (isSim) prefManager.simLatest else SystemUtils.getBuildDateUtc()
                val currentVersion = if (isSim) prefManager.simVersion else SystemUtils.getBuildVersion()

                val updateInfo = if (isSim) {
                    if (prefManager.useLocalJsonMock) {
                        jsonParser.decodeFromString<RomUpdateInfo>(prefManager.localJsonContent)
                    } else {
                        RomUpdateInfo(
                            rom_name = prefManager.targetRomName,
                            rom_device = currentDevice,
                            rom_version = prefManager.targetRomVersion,
                            rom_latest = prefManager.targetRomLatest,
                            banner_img = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=1080",
                            changelog_md = prefManager.targetChangelog,
                            download_url = prefManager.targetDownloadUrl,
                            file_size = prefManager.targetFileSize,
                            sha256 = prefManager.targetSha256,
                            rom_date = prefManager.targetRomDate
                        )
                    }
                } else {
                    val jsonUrl = prefManager.customJsonUrl
                    val request = Request.Builder().url(jsonUrl).build()
                    val response = client.newCall(request).execute()

                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code} error fetching update info")
                    }

                    val body = response.body?.string() ?: throw Exception("Empty response body")
                    jsonParser.decodeFromString<RomUpdateInfo>(body)
                }

                val isDeviceCompatible = if (isSim && prefManager.forceUpdateAvailable) true else {
                    updateInfo.rom_device.equals(currentDevice, ignoreCase = true)
                }
                val isUpdateAvailable = if (isSim && prefManager.forceUpdateAvailable) true else {
                    isDeviceCompatible && (updateInfo.rom_latest > currentBuildDate)
                }

                withContext(Dispatchers.Main) {
                    _uiState.value = MainUiState.UpdateChecked(
                        info = updateInfo,
                        isUpdateAvailable = isUpdateAvailable,
                        isDeviceCompatible = isDeviceCompatible,
                        localDevice = currentDevice,
                        localVersion = currentVersion,
                        localBuildDate = currentBuildDate
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = MainUiState.Error(e.message ?: "Unknown error checking for updates")
                }
            }
        }
    }

    private fun observeDownloadService() {
        viewModelScope.launch {
            OtaDownloadService.downloadState.collectLatest { state ->
                _downloadProgressState.value = state
            }
        }
    }

    fun startOtaDownload(context: Context, info: RomUpdateInfo) {
        val intent = Intent(context, OtaDownloadService::class.java).apply {
            action = OtaDownloadService.ACTION_START
            putExtra(OtaDownloadService.EXTRA_URL, info.download_url)
            putExtra(OtaDownloadService.EXTRA_SHA256, info.sha256)
            putExtra(OtaDownloadService.EXTRA_ROM_NAME, info.rom_name)
        }
        context.startForegroundService(intent)
    }

    fun cancelOtaDownload(context: Context) {
        OtaDownloadService.cancelDownload(context)
    }

    fun triggerRootInstall(filePath: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) {
                withContext(Dispatchers.Main) { onError("ROM update file not found") }
                return@launch
            }

            val success = RootUtils.installOtaViaRecovery(filePath)
            withContext(Dispatchers.Main) {
                if (success) {
                    onSuccess()
                } else {
                    onError("Failed to execute root recovery flash scripts")
                }
            }
        }
    }
}

sealed interface MainUiState {
    object Idle : MainUiState
    object Checking : MainUiState
    data class UpdateChecked(
        val info: RomUpdateInfo,
        val isUpdateAvailable: Boolean,
        val isDeviceCompatible: Boolean,
        val localDevice: String,
        val localVersion: String,
        val localBuildDate: Long
    ) : MainUiState
    data class Error(val message: String) : MainUiState
}
