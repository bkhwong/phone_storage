package com.phonesync.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonesync.app.data.prefs.SecurePrefs
import com.phonesync.app.data.repository.PhotoSyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val serverUrl: String? = null,
    val intervalMinutes: Int = 60,
    val allowCellular: Boolean = false,
    val unpairing: Boolean = false,
)

class SettingsViewModel(
    private val repository: PhotoSyncRepository,
    prefs: SecurePrefs,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        prefs.serverBaseUrl,
        prefs.syncIntervalMinutes,
        prefs.allowCellular,
    ) { url, interval, cellular ->
        SettingsUiState(serverUrl = url, intervalMinutes = interval, allowCellular = cellular)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setSyncInterval(minutes: Int) = repository.updateSyncInterval(minutes)

    fun setAllowCellular(allow: Boolean) = repository.updateAllowCellular(allow)

    fun unpair(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.unpair()
            onDone()
        }
    }
}
