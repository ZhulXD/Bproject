package com.bypassnext.release

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.ArrayDeque
import java.util.ArrayList

data class MainUiState(
    val isRootGranted: Boolean = false,
    val isCheckingRoot: Boolean = true,
    val isPrivacyActive: Boolean = false,
    val logs: List<String> = emptyList(),
    val isBusy: Boolean = false
)

class MainViewModel(
    private val repository: PrivacyRepository,
    private val stringProvider: StringProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {
    private val dateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Custom scope since lifecycle-viewmodel-ktx is not available or we want to control dispatcher
    private val viewModelScope = CoroutineScope(dispatcher + SupervisorJob())

    private val MAX_LOG_SIZE = 1000
    private val logBuffer = ArrayDeque<String>(MAX_LOG_SIZE)

    init {
        checkRoot()
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
    }

    fun checkRoot() {
        log(stringProvider.getString(R.string.checking_root_access))
        viewModelScope.launch {
            val hasRoot = repository.isRootAvailable()
            if (hasRoot) {
                log(stringProvider.getString(R.string.root_access_granted))
                _uiState.update { it.copy(isRootGranted = true, isCheckingRoot = false) }
            } else {
                log(stringProvider.getString(R.string.root_access_denied))
                _uiState.update { it.copy(isRootGranted = false, isCheckingRoot = false) }
            }
        }
    }

    fun checkPrivacyStatus(nextDnsId: String) {
        if (nextDnsId.isEmpty()) return

        viewModelScope.launch {
            val isActive = repository.isPrivacyModeEnabled(nextDnsId)
            _uiState.update { it.copy(isPrivacyActive = isActive) }
            if (isActive) {
                log(stringProvider.getString(R.string.privacy_mode_detected_active))
            }
        }
    }

    fun togglePrivacy(nextDnsId: String, tempDir: String) {
        if (_uiState.value.isBusy) return

        if (_uiState.value.isPrivacyActive) {
            disablePrivacy(tempDir)
        } else {
            enablePrivacy(nextDnsId, tempDir)
        }
    }

    private fun enablePrivacy(nextDnsId: String, tempDir: String) {
        log(stringProvider.getString(R.string.activating_privacy_mode))
        _uiState.update { it.copy(isBusy = true) }

        viewModelScope.launch {
            val result = repository.enablePrivacyMode(nextDnsId, tempDir)
            log(result)
            if (!result.startsWith("Error")) {
                _uiState.update { it.copy(isPrivacyActive = true, isBusy = false) }
            } else {
                log(stringProvider.getString(R.string.failed_to_activate))
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun disablePrivacy(tempDir: String) {
        log(stringProvider.getString(R.string.deactivating_privacy_mode))
        _uiState.update { it.copy(isBusy = true) }

        viewModelScope.launch {
            val result = repository.disablePrivacyMode(tempDir)
            log(result)
            if (!result.startsWith("Error")) {
                _uiState.update { it.copy(isPrivacyActive = false, isBusy = false) }
            } else {
                log(stringProvider.getString(R.string.failed_to_deactivate))
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }


    private fun log(message: String) {
        val timestamp = dateFormat.get()!!.format(Date())
        val logEntry = "[$timestamp] $message"

        val newLogs = synchronized(logBuffer) {
            if (logBuffer.size >= MAX_LOG_SIZE) {
                logBuffer.pollFirst()
            }
            logBuffer.addLast(logEntry)
            ArrayList(logBuffer)
        }

        _uiState.update { it.copy(logs = newLogs) }
    }
}

class MainViewModelFactory(
    private val repository: PrivacyRepository,
    private val stringProvider: StringProvider
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, stringProvider) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
