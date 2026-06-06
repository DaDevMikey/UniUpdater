package com.universal.updater.utils

import android.os.Handler
import android.os.Looper
import android.os.UpdateEngine
import android.os.UpdateEngineCallback
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipFile

object UpdateEngineWrapper {
    private const val TAG = "UpdateEngineWrapper"

    // Status constants (from android.os.UpdateEngine.UpdateStatusConstants)
    const val UPDATE_STATUS_IDLE = 0
    const val UPDATE_STATUS_CHECKING_FOR_UPDATE = 1
    const val UPDATE_STATUS_UPDATE_AVAILABLE = 2
    const val UPDATE_STATUS_DOWNLOADING = 3
    const val UPDATE_STATUS_VERIFYING = 4
    const val UPDATE_STATUS_FINALIZING = 5
    const val UPDATE_STATUS_UPDATED_NEED_REBOOT = 6
    const val UPDATE_STATUS_REPORTING_ERROR_EVENT = 7
    const val UPDATE_STATUS_ATTEMPTING_ROLLBACK = 8
    const val UPDATE_STATUS_DISABLED = 9

    private var updateEngine: UpdateEngine? = null

    interface StatusListener {
        fun onStatusUpdate(status: Int, percent: Float)
        fun onPayloadApplicationComplete(errorCode: Int)
    }

    fun isAvailable(): Boolean {
        return try {
            if (updateEngine == null) {
                updateEngine = UpdateEngine()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "UpdateEngine not available: \${e.message}")
            false
        } catch (e: NoClassDefFoundError) {
            Log.e(TAG, "UpdateEngine not available (NoClassDefFoundError)")
            false
        }
    }

    fun applyUpdate(
        updateFile: File,
        listener: StatusListener,
        handler: Handler = Handler(Looper.getMainLooper())
    ): Boolean {
        if (!isAvailable() || updateEngine == null) return false

        try {
            // Read properties from zip
            val zipFile = ZipFile(updateFile)
            val propertiesEntry = zipFile.getEntry("payload_properties.txt")
            if (propertiesEntry == null) {
                Log.e(TAG, "payload_properties.txt not found in ZIP")
                zipFile.close()
                return false
            }

            val reader = BufferedReader(InputStreamReader(zipFile.getInputStream(propertiesEntry)))
            val properties = mutableListOf<String>()
            reader.forEachLine { line ->
                if (line.isNotBlank()) properties.add(line)
            }
            reader.close()

            val payloadEntry = zipFile.getEntry("payload.bin")
            if (payloadEntry == null) {
                Log.e(TAG, "payload.bin not found in ZIP")
                zipFile.close()
                return false
            }

            // Offset of the local file header data in the ZIP
            // Wait, standard ZipEntry.dataOffset is not exposed directly.
            // But we can extract payload.bin to a temp directory, OR pass the zip path using "file://"
            // However, UpdateEngine applyPayload takes `url` (which can be a file:// URI), `offset`, and `size`.
            // Standard approach by LineageOS is to use the `UpdateInstaller` that calculates the zip entry offset,
            // or we can just extract `payload.bin` to the same folder since it's safer.
            // Let's extract payload.bin and payload_properties.txt for simplicity if we can't find offset easily.
            // Actually, calculating the offset requires reading the ZIP Local File Header (LFH) size.
            // For now, let's just pass the zip file URL and 0 offset? No, UpdateEngine needs exactly the payload start.
            // Let's just extract it to `payload.bin` in the same dir for simplicity.
            
            val payloadFile = File(updateFile.parentFile, "payload.bin")
            if (!payloadFile.exists() || payloadFile.length() != payloadEntry.size) {
                Log.d(TAG, "Extracting payload.bin to \${payloadFile.absolutePath}")
                zipFile.getInputStream(payloadEntry).use { input ->
                    payloadFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            zipFile.close()

            updateEngine?.bind(object : UpdateEngineCallback() {
                override fun onStatusUpdate(status: Int, percent: Float) {
                    listener.onStatusUpdate(status, percent)
                }

                override fun onPayloadApplicationComplete(errorCode: Int) {
                    listener.onPayloadApplicationComplete(errorCode)
                    // Clean up extracted payload.bin
                    if (payloadFile.exists()) {
                        payloadFile.delete()
                    }
                }
            }, handler)

            val url = "file://\${payloadFile.absolutePath}"
            val headerArray = properties.toTypedArray()

            updateEngine?.applyPayload(url, 0, payloadFile.length(), headerArray)
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply payload", e)
            return false
        }
    }
    
    fun cancelUpdate() {
        if (isAvailable()) {
            updateEngine?.cancel()
        }
    }
}
