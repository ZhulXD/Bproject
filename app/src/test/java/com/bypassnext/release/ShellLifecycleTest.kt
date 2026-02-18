package com.bypassnext.release

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Field

class ShellLifecycleTest {

    private fun getProcess(executor: Any): Process? {
        val processField = executor.javaClass.getDeclaredField("process")
        processField.isAccessible = true
        return processField.get(executor) as Process?
    }

    @Test
    fun testExplicitClose() = runBlocking {
        val executor = RootUtil.createDefaultShellExecutor("sh", 60000L)

        executor.execute("echo hello")

        var process = getProcess(executor)
        assertTrue("Process should be alive", process?.isAlive == true)

        executor.close()

        process = getProcess(executor)
        // Process field should be null after close
        assertTrue("Process field should be null after close", process == null)
    }

    @Test
    fun testTimeoutClose() = runBlocking {
        // Create executor with short timeout (e.g., 200ms)
        // We use a real dispatcher so delay() works as expected in real time
        val executor = RootUtil.createDefaultShellExecutor("sh", 200L)

        executor.execute("echo hello")

        var process = getProcess(executor)
        assertTrue("Process should be alive initially", process?.isAlive == true)

        // Wait for timeout + buffer
        Thread.sleep(500)

        process = getProcess(executor)
        assertTrue("Process field should be null after timeout", process == null)
    }
}
