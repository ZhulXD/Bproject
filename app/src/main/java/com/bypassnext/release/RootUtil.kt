package com.bypassnext.release

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.TimeoutCancellationException

interface ShellExecutor : Closeable {
    suspend fun execute(command: String): Result<String>
}

private class DefaultShellExecutor(
    private val shell: String = "su",
    private val timeoutMs: Long = 60_000L,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : ShellExecutor {

    override suspend fun execute(command: String): Result<String> = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            val pb = ProcessBuilder(shell)
            pb.redirectErrorStream(true)
            process = pb.start()

            // Write command to stdin
            // We use strict scoping for outputStream to ensure it's closed immediately,
            // signaling EOF to the shell process.
            try {
                process.outputStream.buffered().use { writer ->
                    writer.write(command.toByteArray())
                    writer.write("\nexit\n".toByteArray())
                    writer.flush()
                }
            } catch (e: IOException) {
                // If writing fails (e.g. process died), we still want to read output/error
            }

            // Read output asynchronously
            val outputDeferred = async {
                try {
                    process!!.inputStream.bufferedReader().use { it.readText() }
                } catch (e: IOException) {
                    ""
                }
            }

            // Wait for exit with timeout
            val exited = runInterruptible {
                process!!.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            }

            if (exited) {
                val output = outputDeferred.await()
                val exitCode = process!!.exitValue()
                if (exitCode == 0) {
                    Result.success(output.trim())
                } else {
                    Result.failure(Exception(output.trim()))
                }
            } else {
                process!!.destroy()
                outputDeferred.cancel()
                throw TimeoutException("Command timed out after ${timeoutMs}ms")
            }
        } catch (e: Exception) {
            process?.destroy()
            if (e is TimeoutCancellationException || e is TimeoutException) {
                Result.failure(e)
            } else {
                Result.failure(Exception("Shell execution failed", e))
            }
        }
    }

    override fun close() {
        // Stateless implementation, nothing to close
    }
}

object RootUtil {

    var shellExecutor: ShellExecutor = DefaultShellExecutor()
    internal fun createDefaultShellExecutor(
        shell: String = "su",
        timeoutMs: Long = 60_000L,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    ): ShellExecutor = DefaultShellExecutor(shell, timeoutMs, scope)

    private val NEXT_DNS_ID_REGEX = Regex("^[a-zA-Z0-9.-]+$")

    private fun escapeShellArg(arg: String): String {
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    fun isValidNextDnsId(nextDnsId: String): Boolean {
        return nextDnsId.isNotEmpty() && nextDnsId.matches(NEXT_DNS_ID_REGEX)
    }

    private suspend fun execute(command: String): Result<String> = shellExecutor.execute(command)

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

    suspend fun disablePrivacyMode(tempDir: String): Result<String> {
        return execute(getDisablePrivacyScript(tempDir))
    }

    fun shutdown() {
        try {
            shellExecutor.close()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
