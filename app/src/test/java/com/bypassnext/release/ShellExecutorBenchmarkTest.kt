package com.bypassnext.release

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.system.measureTimeMillis

class ShellExecutorBenchmarkTest {

    @Test
    fun benchmarkProcessCreation() = runBlocking {
        // Use "sh" to benchmark the persistent shell implementation
        val executor = RootUtil.createDefaultShellExecutor("sh")
        val iterations = 50

        // Warm up
        executor.execute("echo start")

        val time = measureTimeMillis {
            repeat(iterations) {
                // We use a simple echo command to test throughput
                val result = executor.execute("echo test")
                if (result.isFailure) {
                    throw RuntimeException("Command failed: ${result.exceptionOrNull()}")
                }
            }
        }

        println("Benchmark: $iterations executions took $time ms")
        println("Average time per execution: ${time.toFloat() / iterations} ms")
    }
}
