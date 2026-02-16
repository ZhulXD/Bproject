package com.bypassnext.release

interface PrivacyRepository {
    suspend fun isRootAvailable(): Boolean
    suspend fun isPrivacyModeEnabled(nextDnsId: String): Boolean
    suspend fun enablePrivacyMode(nextDnsId: String, tempDir: String): String
    suspend fun disablePrivacyMode(tempDir: String): String
}

class DefaultPrivacyRepository : PrivacyRepository {
    override suspend fun isRootAvailable(): Boolean {
        return RootUtil.isRootAvailable()
    }

    override suspend fun isPrivacyModeEnabled(nextDnsId: String): Boolean {
        return RootUtil.isPrivacyModeEnabled(nextDnsId)
    }

    override suspend fun enablePrivacyMode(nextDnsId: String, tempDir: String): String {
        return RootUtil.enablePrivacyMode(nextDnsId, tempDir)
    }

    override suspend fun disablePrivacyMode(tempDir: String): String {
        return RootUtil.disablePrivacyMode(tempDir)
    }
}
