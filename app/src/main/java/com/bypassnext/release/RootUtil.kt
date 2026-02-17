package com.bypassnext.release

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ShellExecutor {
    suspend fun execute(command: String): String
}

class DefaultShellExecutor : ShellExecutor {
    private val mutex = Mutex()
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    override suspend fun execute(command: String): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                ensureProcess()
                val token = UUID.randomUUID().toString()
                val commandLine = "$command; echo \"$token $?\"\n"

                writer?.write(commandLine)
                writer?.flush()

                val output = StringBuilder()
                var line: String?
                var exitCode = 0

                while (reader?.readLine().also { line = it } != null) {
                    if (line!!.contains(token)) {
                        val parts = line!!.trim().split(" ")
                        if (parts.size >= 2 && parts[parts.size - 2] == token) {
                             exitCode = parts.last().toIntOrNull() ?: 0
                             break
                        }
                    }
                    output.append(line).append("\n")
                }

                if (line == null) {
                    process = null
                    throw IOException("Shell process died unexpectedly")
                }

                val result = output.toString().trim()
                if (exitCode != 0) {
                    "Error: Command failed with exit code $exitCode\n$result"
                } else {
                    result
                }

            } catch (e: Exception) {
                try {
                    process?.destroy()
                } catch (ignore: Exception) {}
                process = null
                writer = null
                reader = null
                "Error: ${e.message}"
            }
        }
    }

    private fun ensureProcess() {
        if (process?.isAlive != true) {
            val pb = ProcessBuilder("su")
            pb.redirectErrorStream(true)
            process = pb.start()
            writer = process!!.outputStream.bufferedWriter()
            reader = process!!.inputStream.bufferedReader()
        }
    }
}

object RootUtil {

    var shellExecutor: ShellExecutor = DefaultShellExecutor()


    private fun escapeShellArg(arg: String): String {
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    fun isValidNextDnsId(nextDnsId: String): Boolean {
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
            grep -El "Digi[Cc]ert|GlobalSign|SSL" ${'$'}TEMP_DIR/* | xargs rm 2>/dev/null

            # Mount the filtered directory over the system one
            mount -o bind "${'$'}TEMP_DIR" "${'$'}SRC_DIR"

            # Restart networking to apply DNS (optional/gentle)
            # svc wifi disable; svc wifi enable

            echo "Privacy Mode Activated: DNS set to NextDNS, Certificates filtered."
        """.trimIndent()
    }

    suspend fun enablePrivacyMode(nextDnsId: String, tempDir: String): String {
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

    suspend fun disablePrivacyMode(tempDir: String): String {
        return execute(getDisablePrivacyScript(tempDir))
    }
}
