package com.universal.updater.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universal.updater.data.PrefManager
import com.universal.updater.data.model.RomUpdateInfo
import com.universal.updater.service.OtaDownloadService
import com.universal.updater.utils.RootUtils
import com.universal.updater.utils.SystemUtils
import com.universal.updater.utils.UpdateEngineWrapper
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
import java.util.UUID
import kotlinx.coroutines.delay

import com.universal.updater.R

class MainScreenViewModel(context: Context) : ViewModel() {

    private val prefManager = PrefManager(context)
    private val client = OkHttpClient()
    private val jsonParser = Json { ignoreUnknownKeys = true }

    val defaultJsonUrl = prefManager.defaultJsonUrl
    val appReleaseRepo = prefManager.appReleaseRepo

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

    private val _appUpdateState = MutableStateFlow<AppUpdateState>(AppUpdateState.UpToDate)
    val appUpdateState: StateFlow<AppUpdateState> = _appUpdateState.asStateFlow()

    private val _appUpdateProgress = MutableStateFlow<Float?>(null)
    val appUpdateProgress: StateFlow<Float?> = _appUpdateProgress.asStateFlow()

    private val _mockSource = MutableStateFlow(prefManager.mockSource)
    val mockSource: StateFlow<String> = _mockSource.asStateFlow()

    private val _checkAppUpdates = MutableStateFlow(prefManager.checkAppUpdates)
    val checkAppUpdates: StateFlow<Boolean> = _checkAppUpdates.asStateFlow()

    private val _downloadOverWifi = MutableStateFlow(prefManager.downloadOverWifi)
    val downloadOverWifi: StateFlow<Boolean> = _downloadOverWifi.asStateFlow()

    private val _autoInstallRoot = MutableStateFlow(prefManager.autoInstallRoot)
    val autoInstallRoot: StateFlow<Boolean> = _autoInstallRoot.asStateFlow()

    private val _enableAbUpdateEngine = MutableStateFlow(prefManager.enableAbUpdateEngine)
    val enableAbUpdateEngine: StateFlow<Boolean> = _enableAbUpdateEngine.asStateFlow()

    private val _autoDeleteAfterInstall = MutableStateFlow(prefManager.autoDeleteAfterInstall)
    val autoDeleteAfterInstall: StateFlow<Boolean> = _autoDeleteAfterInstall.asStateFlow()

    private val _abUpdateProgress = MutableStateFlow<Float?>(null)
    val abUpdateProgress: StateFlow<Float?> = _abUpdateProgress.asStateFlow()
    
    private val _abUpdateStatus = MutableStateFlow<String?>(null)
    val abUpdateStatus: StateFlow<String?> = _abUpdateStatus.asStateFlow()

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
        _enableAbUpdateEngine.value = prefManager.enableAbUpdateEngine
        _autoDeleteAfterInstall.value = prefManager.autoDeleteAfterInstall
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

    fun setEnableAbUpdateEngine(value: Boolean) {
        prefManager.enableAbUpdateEngine = value
        _enableAbUpdateEngine.value = value
    }

    fun setAutoDeleteAfterInstall(value: Boolean) {
        prefManager.autoDeleteAfterInstall = value
        _autoDeleteAfterInstall.value = value
    }

    fun checkAppUpdatesNow(context: Context, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/$appReleaseRepo/releases/latest")
                    .header("User-Agent", "UniUpdater-OTA")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val tagMatch = "\"tag_name\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)
                    val latestVersion = tagMatch?.groupValues?.get(1) ?: "v1.0.1"

                    val bodyMatch = "\"body\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)
                    val rawChangelog = bodyMatch?.groupValues?.get(1) ?: ""
                    val changelog = rawChangelog.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\\"", "\"")

                    val htmlUrlMatch = "\"html_url\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)
                    val downloadUrl = htmlUrlMatch?.groupValues?.get(1) ?: "https://github.com/$appReleaseRepo/releases"

                    val apkMatch = "\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"".toRegex().find(body)
                    val apkUrl = apkMatch?.groupValues?.get(1)

                    val currentVersion = "v1.0.1"
                    
                    withContext(Dispatchers.Main) {
                        if (latestVersion != currentVersion && latestVersion.isNotEmpty()) {
                            _appUpdateState.value = AppUpdateState.UpdateAvailable(
                                version = latestVersion,
                                changelog = changelog,
                                downloadUrl = downloadUrl,
                                apkUrl = apkUrl
                            )
                            onResult("New UniUpdater version available: $latestVersion")
                        } else {
                            _appUpdateState.value = AppUpdateState.UpToDate
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
                // ROM Update checking
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
                    val jsonUrl = prefManager.customJsonUrl.replace("{device}", currentDevice)
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

            // Client app update checking (if enabled)
            if (prefManager.checkAppUpdates) {
                try {
                    val request = Request.Builder()
                        .url("https://api.github.com/repos/$appReleaseRepo/releases/latest")
                        .header("User-Agent", "UniUpdater-OTA")
                        .build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val tagMatch = "\"tag_name\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)
                        val latestVersion = tagMatch?.groupValues?.get(1) ?: "v1.0.1"

                        val bodyMatch = "\"body\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)
                        val rawChangelog = bodyMatch?.groupValues?.get(1) ?: ""
                        val changelog = rawChangelog.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\\"", "\"")

                        val htmlUrlMatch = "\"html_url\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)
                        val downloadUrl = htmlUrlMatch?.groupValues?.get(1) ?: "https://github.com/$appReleaseRepo/releases"

                        val apkMatch = "\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"".toRegex().find(body)
                        val apkUrl = apkMatch?.groupValues?.get(1)

                        val currentVersion = "v1.0.1"
                        if (latestVersion != currentVersion && latestVersion.isNotEmpty()) {
                            _appUpdateState.value = AppUpdateState.UpdateAvailable(
                                version = latestVersion,
                                changelog = changelog,
                                downloadUrl = downloadUrl,
                                apkUrl = apkUrl
                            )
                        } else {
                            _appUpdateState.value = AppUpdateState.UpToDate
                        }
                    }
                } catch (e: Exception) {
                    // Silent fail
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

    fun pauseOtaDownload(context: Context) {
        OtaDownloadService.pauseDownload(context)
    }

    fun resumeOtaDownload(context: Context, info: RomUpdateInfo) {
        OtaDownloadService.resumeDownload(context, info.download_url, info.sha256, info.rom_name)
    }

    fun exportOtaUpdate(context: Context, filePath: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sourceFile = File(filePath)
                if (!sourceFile.exists()) {
                    withContext(Dispatchers.Main) { onError("Update file not found") }
                    return@launch
                }
                
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                
                val destFile = File(downloadsDir, sourceFile.name)
                sourceFile.copyTo(destFile, overwrite = true)
                
                withContext(Dispatchers.Main) { onSuccess(destFile.absolutePath) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Failed to export update") }
            }
        }
    }

    fun deleteOtaUpdate(filePath: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(filePath)
            val deleted = if (file.exists()) file.delete() else false
            if (deleted) {
                _downloadProgressState.value = OtaDownloadService.DownloadState.Idle
            }
            withContext(Dispatchers.Main) { onResult(deleted) }
        }
    }

    fun triggerRootInstall(filePath: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) {
                withContext(Dispatchers.Main) { onError("ROM update file not found") }
                return@launch
            }

            if (prefManager.enableAbUpdateEngine) {
                withContext(Dispatchers.Main) {
                    _abUpdateStatus.value = "Initializing UpdateEngine..."
                    _abUpdateProgress.value = 0f
                }
                
                val listener = object : UpdateEngineWrapper.StatusListener {
                    override fun onStatusUpdate(status: Int, percent: Float) {
                        val statusText = when (status) {
                            UpdateEngineWrapper.UPDATE_STATUS_DOWNLOADING -> "Installing..."
                            UpdateEngineWrapper.UPDATE_STATUS_VERIFYING -> "Verifying..."
                            UpdateEngineWrapper.UPDATE_STATUS_FINALIZING -> "Finalizing..."
                            UpdateEngineWrapper.UPDATE_STATUS_UPDATED_NEED_REBOOT -> "Done. Reboot required."
                            else -> "Status: \$status"
                        }
                        _abUpdateStatus.value = statusText
                        _abUpdateProgress.value = percent
                    }

                    override fun onPayloadApplicationComplete(errorCode: Int) {
                        if (errorCode == 0) { // Success
                            _abUpdateStatus.value = "Installation Complete"
                            _abUpdateProgress.value = 1f
                            if (prefManager.autoDeleteAfterInstall) {
                                file.delete()
                            }
                            // Notify success
                            // Since we can't cleanly launch a UI callback from this background listener without context,
                            // we just update state, maybe the UI observes it and prompts reboot.
                            _abUpdateStatus.value = "Installation Complete. Please Reboot."
                        } else {
                            _abUpdateStatus.value = "Error: \$errorCode"
                            _abUpdateProgress.value = null
                        }
                    }

                    fun importLocalUpdateZip(
                        context: Context,
                        zipUri: Uri,
                        onSuccess: (String) -> Unit,
                        onError: (String) -> Unit
                    ) {
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                val resolver = context.contentResolver
                                val sourceStream = resolver.openInputStream(zipUri) ?: throw Exception("Could not read selected file")
                                val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                                    ?: throw Exception("Storage unavailable")
                                if (!targetDir.exists()) targetDir.mkdirs()

                                val targetFile = File(targetDir, "ota_update_local_${System.currentTimeMillis()}_${UUID.randomUUID()}.zip")
                                sourceStream.use { input ->
                                    targetFile.outputStream().use { output ->
                                        val copiedBytes = input.copyTo(output)
                                        if (copiedBytes == 0L) {
                                            throw Exception("Selected ZIP is empty")
                                        }
                                    }
                                }

                                if (!targetFile.exists() || targetFile.length() == 0L) {
                                    throw Exception("ZIP import failed during copy")
                                }

                                _abUpdateStatus.value = null
                                _abUpdateProgress.value = null
                                _downloadProgressState.value = OtaDownloadService.DownloadState.Success(targetFile.absolutePath)

                                withContext(Dispatchers.Main) {
                                    onSuccess(targetFile.absolutePath)
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    onError(e.message ?: "Failed to import local ZIP")
                                }
                            }
                        }
                    }
                }
                
                val started = UpdateEngineWrapper.applyUpdate(file, listener)
                if (!started) {
                    withContext(Dispatchers.Main) {
                        _abUpdateStatus.value = null
                        onError("Failed to start UpdateEngine. Is it supported?")
                    }
                }
            } else {
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

    fun downloadAndInstallAppUpdate(context: Context, apkUrl: String) {
        _appUpdateProgress.value = 0f
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(apkUrl).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                
                val body = response.body ?: throw Exception("Empty body")
                val totalBytes = body.contentLength()
                val inputStream = body.byteStream()
                
                val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: throw Exception("Storage unavailable")
                if (!targetDir.exists()) targetDir.mkdirs()
                val targetFile = File(targetDir, "updater_update.apk")
                if (targetFile.exists()) targetFile.delete()
                
                val outputStream = targetFile.outputStream()
                val buffer = ByteArray(4096)
                var bytesRead: Int
                var bytesDownloaded = 0L
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    bytesDownloaded += bytesRead
                    if (totalBytes > 0) {
                        _appUpdateProgress.value = bytesDownloaded.toFloat() / totalBytes
                    }
                }
                
                outputStream.flush()
                outputStream.close()
                inputStream.close()
                
                withContext(Dispatchers.Main) {
                    _appUpdateProgress.value = null
                    installApk(context, targetFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _appUpdateProgress.value = null
                    Toast.makeText(context, "Failed to download update: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

sealed interface AppUpdateState {
    object UpToDate : AppUpdateState
    data class UpdateAvailable(
        val version: String,
        val changelog: String,
        val downloadUrl: String,
        val apkUrl: String? = null
    ) : AppUpdateState
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
