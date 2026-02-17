package com.bypassnext.release

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test

class PerformanceBenchmarkTest {

    private class MockShellExecutor(private val delayMs: Long = 100) : ShellExecutor {
        var callCount = 0
        override suspend fun execute(command: String): String {
            callCount++
            delay(delayMs)
            // Handle combined command
            if (command.contains("private_dns_mode") && command.contains("private_dns_specifier")) {
                 return "hostname\na4f5f2.dns.nextdns.io"
            }
            // Handle individual commands (fallback/legacy)
            return if (command.contains("private_dns_mode")) "hostname"
            else if (command.contains("private_dns_specifier")) "a4f5f2.dns.nextdns.io"
            else "unknown"
        }
    }

    @After
    fun tearDown() {
        RootUtil.shellExecutor = DefaultShellExecutor()
    }

    @Test
    fun verifyPrivacyCheckCommandOptimization() = runTest {
        // Setup
        val mockExecutor = MockShellExecutor(100)
        RootUtil.shellExecutor = mockExecutor
        val testId = "a4f5f2.dns.nextdns.io"

        // Execute
        val isEnabled = RootUtil.isPrivacyModeEnabled(testId)
        assertTrue(isEnabled)

        // Verify optimization: Should use exactly 1 shell execution
        // We check call count instead of execution time to avoid flaky tests.
        assertEquals("Should combine commands into a single shell execution", 1, mockExecutor.callCount)
    }
}
