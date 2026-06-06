package com.universal.updater.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.universal.updater.MainActivity
import com.universal.updater.R
import com.universal.updater.data.PrefManager
import com.universal.updater.data.model.RomUpdateInfo
import com.universal.updater.utils.SystemUtils
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class UpdateCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefManager = PrefManager(applicationContext)

        // Only check if it's not set to NEVER, although WorkManager schedules handle this, it's a safe guard.
        if (prefManager.updateCheckInterval == "NEVER") {
            return Result.success()
        }

        try {
            val isSim = prefManager.isSimulationEnabled
            val currentDevice = if (isSim) prefManager.simDevice else SystemUtils.getDeviceCodename()
            val currentBuildDate = if (isSim) prefManager.simLatest else SystemUtils.getBuildDateUtc()

            val jsonParser = Json { ignoreUnknownKeys = true }
            val client = OkHttpClient()

            val updateInfo: RomUpdateInfo = if (isSim) {
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
                    return Result.retry()
                }

                val body = response.body?.string() ?: return Result.failure()
                jsonParser.decodeFromString<RomUpdateInfo>(body)
            }

            val isDeviceCompatible = if (isSim && prefManager.forceUpdateAvailable) true else {
                updateInfo.rom_device.equals(currentDevice, ignoreCase = true)
            }
            
            val isUpdateAvailable = if (isSim && prefManager.forceUpdateAvailable) true else {
                isDeviceCompatible && (updateInfo.rom_latest > currentBuildDate)
            }

            if (isUpdateAvailable) {
                showUpdateAvailableNotification(updateInfo)
            }

            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    private fun showUpdateAvailableNotification(updateInfo: RomUpdateInfo) {
        val channelId = "ota_update_available_channel"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "New Update Available",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a new OTA update is found"
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("New Update Available!")
            .setContentText("${updateInfo.rom_name} ${updateInfo.rom_version} is ready to download.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(2002, notification)
    }
}
