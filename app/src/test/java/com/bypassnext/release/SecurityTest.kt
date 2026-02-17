package com.bypassnext.release

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityTest {

    @Test
    fun testCommandInjectionInNextDnsId_Escaped() {
        val maliciousId = "abc; echo hello"
        val tempDir = "/data/local/tmp/filtered_certs"
        val script = RootUtil.getEnablePrivacyScript(maliciousId, tempDir)

        // Now it should be: settings put global private_dns_specifier 'abc; echo hello'

        val lines = script.lines().map { it.trim() }
        val dnsSpecifierLine = lines.find { it.startsWith("settings put global private_dns_specifier") }

        assertTrue("DNS specifier should be escaped: $dnsSpecifierLine", dnsSpecifierLine == "settings put global private_dns_specifier 'abc; echo hello'")
    }

    @Test
    fun testCommandInjectionInTempDir_Escaped() {
        val nextDnsId = "test.dns.id"
        val maliciousTempDir = "/tmp/foo\"; echo hello; #"
        val script = RootUtil.getEnablePrivacyScript(nextDnsId, maliciousTempDir)

        // TEMP_DIR='/tmp/foo"; echo hello; #'

        val lines = script.lines().map { it.trim() }
        val tempDirLine = lines.find { it.startsWith("TEMP_DIR=") }

        assertTrue("Temp dir should be escaped: $tempDirLine", tempDirLine == "TEMP_DIR='/tmp/foo\"; echo hello; #'")
    }

    @Test
    fun testShellEscapingWithSingleQuotes() {
        val maliciousId = "abc'def"
        val tempDir = "/data/local/tmp/filtered_certs"
        val script = RootUtil.getEnablePrivacyScript(maliciousId, tempDir)

        // settings put global private_dns_specifier 'abc'\''def'
        val lines = script.lines().map { it.trim() }
        val dnsSpecifierLine = lines.find { it.startsWith("settings put global private_dns_specifier") }
        assertTrue("Single quotes should be escaped: $dnsSpecifierLine", dnsSpecifierLine == "settings put global private_dns_specifier 'abc'\\''def'")
    }

    @Test
    fun testNextDnsIdValidation() {
        assertTrue(RootUtil.isValidNextDnsId("abcdef"))
        assertTrue(RootUtil.isValidNextDnsId("abc.123-def"))
        assertFalse(RootUtil.isValidNextDnsId("abc; echo hello"))
        assertFalse(RootUtil.isValidNextDnsId("abc'def"))
        assertFalse(RootUtil.isValidNextDnsId("  "))
        assertFalse(RootUtil.isValidNextDnsId(""))
    }

    @Test
    fun testEnablePrivacyModeValidation() = runTest {
        val result = RootUtil.enablePrivacyMode("abc; echo hello", "/tmp/test")
        assertTrue("Should return failure for invalid ID", result.isFailure)
        assertTrue("Should contain error message", result.exceptionOrNull()?.message == "Invalid NextDNS ID")
    }
}
