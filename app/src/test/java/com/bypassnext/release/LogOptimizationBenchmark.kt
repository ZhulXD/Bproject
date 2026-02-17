package com.bypassnext.release

import org.junit.Test
import kotlin.system.measureNanoTime

class LogOptimizationBenchmark {

    @Test
    fun benchmarkLogJoining() {
        val logCount = 1000
        val logs = List(logCount) { "Log entry number $it which is somewhat long to simulate real logs" }

        // Baseline: joinToString the whole list many times (simulating render calls)
        val iterations = 100

        val baselineTime = measureNanoTime {
            repeat(iterations) {
                val logsText = logs.joinToString("\n")
                // Simulate comparison and assignment
                val currentText = logsText // In real life this is tvLog.text.toString()
                if (currentText != logsText) {
                    // update
                }
            }
        }

        println("Baseline time for $iterations iterations with $logCount logs: ${baselineTime / 1_000_000.0} ms")

        // Optimized: only join new logs
        var lastRenderedCount = 0
        val optimizedTime = measureNanoTime {
            repeat(iterations) {
                // In each iteration we "add" one more log or just use the same set
                // To be fair, let's say we are at the end
                if (logs.size > lastRenderedCount) {
                    val newLogs = logs.subList(lastRenderedCount, logs.size)
                    val newText = newLogs.joinToString("\n")
                    // append newText
                    lastRenderedCount = logs.size
                }
            }
        }

        println("Optimized time for $iterations iterations with $logCount logs: ${optimizedTime / 1_000_000.0} ms")

        // Measure with incremental growth
        lastRenderedCount = 0
        val incrementalOptimizedTime = measureNanoTime {
            for (i in 1..logCount) {
                val currentLogs = logs.subList(0, i)
                if (currentLogs.size > lastRenderedCount) {
                    val newLogs = currentLogs.subList(lastRenderedCount, currentLogs.size)
                    val newText = newLogs.joinToString("\n")
                    lastRenderedCount = currentLogs.size
                }
            }
        }

        val incrementalBaselineTime = measureNanoTime {
            for (i in 1..logCount) {
                val currentLogs = logs.subList(0, i)
                val logsText = currentLogs.joinToString("\n")
                val currentText = logsText
                if (currentText != logsText) { }
            }
        }

        println("Incremental Baseline time: ${incrementalBaselineTime / 1_000_000.0} ms")
        println("Incremental Optimized time: ${incrementalOptimizedTime / 1_000_000.0} ms")
    }
}
