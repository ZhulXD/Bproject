package com.bypassnext.release

sealed class LogUpdate {
    object NoChange : LogUpdate()
    data class Append(val newText: String, val addNewline: Boolean) : LogUpdate()
    data class Replace(val fullText: String) : LogUpdate()
}

class LogDiffer {
    private var lastLogList: List<String> = emptyList()

    fun computeUpdate(newLogs: List<String>): LogUpdate {
        if (newLogs === lastLogList) return LogUpdate.NoChange

        val oldLogs = lastLogList
        val oldSize = oldLogs.size
        val newSize = newLogs.size

        val isAppend = if (newSize > oldSize) {
            // Check if new list starts with old list
            newLogs.subList(0, oldSize) == oldLogs
        } else {
            false
        }

        val update = if (isAppend) {
            val newItems = newLogs.subList(oldSize, newSize)
            val newText = newItems.joinToString("\n")
            LogUpdate.Append(newText, addNewline = oldSize > 0)
        } else {
            LogUpdate.Replace(newLogs.joinToString("\n"))
        }

        lastLogList = newLogs
        return update
    }
}
