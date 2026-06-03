package com.example.uniupdater.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.BufferedWriter
import java.io.OutputStreamWriter

object RootUtils {

    fun isSystemApp(context: Context): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (e: Exception) {
            false
        }
    }

    fun installAsSystemApp(apkPath: String): Boolean {
        val systemAppPath = "/system/priv-app/UniUpdater"
        val commands = listOf(
            // Try mounting root and system as read-write
            "mount -o remount,rw /",
            "mount -o remount,rw /system",
            "mount -o remount,rw /system_root",
            
            // Create directory and copy the APK
            "mkdir -p $systemAppPath",
            "cp $apkPath $systemAppPath/UniUpdater.apk",
            
            // Apply standard system-app permissions
            "chmod 755 $systemAppPath",
            "chmod 644 $systemAppPath/UniUpdater.apk",
            
            // Fallback for some device trees to copy to /system/app if priv-app fails
            "mkdir -p /system/app/UniUpdater",
            "cp $apkPath /system/app/UniUpdater/UniUpdater.apk",
            "chmod 755 /system/app/UniUpdater",
            "chmod 644 /system/app/UniUpdater/UniUpdater.apk",
            
            // Restore read-only partitions
            "mount -o remount,ro /system",
            "mount -o remount,ro /system_root",
            "mount -o remount,ro /"
        )
        return runRootCommands(commands)
    }

    fun isRootAvailable(): Boolean {
        var process: Process? = null
        var writer: BufferedWriter? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            writer = BufferedWriter(OutputStreamWriter(process.outputStream))
            writer.write("exit\n")
            writer.flush()
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            false
        } finally {
            writer?.close()
            process?.destroy()
        }
    }

    fun runRootCommands(commands: List<String>): Boolean {
        var process: Process? = null
        var writer: BufferedWriter? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            writer = BufferedWriter(OutputStreamWriter(process.outputStream))
            for (cmd in commands) {
                writer.write(cmd + "\n")
            }
            writer.write("exit\n")
            writer.flush()
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            false
        } finally {
            writer?.close()
            process?.destroy()
        }
    }

    fun installOtaViaRecovery(zipPath: String): Boolean {
        // Build openrecoveryscript commands for TWRP / OrangeFox
        // We write to both standard /cache/recovery and /metadata/recovery as fallback
        val commands = listOf(
            "mkdir -p /cache/recovery",
            "echo \"install $zipPath\" > /cache/recovery/openrecoveryscript",
            "echo \"reboot\" >> /cache/recovery/openrecoveryscript",
            "chmod 0777 /cache/recovery/openrecoveryscript",
            
            // Also write a classic Android update command to /cache/recovery/command
            "echo \"--update_package=$zipPath\" > /cache/recovery/command",
            "chmod 0777 /cache/recovery/command",
            
            // Reboot to recovery to trigger the installation
            "reboot recovery"
        )
        return runRootCommands(commands)
    }

    fun reboot(): Boolean {
        return runRootCommands(listOf("reboot"))
    }

    fun rebootRecovery(): Boolean {
        return runRootCommands(listOf("reboot recovery"))
    }
}
