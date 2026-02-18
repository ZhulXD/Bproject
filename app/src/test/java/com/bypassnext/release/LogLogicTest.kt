package com.bypassnext.release

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class LogLogicTest {

    @Test
    fun testNoChange() {
        val differ = LogDiffer()
        val logs = listOf("1", "2")

        differ.computeUpdate(logs) // Initial load

        val update = differ.computeUpdate(logs) // Same list
        assertTrue(update is LogUpdate.NoChange)
    }

    @Test
    fun testInitialLoad() {
        val differ = LogDiffer()
        val logs = listOf("Line 1", "Line 2")

        val update = differ.computeUpdate(logs)
        // Initial load is an append from empty state
        assertTrue(update is LogUpdate.Append)
        val append = update as LogUpdate.Append
        assertEquals("Line 1\nLine 2", append.newText)
        assertEquals(false, append.addNewline)
    }

    @Test
    fun testAppend() {
        val differ = LogDiffer()
        val logs1 = listOf("Line 1")
        differ.computeUpdate(logs1)

        val logs2 = listOf("Line 1", "Line 2")
        val update = differ.computeUpdate(logs2)

        assertTrue(update is LogUpdate.Append)
        val append = update as LogUpdate.Append
        assertEquals("Line 2", append.newText)
        assertTrue(append.addNewline)
    }

    @Test
    fun testRotation() {
        val differ = LogDiffer()
        val logs1 = listOf("Line 1", "Line 2")
        differ.computeUpdate(logs1)

        val logs2 = listOf("Line 2", "Line 3") // Same size, different content
        val update = differ.computeUpdate(logs2)

        assertTrue(update is LogUpdate.Replace)
        assertEquals("Line 2\nLine 3", (update as LogUpdate.Replace).fullText)
    }

    @Test
    fun testClear() {
        val differ = LogDiffer()
        val logs1 = listOf("Line 1")
        differ.computeUpdate(logs1)

        val logs2 = emptyList<String>()
        val update = differ.computeUpdate(logs2)

        assertTrue(update is LogUpdate.Replace)
        assertEquals("", (update as LogUpdate.Replace).fullText)
    }

    @Test
    fun testAppendToEmpty() {
        val differ = LogDiffer()
        val logs1 = emptyList<String>()
        differ.computeUpdate(logs1)

        val logs2 = listOf("Line 1")
        val update = differ.computeUpdate(logs2)

        assertTrue(update is LogUpdate.Append)
        val append = update as LogUpdate.Append
        assertEquals("Line 1", append.newText)
        assertEquals(false, append.addNewline) // Should not add newline if old was empty
    }
}
