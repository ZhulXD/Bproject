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

    suspend fun execute(command: String): String = shellExecutor.execute(command)

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val p = Runtime.getRuntime().exec("su -c id")
            p.waitFor()
            p.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    suspend fun isPrivacyModeEnabled(nextDnsId: String): Boolean = coroutineScope {
        // Check DNS settings in parallel
        val dnsModeDeferred = async { execute("settings get global private_dns_mode").trim() }
        val dnsSpecifierDeferred = async { execute("settings get global private_dns_specifier").trim() }

        checkPrivacyStatus(dnsModeDeferred.await(), dnsSpecifierDeferred.await(), nextDnsId)
    }

    fun checkPrivacyStatus(dnsMode: String, dnsSpecifier: String, expectedNextDnsId: String): Boolean {
        return dnsMode == "hostname" && dnsSpecifier == expectedNextDnsId
    }

    // Commands to enable Privacy Mode
    // TODO: Use applicationContext.cacheDir.absolutePath instead of hardcoded path if possible
    suspend fun enablePrivacyMode(nextDnsId: String, tempDir: String = DEFAULT_TEMP_DIR): String {
        val script = """
            # 1. Set Private DNS
            settings put global private_dns_mode hostname
            settings put global private_dns_specifier $nextDnsId

            # 2. Disable Certificates (Safe Mount Method)
            # Create a clean temp directory
            TEMP_DIR="$tempDir"
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

        return execute(script)
    }

    fun getDisablePrivacyScript(tempDir: String = DEFAULT_TEMP_DIR): String {
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
            rm -rf "$tempDir"

            echo "Privacy Mode Deactivated: DNS reset, System Certificates restored."
        """.trimIndent()
    }

    // Commands to disable Privacy Mode (Revert)
    suspend fun disablePrivacyMode(tempDir: String = DEFAULT_TEMP_DIR): String {
        return execute(getDisablePrivacyScript(tempDir))
    }
}
