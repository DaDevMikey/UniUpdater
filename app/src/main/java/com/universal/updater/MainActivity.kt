package com.universal.updater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.universal.updater.theme.UniUpdaterTheme
import androidx.work.*
import java.util.concurrent.TimeUnit
import com.universal.updater.data.PrefManager
import com.universal.updater.service.UpdateCheckWorker

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    scheduleUpdateCheck()

    enableEdgeToEdge()
    setContent {
      UniUpdaterTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }

  private fun scheduleUpdateCheck() {
      val prefManager = PrefManager(this)
      val interval = prefManager.updateCheckInterval
      
      val workManager = WorkManager.getInstance(this)
      
      if (interval == "NEVER") {
          workManager.cancelUniqueWork("UpdateCheckWorker")
          return
      }
      
      val repeatInterval = when (interval) {
          "DAILY" -> 1L
          "WEEKLY" -> 7L
          "MONTHLY" -> 30L
          else -> 1L
      }
      
      val constraints = Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build()
          
      val workRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(repeatInterval, TimeUnit.DAYS)
          .setConstraints(constraints)
          .build()
          
      workManager.enqueueUniquePeriodicWork(
          "UpdateCheckWorker",
          ExistingPeriodicWorkPolicy.UPDATE,
          workRequest
      )
  }
}
