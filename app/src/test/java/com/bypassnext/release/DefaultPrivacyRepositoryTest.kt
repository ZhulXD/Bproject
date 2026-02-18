package com.bypassnext.release

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultPrivacyRepositoryTest {

    private lateinit var repository: DefaultPrivacyRepository
    private lateinit var mockShellExecutor: TestMockShellExecutor
    private val TEST_DNS_ID = "test.dns.id"

    @Before
    fun setUp() {
        mockShellExecutor = TestMockShellExecutor()
        RootUtil.setShellExecutor(mockShellExecutor)
        repository = DefaultPrivacyRepository()
    }

    @After
    fun tearDown() {
        RootUtil.setShellExecutor(RootUtil.createDefaultShellExecutor())
    }

    @Test
    fun testIsRootAvailable_True() = runTest {
        mockShellExecutor.commandResponses["id"] = "uid=0(root) gid=0(root) groups=0(root)"
        assertTrue("Should return true when id command succeeds", repository.isRootAvailable())
    }

    @Test
    fun testIsRootAvailable_False() = runTest {
        mockShellExecutor.commandResponses["id"] = "Error: su denied"
        assertFalse("Should return false when id command fails", repository.isRootAvailable())
    }

    @Test
    fun testIsPrivacyModeEnabled_True() = runTest {
        val combinedCommand = "settings get global private_dns_mode; settings get global private_dns_specifier"
        mockShellExecutor.commandResponses[combinedCommand] = "hostname\n$TEST_DNS_ID"
        assertTrue("Should return true when DNS settings match", repository.isPrivacyModeEnabled(TEST_DNS_ID))
    }

    @Test
    fun testIsPrivacyModeEnabled_False() = runTest {
        val combinedCommand = "settings get global private_dns_mode; settings get global private_dns_specifier"
        mockShellExecutor.commandResponses[combinedCommand] = "off\n$TEST_DNS_ID"
        assertFalse("Should return false when DNS mode is off", repository.isPrivacyModeEnabled(TEST_DNS_ID))
    }

    @Test
    fun testEnablePrivacyMode() = runTest {
        val tempDir = "/data/local/tmp/test"
        val result = repository.enablePrivacyMode(TEST_DNS_ID, tempDir)
        assertTrue("Should return success", result.isSuccess)

        val executedScript = mockShellExecutor.executedCommands.joinToString("\n")

        // Verify key commands are present, ensuring delegation to RootUtil
        assertTrue("Should set DNS mode", executedScript.contains("settings put global private_dns_mode hostname"))
        assertTrue("Should set DNS specifier", executedScript.contains("settings put global private_dns_specifier '$TEST_DNS_ID'"))
        assertTrue("Should use provided temp dir", executedScript.contains("TEMP_DIR='$tempDir'"))
    }

    @Test
    fun testDisablePrivacyMode() = runTest {
        val tempDir = "/data/local/tmp/test"
        val result = repository.disablePrivacyMode(tempDir)
        assertTrue("Should return success", result.isSuccess)

        val executedScript = mockShellExecutor.executedCommands.joinToString("\n")

        assertTrue("Should reset DNS mode", executedScript.contains("settings put global private_dns_mode off"))
        assertTrue("Should delete DNS specifier", executedScript.contains("settings delete global private_dns_specifier"))
        assertTrue("Should clean up temp dir", executedScript.contains("rm -rf '$tempDir'"))
    }
}
