package com.bypassnext.release

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.DataOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ShellExecutor {
    suspend fun execute(command: String): Result<String>
}

class DefaultShellExecutor(private val shell: String = "su") : ShellExecutor {
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private val mutex = Mutex()
    private val TOKEN = UUID.randomUUID().toString()

    private fun ensureProcess() {
        if (process?.isAlive == true) return

        try {
            val pb = ProcessBuilder(shell)
            pb.redirectErrorStream(true)
            process = pb.start()

            writer = process!!.outputStream.bufferedWriter()
            reader = process!!.inputStream.bufferedReader()
        } catch (e: Exception) {
            throw IOException("Failed to start shell process: $shell", e)
        }
    }

    override suspend fun execute(command: String): Result<String> = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                ensureProcess()
                val writer = this@DefaultShellExecutor.writer!!
                val reader = this@DefaultShellExecutor.reader!!

                // Append token to detect end of command
                // echo the token and exit code
                // We use UUID to uniquely identify the end of our command output
                writer.write("$command; echo \"$TOKEN $?\"")
                writer.newLine()
                writer.flush()

                val output = StringBuilder()
                var exitCode = -1

                while (true) {
                    val line = reader.readLine() ?: break // Pipe broken
                    if (line.contains(TOKEN)) {
                         val parts = line.trim().split(" ")
                         // Format: ... UUID exitCode
                         // check if the line ends with TOKEN and exit code
                         if (parts.size >= 2 && parts[parts.size - 2] == TOKEN) {
                             exitCode = parts.last().toIntOrNull() ?: -1
                             break
                         }
                    }
                    output.append(line).append("\n")
                }

                if (exitCode == 0) {
                    Result.success(output.toString().trim())
                } else {
                    Result.failure(Exception(output.toString().trim()))
                }
            } catch (e: Exception) {
                // If IO error or crash, kill process to restart next time
                process?.destroy()
                process = null
                Result.failure(e)
            }
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

    suspend fun execute(command: String): Result<String> = shellExecutor.execute(command)

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        execute("id").isSuccess
    }

    suspend fun isPrivacyModeEnabled(nextDnsId: String): Boolean = coroutineScope {
        if (!isValidNextDnsId(nextDnsId)) return@coroutineScope false

        // Check DNS settings in a single shell execution to reduce process overhead
        val result = execute("settings get global private_dns_mode; settings get global private_dns_specifier")
        val output = result.getOrNull() ?: return@coroutineScope false

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
            chmod 700 "${'$'}TEMP_DIR"

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

    // Commands to enable Privacy Mode
    suspend fun enablePrivacyMode(nextDnsId: String, tempDir: String): Result<String> {
        if (!isValidNextDnsId(nextDnsId)) {
            return Result.failure(IllegalArgumentException("Invalid NextDNS ID"))
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
    suspend fun disablePrivacyMode(tempDir: String): Result<String> {
        return execute(getDisablePrivacyScript(tempDir))
    }
}
