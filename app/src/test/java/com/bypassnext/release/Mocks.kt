package com.bypassnext.release

class MockPrivacyRepository : PrivacyRepository {
    var isRootAvailableResponse = true
    var isPrivacyModeEnabledResponse = false
    // Changing to Result to match interface
    var enablePrivacyModeResponse: Result<String> = Result.success("Privacy Mode Activated")
    var disablePrivacyModeResponse: Result<String> = Result.success("Privacy Mode Deactivated")

    var enablePrivacyModeCalled = false
    var disablePrivacyModeCalled = false

    override suspend fun isRootAvailable(): Boolean = isRootAvailableResponse
    override suspend fun isPrivacyModeEnabled(nextDnsId: String): Boolean = isPrivacyModeEnabledResponse
    override suspend fun enablePrivacyMode(nextDnsId: String, tempDir: String): Result<String> {
        enablePrivacyModeCalled = true
        return enablePrivacyModeResponse
    }
    override suspend fun disablePrivacyMode(tempDir: String): Result<String> {
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

    override suspend fun execute(command: String): Result<String> {
        executedCommands.add(command)
        commandsToThrow[command]?.let { return Result.failure(it) }

        // Find a matching response or return empty string
        // Check for exact match first, then check if key is contained in command
        val response = commandResponses[command] ?: commandResponses.entries.find { command.contains(it.key) }?.value ?: ""

        // Compatibility: if response starts with "Error", treat as failure
        if (response.startsWith("Error")) {
            return Result.failure(Exception(response))
        }

        return Result.success(response)
    }
}
