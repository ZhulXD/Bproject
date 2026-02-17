package com.bypassnext.release

class MockPrivacyRepository : PrivacyRepository {
    var isRootAvailableResponse = true
    var isPrivacyModeEnabledResponse = false
    var enablePrivacyModeResponse = "Privacy Mode Activated"
    var disablePrivacyModeResponse = "Privacy Mode Deactivated"

    var enablePrivacyModeCalled = false
    var disablePrivacyModeCalled = false

    override suspend fun isRootAvailable(): Boolean = isRootAvailableResponse
    override suspend fun isPrivacyModeEnabled(nextDnsId: String): Boolean = isPrivacyModeEnabledResponse
    override suspend fun enablePrivacyMode(nextDnsId: String, tempDir: String): String {
        enablePrivacyModeCalled = true
        return enablePrivacyModeResponse
    }
    override suspend fun disablePrivacyMode(tempDir: String): String {
        disablePrivacyModeCalled = true
        return disablePrivacyModeResponse
    }
}

class MockStringProvider : StringProvider {
    override fun getString(resId: Int): String = "Res($resId)"
    override fun getString(resId: Int, vararg args: Any): String = "Res($resId, ${args.joinToString()})"
}

class TestMockShellExecutor : ShellExecutor {
    val executedCommands = mutableListOf<String>()
    val commandResponses = mutableMapOf<String, String>()
    val commandsToThrow = mutableMapOf<String, Exception>()

    override suspend fun execute(command: String): String {
        executedCommands.add(command)
        commandsToThrow[command]?.let { throw it }
        // Find a matching response or return empty string
        // Check for exact match first, then check if key is contained in command
        return commandResponses[command] ?: commandResponses.entries.find { command.contains(it.key) }?.value ?: ""
    }
}
