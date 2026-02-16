package com.bypassnext.release

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
    override suspend fun isPrivacyModeEnabled(): Boolean = isPrivacyModeEnabledResponse
    override suspend fun enablePrivacyMode(): String {
        enablePrivacyModeCalled = true
        return enablePrivacyModeResponse
    }
    override suspend fun disablePrivacyMode(): String {
        disablePrivacyModeCalled = true
        return disablePrivacyModeResponse
    }
}

class MockStringProvider : StringProvider {
    override fun getString(resId: Int): String = "Res($resId)"
    override fun getString(resId: Int, vararg args: Any): String = "Res($resId, ${args.joinToString()})"
}

class MainViewModelTest {

    private lateinit var repository: MockPrivacyRepository
    private lateinit var stringProvider: MockStringProvider
    private lateinit var viewModel: MainViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = MockPrivacyRepository()
        stringProvider = MockStringProvider()
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
        viewModel = MainViewModel(repository, stringProvider, testDispatcher)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRootGranted)
        assertFalse(state.isCheckingRoot)
    }

    @Test
    fun `initial state checks privacy status if root granted`() = runTest(testDispatcher) {
        repository.isPrivacyModeEnabledResponse = true
        viewModel = MainViewModel(repository, stringProvider, testDispatcher)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isRootGranted)
        assertTrue(state.isPrivacyActive)
    }

    @Test
    fun `togglePrivacy enables privacy when inactive`() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()

        viewModel.togglePrivacy()
        testScheduler.advanceUntilIdle()

        assertTrue(repository.enablePrivacyModeCalled)
        assertTrue(viewModel.uiState.value.isPrivacyActive)
        assertEquals("Privacy Mode Activated", viewModel.uiState.value.logs.last().substringAfter("] "))
    }

    @Test
    fun `togglePrivacy disables privacy when active`() = runTest(testDispatcher) {
        repository.isPrivacyModeEnabledResponse = true
        viewModel = MainViewModel(repository, stringProvider, testDispatcher)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPrivacyActive)

        viewModel.togglePrivacy()
        testScheduler.advanceUntilIdle()

        assertTrue(repository.disablePrivacyModeCalled)
        assertFalse(viewModel.uiState.value.isPrivacyActive)
    }

    @Test
    fun `togglePrivacy handles failure`() = runTest(testDispatcher) {
        repository.enablePrivacyModeResponse = "Error: Failed"
        testScheduler.advanceUntilIdle()

        viewModel.togglePrivacy()
        testScheduler.advanceUntilIdle()

        assertTrue(repository.enablePrivacyModeCalled)
        assertFalse(viewModel.uiState.value.isPrivacyActive)
        assertTrue(viewModel.uiState.value.logs.any { it.contains("Res(") || it.contains("Error: Failed") })
    }
}
