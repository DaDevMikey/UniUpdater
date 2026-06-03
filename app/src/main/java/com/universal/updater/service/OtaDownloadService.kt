package com.universal.updater.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.universal.updater.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.math.roundToLong

class OtaDownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var downloadJob: Job? = null
    private val okHttpClient = OkHttpClient()

    companion object {
        const val CHANNEL_ID = "ota_download_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.universal.updater.action.START_DOWNLOAD"
        const val ACTION_CANCEL = "com.universal.updater.action.CANCEL_DOWNLOAD"

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
        object Verifying : DownloadState
        data class Success(val fileAbsolutePath: String) : DownloadState
        data class Error(val errorMessage: String) : DownloadState
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: ""
                val sha256 = intent.getStringExtra(EXTRA_SHA256) ?: ""
                val romName = intent.getStringExtra(EXTRA_ROM_NAME) ?: "Update"
                startDownload(url, sha256, romName)
            }
            ACTION_CANCEL -> {
                stopDownload()
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(url: String, expectedSha256: String, romName: String) {
        if (downloadJob != null && downloadJob?.isActive == true) return

        val initialNotification = createNotification(romName, "Connecting...", 0, true)
        startForeground(NOTIFICATION_ID, initialNotification)

        _downloadState.value = DownloadState.Connecting

        downloadJob = serviceScope.launch {
            try {
                if (url.contains("uniupdater-ota/main/test.zip") || url.isEmpty()) {
                    var bytesDownloaded = 0L
                    val totalBytes = 15L * 1024 * 1024 // 15MB
                    val startTime = System.currentTimeMillis()
                    var lastUpdate = System.currentTimeMillis()
                    
                    while (bytesDownloaded < totalBytes) {
                        if (!isActive) {
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
                            updateNotification(romName, text, pct, false)
                            lastUpdate = currentTime
                        }
                    }

                    val targetDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: throw Exception("Storage unavailable")
                    if (!targetDir.exists()) targetDir.mkdirs()
                    val targetFile = File(targetDir, "ota_update.zip")
                    targetFile.createNewFile()

                    _downloadState.value = DownloadState.Verifying
                    updateNotification(romName, "Verifying checksum... [Simulated]", 0, true)
                    delay(1500)

                    _downloadState.value = DownloadState.Success(targetFile.absolutePath)
                    showCompletionNotification(romName, "Mock download completed successfully.")
                    return@launch
                }

                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    throw Exception("Server returned code ${response.code}")
                }

                val body = response.body ?: throw Exception("Response body is empty")
                val totalBytes = body.contentLength()
                val inputStream = body.byteStream()

                // Save to app external files dir (scoped, accessible by root/recovery)
                val targetDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: throw Exception("Storage unavailable")
                if (!targetDir.exists()) targetDir.mkdirs()

                val targetFile = File(targetDir, "ota_update.zip")
                if (targetFile.exists()) targetFile.delete()

                val outputStream = targetFile.outputStream()
                val buffer = ByteArray(8192)
                var bytesDownloaded = 0L
                var bytes: Int

                val startTime = System.currentTimeMillis()
                var lastUpdate = System.currentTimeMillis()

                while (inputStream.read(buffer).also { bytes = it } != -1) {
                    if (!isActive) {
                        outputStream.close()
                        inputStream.close()
                        targetFile.delete()
                        return@launch
                    }
                    outputStream.write(buffer, 0, bytes)
                    bytesDownloaded += bytes

                    val currentTime = System.currentTimeMillis()
                    // Update UI/Notification at most every 400ms
                    if (currentTime - lastUpdate > 400) {
                        val progress = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
                        val durationSec = (currentTime - startTime) / 1000.0
                        val speed = if (durationSec > 0) (bytesDownloaded / (1024.0 * 1024.0)) / durationSec else 0.0
                        val eta = if (speed > 0 && totalBytes > 0) {
                            ((totalBytes - bytesDownloaded) / (1024.0 * 1024.0) / speed).roundToLong()
                        } else {
                            0L
                        }

                        _downloadState.value = DownloadState.Downloading(
                            progress = progress,
                            downloadedBytes = bytesDownloaded,
                            totalBytes = totalBytes,
                            speedMbSeconds = speed,
                            etaSeconds = eta
                        )

                        val pct = (progress * 100).toInt()
                        val speedStr = String.format("%.1f MB/s", speed)
                        val text = "$pct% | $speedStr | ETA: ${formatEta(eta)}"
                        updateNotification(romName, text, pct, false)

                        lastUpdate = currentTime
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                // SHA256 Verification
                _downloadState.value = DownloadState.Verifying
                updateNotification(romName, "Verifying checksum...", 0, true)

                if (verifySha256(targetFile, expectedSha256)) {
                    _downloadState.value = DownloadState.Success(targetFile.absolutePath)
                    showCompletionNotification(romName, "Download completed and verified successfully.")
                } else {
                    targetFile.delete()
                    throw Exception("SHA-256 verification failed. File may be corrupted.")
                }

            } catch (e: Exception) {
                _downloadState.value = DownloadState.Error(e.message ?: "Unknown error")
                showCompletionNotification(romName, "Download failed: ${e.message}", true)
            } finally {
                stopSelf()
            }
        }
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
        indeterminate: Boolean
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

        // Launch app on notification tap
        val appIntent = packageManager.getLaunchIntentForPackage(packageName)
        val appPendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading: $title")
            .setContentText(text)
            .setOngoing(true)
            .setProgress(100, progress, indeterminate)
            .setContentIntent(appPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    private fun updateNotification(title: String, text: String, progress: Int, indeterminate: Boolean) {
        val notification = createNotification(title, text, progress, indeterminate)
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
