package com.phonesync.app.ui.migration

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

data class MigrationUiState(
    val pendingCount: Int = 0,
    val allowCellular: Boolean = false,
    val starting: Boolean = false,
)

class MigrationViewModel(
    private val repository: PhotoSyncRepository,
    prefs: SecurePrefs,
) : ViewModel() {

    private val starting = MutableStateFlow(false)

    val state: StateFlow<MigrationUiState> = combine(
        repository.observePendingCount(),
        prefs.allowCellular,
        starting,
    ) { pendingCount, allowCellular, isStarting ->
        MigrationUiState(pendingCount, allowCellular, isStarting)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MigrationUiState())

    fun start() {
        if (starting.value) return
        starting.value = true
        viewModelScope.launch {
            runCatching { repository.startMigration() }
            starting.value = false
        }
    }

    fun cancel() {
        repository.cancelMigration()
    }
}
