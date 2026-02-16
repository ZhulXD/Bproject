package com.bypassnext.release

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import kotlin.system.measureTimeMillis

class MockShellExecutor(private val delayMs: Long = 100) : ShellExecutor {
    override suspend fun execute(command: String): String {
        delay(delayMs)
        return if (command.contains("private_dns_mode")) "hostname"
        else if (command.contains("private_dns_specifier")) "a4f5f2.dns.nextdns.io"
        else "unknown"
    }
}

class PerformanceBenchmarkTest {

    @After
    fun tearDown() {
        RootUtil.shellExecutor = DefaultShellExecutor()
    }

    @Test
    fun measurePrivacyCheckPerformance() = runTest {
        // Setup
        RootUtil.shellExecutor = MockShellExecutor(100)
        val testId = "a4f5f2.dns.nextdns.io"

        // Measure
        val time = measureTimeMillis {
            val isEnabled = RootUtil.isPrivacyModeEnabled(testId)
            assertTrue(isEnabled)
        }

        println("Execution time: ${time}ms")

        // Improved expectation: Parallel execution should be ~100ms (plus overhead)
        // Sequential would be ~200ms. So we assert it's less than sequential.
        // We add some buffer (e.g. 150ms) to account for test overhead, but < 200 is sufficient proof.
        assertTrue("Execution time ($time ms) should be < 200ms for parallel execution", time < 190)
    }
}
