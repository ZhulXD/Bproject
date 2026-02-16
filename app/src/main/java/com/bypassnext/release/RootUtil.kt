package com.bypassnext.release

import java.io.DataOutputStream
import java.io.IOException

object RootUtil {

    private const val NEXTDNS_ID = "a4f5f2.dns.nextdns.io"

    fun execute(command: String): String {
        return try {
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

    fun isRootAvailable(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec("su -c id")
            p.waitFor()
            p.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun isPrivacyModeEnabled(): Boolean {
        // Check DNS settings
        val dnsMode = execute("settings get global private_dns_mode").trim()
        val dnsSpecifier = execute("settings get global private_dns_specifier").trim()

        return dnsMode == "hostname" && dnsSpecifier == NEXTDNS_ID
    }

    // Commands to enable Privacy Mode
    // TODO: Use applicationContext.cacheDir.absolutePath instead of hardcoded path if possible
    fun enablePrivacyMode(tempDir: String = "/data/local/tmp/filtered_certs"): String {
        val script = """
            # 1. Set Private DNS
            settings put global private_dns_mode hostname
            settings put global private_dns_specifier $NEXTDNS_ID

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

    fun getDisablePrivacyScript(tempDir: String = "/data/local/tmp/filtered_certs"): String {
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
    fun disablePrivacyMode(tempDir: String = "/data/local/tmp/filtered_certs"): String {
        return execute(getDisablePrivacyScript(tempDir))
    }
}
