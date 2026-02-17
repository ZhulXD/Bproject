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
