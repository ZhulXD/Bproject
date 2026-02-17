package com.bypassnext.release

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class MainViewModelFactoryTest {

    private lateinit var repository: MockPrivacyRepository
    private lateinit var stringProvider: MockStringProvider
    private lateinit var factory: MainViewModelFactory

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = MockPrivacyRepository()
        stringProvider = MockStringProvider()
        factory = MainViewModelFactory(repository, stringProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `create returns MainViewModel when requested`() {
        val viewModel = factory.create(MainViewModel::class.java)
        assertNotNull(viewModel)
        // Check if it's actually a MainViewModel
        assert(viewModel is MainViewModel)
    }

    @Test
    fun `create throws IllegalArgumentException for unknown ViewModel`() {
        class UnknownViewModel : ViewModel()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }

        assert(exception.message == "Unknown ViewModel class")
    }
}
