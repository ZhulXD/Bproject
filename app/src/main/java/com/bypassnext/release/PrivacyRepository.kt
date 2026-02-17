package com.bypassnext.release

interface PrivacyRepository {
    suspend fun isRootAvailable(): Boolean
    suspend fun isPrivacyModeEnabled(nextDnsId: String): Boolean
    suspend fun enablePrivacyMode(nextDnsId: String, tempDir: String): Result<String>
    suspend fun disablePrivacyMode(tempDir: String): Result<String>
}

class DefaultPrivacyRepository : PrivacyRepository {
    override suspend fun isRootAvailable(): Boolean {
        return RootUtil.isRootAvailable()
    }

    override suspend fun isPrivacyModeEnabled(nextDnsId: String): Boolean {
        return RootUtil.isPrivacyModeEnabled(nextDnsId)
    }

    override suspend fun enablePrivacyMode(nextDnsId: String, tempDir: String): Result<String> {
        return RootUtil.enablePrivacyMode(nextDnsId, tempDir)
    }

    override suspend fun disablePrivacyMode(tempDir: String): Result<String> {
        return RootUtil.disablePrivacyMode(tempDir)
    }
}
