package com.bypassnext.release

import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.measureNanoTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.Callable

class DateFormatBenchmark {

    @Test
    fun benchmarkDateFormattingMultiThreaded() {
        val iterations = 10000
        val threadCount = 8
        val date = Date()
        val executor = Executors.newFixedThreadPool(threadCount)

        // 1. Baseline: Create every time
        val baselineTime = measureNanoTime {
            val tasks = List(threadCount) {
                Callable {
                    repeat(iterations) {
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(date)
                    }
                }
            }
            executor.invokeAll(tasks)
        }
        println("Baseline (Create every time, MT): ${baselineTime / 1_000_000.0} ms")

        // 2. Synchronized Instance
        val instanceDateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val synchronizedTime = measureNanoTime {
            val tasks = List(threadCount) {
                Callable {
                    repeat(iterations) {
                        synchronized(instanceDateFormat) {
                            instanceDateFormat.format(date)
                        }
                    }
                }
            }
            executor.invokeAll(tasks)
        }
        println("Synchronized Instance (MT): ${synchronizedTime / 1_000_000.0} ms")

        // 3. ThreadLocal
        val threadLocalFormat = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            }
        }
        val threadLocalTime = measureNanoTime {
            val tasks = List(threadCount) {
                Callable {
                    repeat(iterations) {
                        threadLocalFormat.get()!!.format(date)
                    }
                }
            }
            executor.invokeAll(tasks)
        }
        println("ThreadLocal (MT): ${threadLocalTime / 1_000_000.0} ms")

        executor.shutdown()
    }
}
