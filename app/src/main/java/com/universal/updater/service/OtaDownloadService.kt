package com.universal.updater.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.universal.updater.R
import com.universal.updater.data.PrefManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.math.roundToLong

class OtaDownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var downloadJob: Job? = null
    private val okHttpClient = OkHttpClient()
    private lateinit var prefManager: PrefManager
    
    // Track current state
    private var currentUrl = ""
    private var currentSha256 = ""
    private var currentRomName = ""
    private var isPausedByUser = false

    companion object {
        const val CHANNEL_ID = "ota_download_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.universal.updater.action.START_DOWNLOAD"
        const val ACTION_CANCEL = "com.universal.updater.action.CANCEL_DOWNLOAD"
        const val ACTION_PAUSE = "com.universal.updater.action.PAUSE_DOWNLOAD"
        const val ACTION_RESUME = "com.universal.updater.action.RESUME_DOWNLOAD"

        const val EXTRA_URL = "extra_download_url"
        const val EXTRA_SHA256 = "extra_sha256"
        const val EXTRA_ROM_NAME = "extra_rom_name"

        private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
        val downloadState = _downloadState.asStateFlow()

        fun cancelDownload(context: android.content.Context) {
            val intent = Intent(context, OtaDownloadService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }

        fun pauseDownload(context: android.content.Context) {
            val intent = Intent(context, OtaDownloadService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resumeDownload(context: android.content.Context, url: String, sha256: String, romName: String) {
            val intent = Intent(context, OtaDownloadService::class.java).apply {
                action = ACTION_RESUME
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_SHA256, sha256)
                putExtra(EXTRA_ROM_NAME, romName)
            }
            context.startService(intent)
        }
    }

    sealed interface DownloadState {
        object Idle : DownloadState
        object Connecting : DownloadState
        data class Downloading(
            val progress: Float, // 0.0 to 1.0
            val downloadedBytes: Long,
            val totalBytes: Long,
            val speedMbSeconds: Double,
            val etaSeconds: Long
        ) : DownloadState
        data class Paused(
            val progress: Float,
            val downloadedBytes: Long,
            val totalBytes: Long
        ) : DownloadState
        object Verifying : DownloadState
        data class Success(val fileAbsolutePath: String) : DownloadState
        data class Error(val errorMessage: String) : DownloadState
    }

    override fun onCreate() {
        super.onCreate()
        prefManager = PrefManager(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: ""
                val sha256 = intent.getStringExtra(EXTRA_SHA256) ?: ""
                val romName = intent.getStringExtra(EXTRA_ROM_NAME) ?: "Update"
                startDownload(url, sha256, romName, false)
            }
            ACTION_RESUME -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: currentUrl
                val sha256 = intent.getStringExtra(EXTRA_SHA256) ?: currentSha256
                val romName = intent.getStringExtra(EXTRA_ROM_NAME) ?: currentRomName
                startDownload(url, sha256, romName, true)
            }
            ACTION_PAUSE -> {
                pauseDownload()
            }
            ACTION_CANCEL -> {
                stopDownload()
            }
        }
        return START_NOT_STICKY
    }

    private fun checkNetworkAllowed(): Boolean {
        if (!prefManager.downloadOverWifi) return true // allowed on any network
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun startDownload(url: String, expectedSha256: String, romName: String, isResume: Boolean) {
        if (downloadJob != null && downloadJob?.isActive == true) return
        
        currentUrl = url
        currentSha256 = expectedSha256
        currentRomName = romName
        isPausedByUser = false

        if (!checkNetworkAllowed()) {
            _downloadState.value = DownloadState.Error("Download paused: Waiting for Wi-Fi")
            showCompletionNotification(romName, "Download paused: Waiting for Wi-Fi", true)
            stopSelf()
            return
        }

        val initialNotification = createNotification(romName, if (isResume) "Resuming..." else "Connecting...", 0, true, false)
        startForeground(NOTIFICATION_ID, initialNotification)

        _downloadState.value = DownloadState.Connecting

        downloadJob = serviceScope.launch {
            try {
                if (url.contains("uniupdater-ota/main/test.zip") || url.isEmpty()) {
                    var bytesDownloaded = 0L
                    val totalBytes = 15L * 1024 * 1024 // 15MB
                    
                    if (isResume && _downloadState.value is DownloadState.Paused) {
                        val pausedState = _downloadState.value as DownloadState.Paused
                        bytesDownloaded = pausedState.downloadedBytes
                    }
                    
                    val startTime = System.currentTimeMillis()
                    var lastUpdate = System.currentTimeMillis()
                    
                    while (bytesDownloaded < totalBytes) {
                        if (!isActive || isPausedByUser) {
                            return@launch
                        }
                        
                        // Network check periodically
                        if (bytesDownloaded % (1024 * 1024) == 0L && !checkNetworkAllowed()) {
                             pauseDownloadInternal("Waiting for Wi-Fi", bytesDownloaded, totalBytes)
                             return@launch
                        }
                        
                        delay(150)
                        bytesDownloaded += 350 * 1024
                        if (bytesDownloaded > totalBytes) bytesDownloaded = totalBytes

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUpdate > 300 || bytesDownloaded == totalBytes) {
                            val progress = bytesDownloaded.toFloat() / totalBytes
                            val durationSec = (currentTime - startTime) / 1000.0
                            val speed = if (durationSec > 0) (bytesDownloaded / (1024.0 * 1024.0)) / durationSec else 0.0
                            val eta = if (speed > 0) {
                                ((totalBytes - bytesDownloaded) / (1024.0 * 1024.0) / speed).roundToLong()
                            } else 0L

                            _downloadState.value = DownloadState.Downloading(
                                progress = progress,
                                downloadedBytes = bytesDownloaded,
                                totalBytes = totalBytes,
                                speedMbSeconds = speed,
                                etaSeconds = eta
                            )

                            val pct = (progress * 100).toInt()
                            val speedStr = String.format("%.1f MB/s", speed)
                            val text = "$pct% | $speedStr | ETA: ${formatEta(eta)} [Simulated]"
                            updateNotification(romName, text, pct, false, true)
                            lastUpdate = currentTime
                        }
                    }

                    val targetDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: throw Exception("Storage unavailable")
                    if (!targetDir.exists()) targetDir.mkdirs()
                    val targetFile = File(targetDir, "ota_update.zip")
                    targetFile.createNewFile()

                    _downloadState.value = DownloadState.Verifying
                    updateNotification(romName, "Verifying checksum... [Simulated]", 0, true, false)
                    delay(1500)

                    _downloadState.value = DownloadState.Success(targetFile.absolutePath)
                    showCompletionNotification(romName, "Mock download completed successfully.")
                    return@launch
                }

                val targetDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: throw Exception("Storage unavailable")
                if (!targetDir.exists()) targetDir.mkdirs()

                val targetFile = File(targetDir, "ota_update.zip")
                var downloadedBytes = 0L

                if (isResume && targetFile.exists()) {
                    downloadedBytes = targetFile.length()
                } else if (targetFile.exists()) {
                    targetFile.delete()
                    targetFile.createNewFile()
                }

                val requestBuilder = Request.Builder().url(url)
                if (downloadedBytes > 0) {
                    requestBuilder.header("Range", "bytes=$downloadedBytes-")
                }

                val request = requestBuilder.build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful && response.code != 206) {
                    throw Exception("Server returned code ${response.code}")
                }

                val body = response.body ?: throw Exception("Response body is empty")
                val totalBytes = downloadedBytes + body.contentLength()
                val inputStream = body.byteStream()

                val randomAccessFile = RandomAccessFile(targetFile, "rw")
                randomAccessFile.seek(downloadedBytes)

                val buffer = ByteArray(8192)
                var bytes: Int

                val startTime = System.currentTimeMillis()
                var lastUpdate = System.currentTimeMillis()

                while (inputStream.read(buffer).also { bytes = it } != -1) {
                    if (!isActive || isPausedByUser) {
                        randomAccessFile.close()
                        inputStream.close()
                        return@launch
                    }
                    
                    if (downloadedBytes % (1024 * 1024) == 0L && !checkNetworkAllowed()) {
                         randomAccessFile.close()
                         inputStream.close()
                         pauseDownloadInternal("Waiting for Wi-Fi", downloadedBytes, totalBytes)
                         return@launch
                    }
                    
                    randomAccessFile.write(buffer, 0, bytes)
                    downloadedBytes += bytes

                    val currentTime = System.currentTimeMillis()
                    // Update UI/Notification at most every 400ms
                    if (currentTime - lastUpdate > 400) {
                        val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                        val durationSec = (currentTime - startTime) / 1000.0
                        val speed = if (durationSec > 0) (downloadedBytes / (1024.0 * 1024.0)) / durationSec else 0.0
                        val eta = if (speed > 0 && totalBytes > 0) {
                            ((totalBytes - downloadedBytes) / (1024.0 * 1024.0) / speed).roundToLong()
                        } else {
                            0L
                        }

                        _downloadState.value = DownloadState.Downloading(
                            progress = progress,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            speedMbSeconds = speed,
                            etaSeconds = eta
                        )

                        val pct = (progress * 100).toInt()
                        val speedStr = String.format("%.1f MB/s", speed)
                        val text = "$pct% | $speedStr | ETA: ${formatEta(eta)}"
                        updateNotification(romName, text, pct, false, true)

                        lastUpdate = currentTime
                    }
                }

                randomAccessFile.close()
                inputStream.close()

                // SHA256 Verification
                _downloadState.value = DownloadState.Verifying
                updateNotification(romName, "Verifying checksum...", 0, true, false)

                if (verifySha256(targetFile, expectedSha256)) {
                    _downloadState.value = DownloadState.Success(targetFile.absolutePath)
                    showCompletionNotification(romName, "Download completed and verified successfully.")
                } else {
                    targetFile.delete()
                    throw Exception("SHA-256 verification failed. File may be corrupted.")
                }

            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _downloadState.value = DownloadState.Error(e.message ?: "Unknown error")
                    showCompletionNotification(romName, "Download failed: ${e.message}", true)
                }
            } finally {
                if (!isPausedByUser && _downloadState.value !is DownloadState.Paused && _downloadState.value !is DownloadState.Connecting && _downloadState.value !is DownloadState.Downloading) {
                    stopSelf()
                }
            }
        }
    }

    private fun pauseDownload() {
        isPausedByUser = true
        downloadJob?.cancel()
        
        val currentState = _downloadState.value
        if (currentState is DownloadState.Downloading) {
            pauseDownloadInternal("Paused by user", currentState.downloadedBytes, currentState.totalBytes)
        } else {
            _downloadState.value = DownloadState.Idle
            stopSelf()
        }
    }

    private fun pauseDownloadInternal(reason: String, downloadedBytes: Long, totalBytes: Long) {
        val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
        _downloadState.value = DownloadState.Paused(
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes
        )
        val pct = (progress * 100).toInt()
        val text = "Paused: $reason - $pct%"
        updateNotification(currentRomName, text, pct, false, false)
    }

    private fun stopDownload() {
        downloadJob?.cancel()
        _downloadState.value = DownloadState.Idle
        stopSelf()
    }

    private fun verifySha256(file: File, expectedSha256: String): Boolean {
        if (expectedSha256.isEmpty()) return true // Skip check if no sha256 provided
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val fis = FileInputStream(file)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            fis.close()
            val sha256Hex = digest.digest().joinToString("") { "%02x".format(it) }
            sha256Hex.equals(expectedSha256, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    private fun formatEta(seconds: Long): String {
        return when {
            seconds <= 0 -> "Calculating..."
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OTA Update Downloader",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of the custom ROM update download"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(
        title: String,
        text: String,
        progress: Int,
        indeterminate: Boolean,
        canPause: Boolean
    ): Notification {
        val cancelIntent = Intent(this, OtaDownloadService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val pauseIntent = Intent(this, OtaDownloadService::class.java).apply {
            action = ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(
            this,
            2,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resumeIntent = Intent(this, OtaDownloadService::class.java).apply {
            action = ACTION_RESUME
        }
        val resumePendingIntent = PendingIntent.getService(
            this,
            3,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Launch app on notification tap
        val appIntent = packageManager.getLaunchIntentForPackage(packageName)
        val appPendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading: $title")
            .setContentText(text)
            .setOngoing(true)
            .setProgress(100, progress, indeterminate)
            .setContentIntent(appPendingIntent)
            
        if (canPause) {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
        } else if (_downloadState.value is DownloadState.Paused) {
            builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
        }
            
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)

        return builder.build()
    }

    private fun updateNotification(title: String, text: String, progress: Int, indeterminate: Boolean, canPause: Boolean) {
        val notification = createNotification(title, text, progress, indeterminate, canPause)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(title: String, text: String, isFailed: Boolean = false) {
        val appIntent = packageManager.getLaunchIntentForPackage(packageName)
        val appPendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (isFailed) android.R.drawable.stat_notify_error else android.R.drawable.stat_sys_download_done)
            .setContentTitle(if (isFailed) "Update Download Failed" else "Update Ready: $title")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(appPendingIntent)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID + 1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadJob?.cancel()
        serviceJob.cancel()
    }
}
