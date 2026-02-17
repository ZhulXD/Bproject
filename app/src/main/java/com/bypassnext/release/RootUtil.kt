package com.bypassnext.release

import java.io.DataOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async

interface ShellExecutor {
    suspend fun execute(command: String): String
}

class DefaultShellExecutor : ShellExecutor {
    override suspend fun execute(command: String): String = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes(command + "\n")
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor()

            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()

            if (process.exitValue() == 0) output else "Error: $error"
        } catch (e: IOException) {
            "Error: ${e.message}"
        } catch (e: InterruptedException) {
            "Error: ${e.message}"
        }
    }
}

object RootUtil {

    var shellExecutor: ShellExecutor = DefaultShellExecutor()

    private const val DEFAULT_TEMP_DIR = "/data/local/tmp/filtered_certs"

    private fun escapeShellArg(arg: String): String {
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    fun isValidNextDnsId(nextDnsId: String): Boolean {
        // NextDNS IDs are typically alphanumeric, possibly with hyphens or dots
        return nextDnsId.isNotEmpty() && nextDnsId.matches(Regex("^[a-zA-Z0-9.-]+$"))
    }

    suspend fun execute(command: String): String = shellExecutor.execute(command)

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val output = execute("id")
            !output.startsWith("Error")
        } catch (e: Exception) {
            false
        }
    }

    suspend fun isPrivacyModeEnabled(nextDnsId: String): Boolean = coroutineScope {
        if (!isValidNextDnsId(nextDnsId)) return@coroutineScope false

        // Check DNS settings in a single shell execution to reduce process overhead
        val output = execute("settings get global private_dns_mode; settings get global private_dns_specifier")
        val lines = output.trim().split("\n").map { it.trim() }

        if (lines.size < 2) return@coroutineScope false

        val dnsMode = lines[0]
        val dnsSpecifier = lines[1]

        checkPrivacyStatus(dnsMode, dnsSpecifier, nextDnsId)
    }

    fun checkPrivacyStatus(dnsMode: String, dnsSpecifier: String, expectedNextDnsId: String): Boolean {
        return dnsMode == "hostname" && dnsSpecifier == expectedNextDnsId
    }

    fun getEnablePrivacyScript(nextDnsId: String, tempDir: String): String {
        val escapedNextDnsId = escapeShellArg(nextDnsId)
        val escapedTempDir = escapeShellArg(tempDir)
        return """
            # 1. Set Private DNS
            settings put global private_dns_mode hostname
            settings put global private_dns_specifier $escapedNextDnsId

            # 2. Disable Certificates (Safe Mount Method)
            # Create a clean temp directory
            TEMP_DIR=$escapedTempDir
            rm -rf "${'$'}TEMP_DIR"
            mkdir -p "${'$'}TEMP_DIR"
            chmod 755 "${'$'}TEMP_DIR"

            # Identify source directory (Android version dependent)
            if [ -d "/apex/com.android.conscrypt/cacerts" ]; then
                SRC_DIR="/apex/com.android.conscrypt/cacerts"
            else
                SRC_DIR="/system/etc/security/cacerts"
            fi

            # Copy all certs
            cp ${'$'}SRC_DIR/* "${'$'}TEMP_DIR/"

            # Filter out the blocked ones (DigiCert, GlobalSign, SSL) based on content
            # Note: Using grep on binary files can be tricky, assuming ASCII text exists inside PEM
            grep -El "Digi[Cc]ert|GlobalSign|SSL" ${'$'}TEMP_DIR/* | xargs rm 2>/dev/null

            # Mount the filtered directory over the system one
            mount -o bind "${'$'}TEMP_DIR" "${'$'}SRC_DIR"

            # Restart networking to apply DNS (optional/gentle)
            # svc wifi disable; svc wifi enable

            echo "Privacy Mode Activated: DNS set to NextDNS, Certificates filtered."
        """.trimIndent()
    }

    // Commands to enable Privacy Mode
    // TODO: Use applicationContext.cacheDir.absolutePath instead of hardcoded path if possible
    suspend fun enablePrivacyMode(nextDnsId: String, tempDir: String = DEFAULT_TEMP_DIR): String {
        if (!isValidNextDnsId(nextDnsId)) {
            return "Error: Invalid NextDNS ID"
        }
        return execute(getEnablePrivacyScript(nextDnsId, tempDir))
    }

    fun getDisablePrivacyScript(tempDir: String): String {
        val escapedTempDir = escapeShellArg(tempDir)
        return """
            # 1. Reset DNS
            settings put global private_dns_mode off
            settings delete global private_dns_specifier

            # 2. Unmount Certificates (Revert to System Default)
            if [ -d "/apex/com.android.conscrypt/cacerts" ]; then
                umount "/apex/com.android.conscrypt/cacerts"
            else
                umount "/system/etc/security/cacerts"
            fi

            # Clean up temp
            rm -rf $escapedTempDir

            echo "Privacy Mode Deactivated: DNS reset, System Certificates restored."
        """.trimIndent()
    }

    // Commands to disable Privacy Mode (Revert)
    suspend fun disablePrivacyMode(tempDir: String): String {
        return execute(getDisablePrivacyScript(tempDir))
    }
}
