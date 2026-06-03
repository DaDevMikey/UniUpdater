package com.example.uniupdater.utils

import android.os.Build
import java.io.BufferedReader
import java.io.InputStreamReader

object SystemUtils {

    fun getDeviceCodename(): String {
        val prop = getSystemProperty("ro.product.device")
        return prop.ifEmpty { Build.DEVICE ?: "unknown" }
    }

    fun getBuildVersion(): String {
        val prop = getSystemProperty("ro.build.version.incremental")
        return prop.ifEmpty { Build.VERSION.INCREMENTAL ?: "1.0" }
    }

    fun getBuildDateUtc(): Long {
        val prop = getSystemProperty("ro.build.date.utc")
        return if (prop.isNotEmpty()) {
            prop.toLongOrNull() ?: (Build.TIME / 1000)
        } else {
            Build.TIME / 1000 // Fallback to Build.TIME in seconds
        }
    }

    fun getSystemProperty(key: String): String {
        var process: Process? = null
        var reader: BufferedReader? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLine()?.trim() ?: ""
        } catch (e: Exception) {
            ""
        } finally {
            reader?.close()
            process?.destroy()
        }
    }
}
