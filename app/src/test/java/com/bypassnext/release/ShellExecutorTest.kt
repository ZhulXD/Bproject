package com.bypassnext.release

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.TimeoutCancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class ShellExecutorTest {

    @Test
    fun testExecuteEcho() = runTest {
        val executor = RootUtil.createDefaultShellExecutor(shell = "sh")
        val result = executor.execute("echo hello")
        if (result.isFailure) {
             throw RuntimeException("Echo failed: " + result.exceptionOrNull())
        }
        assertEquals("hello", result.getOrNull())
        executor.close()
    }

    @Test
    fun testExecuteSleepTimeout() = runTest {
        val executor = RootUtil.createDefaultShellExecutor(shell = "sh", timeoutMs = 500L)

        val start = System.currentTimeMillis()
        val result = executor.execute("sleep 2")
        val duration = System.currentTimeMillis() - start

        if (result.isSuccess) {
             val output = result.getOrNull()
             throw RuntimeException("Should have failed due to timeout. Duration: ${duration}ms. Output: '$output'")
        }

        val exception = result.exceptionOrNull()
        val isTimeout = exception is TimeoutException ||
                        exception is TimeoutCancellationException ||
                        exception?.message?.contains("timed out") == true

        if (!isTimeout) {
             throw RuntimeException("Failed with unexpected error: $exception")
        }

        executor.close()
    }
}
