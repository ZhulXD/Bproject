package com.bypassnext.release

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private lateinit var repository: MockPrivacyRepository
    private lateinit var stringProvider: MockStringProvider
    private lateinit var viewModel: MainViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val TEST_DNS_ID = "test.dns.id"
    private val TEST_TEMP_DIR = "/data/local/tmp/test_certs"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = MockPrivacyRepository()
        stringProvider = MockStringProvider()
        // Initialize ViewModel. It calls checkRoot() in init block.
        // checkRoot() no longer calls checkPrivacyStatus().
        viewModel = MainViewModel(repository, stringProvider, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state checks root`() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isRootGranted)
        assertFalse(state.isCheckingRoot)
        assertFalse(state.isPrivacyActive)
    }

    @Test
    fun `initial state handles no root`() = runTest(testDispatcher) {
        repository.isRootAvailableResponse = false
        // Re-init viewModel to trigger init block with new mock response
        viewModel = MainViewModel(repository, stringProvider, testDispatcher)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRootGranted)
        assertFalse(state.isCheckingRoot)
    }

    @Test
    fun `checkPrivacyStatus updates state`() = runTest(testDispatcher) {
        repository.isPrivacyModeEnabledResponse = true
        testScheduler.advanceUntilIdle()

        // Explicitly call checkPrivacyStatus as it's not called in init anymore
        viewModel.checkPrivacyStatus(TEST_DNS_ID)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isRootGranted)
        assertTrue(state.isPrivacyActive)
    }

    @Test
    fun `togglePrivacy enables privacy when inactive`() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()

        viewModel.togglePrivacy(TEST_DNS_ID, TEST_TEMP_DIR)
        testScheduler.advanceUntilIdle()

        assertTrue(repository.enablePrivacyModeCalled)
        assertTrue(viewModel.uiState.value.isPrivacyActive)
        // Check logs contain success message
        val lastLog = viewModel.uiState.value.logs.lastOrNull() ?: ""
        assertTrue(lastLog.contains("Privacy Mode Activated"))
    }

    @Test
    fun `togglePrivacy disables privacy when active`() = runTest(testDispatcher) {
        repository.isPrivacyModeEnabledResponse = true
        testScheduler.advanceUntilIdle()

        // Set initial state to active
        viewModel.checkPrivacyStatus(TEST_DNS_ID)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPrivacyActive)

        viewModel.togglePrivacy(TEST_DNS_ID, TEST_TEMP_DIR)
        testScheduler.advanceUntilIdle()

        assertTrue(repository.disablePrivacyModeCalled)
        assertFalse(viewModel.uiState.value.isPrivacyActive)
    }

    @Test
    fun `togglePrivacy handles failure`() = runTest(testDispatcher) {
        repository.enablePrivacyModeResponse = "Error: Failed"
        testScheduler.advanceUntilIdle()

        viewModel.togglePrivacy(TEST_DNS_ID, TEST_TEMP_DIR)
        testScheduler.advanceUntilIdle()

        assertTrue(repository.enablePrivacyModeCalled)
        assertFalse(viewModel.uiState.value.isPrivacyActive)
        assertTrue(viewModel.uiState.value.logs.any { it.contains("Res(") || it.contains("Error: Failed") })
    }
}
