package com.example.uniupdater.utils

import java.io.BufferedWriter
import java.io.OutputStreamWriter

object RootUtils {

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

    fun rebootRecovery(): Boolean {
        return runRootCommands(listOf("reboot recovery"))
    }
}
