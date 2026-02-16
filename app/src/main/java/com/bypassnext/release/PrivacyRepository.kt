package com.bypassnext.release

interface PrivacyRepository {
    suspend fun isRootAvailable(): Boolean
    suspend fun isPrivacyModeEnabled(): Boolean
    suspend fun enablePrivacyMode(): String
    suspend fun disablePrivacyMode(): String
}

class DefaultPrivacyRepository : PrivacyRepository {
    override suspend fun isRootAvailable(): Boolean {
        return RootUtil.isRootAvailable()
    }

    override suspend fun isPrivacyModeEnabled(): Boolean {
        return RootUtil.isPrivacyModeEnabled()
    }

    override suspend fun enablePrivacyMode(): String {
        return RootUtil.enablePrivacyMode()
    }

    override suspend fun disablePrivacyMode(): String {
        return RootUtil.disablePrivacyMode()
    }
}
