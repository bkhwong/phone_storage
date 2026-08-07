package com.phonesync.app.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonesync.app.data.local.LocalAssetEntity
import com.phonesync.app.data.repository.PhotoSyncRepository
import com.phonesync.app.data.repository.StatusSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val HEALTH_POLL_INTERVAL_MS = 20_000L

data class StatusUiState(
    val snapshot: StatusSnapshot = StatusSnapshot(),
    val pending: List<LocalAssetEntity> = emptyList(),
    val refreshing: Boolean = false,
    val message: String? = null,
)

class StatusViewModel(private val repository: PhotoSyncRepository) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val pcReachable = MutableStateFlow<Boolean?>(null)

    val state: StateFlow<StatusUiState> = combine(
        repository.observeStatus(),
        repository.observePending(),
        refreshing,
        message,
        pcReachable,
    ) { snapshot, pending, isRefreshing, msg, reachable ->
        StatusUiState(
            snapshot = snapshot.copy(pcReachable = reachable),
            pending = pending,
            refreshing = isRefreshing,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatusUiState())

    init {
        viewModelScope.launch {
            while (isActive) {
                pcReachable.value = repository.checkHealth()
                delay(HEALTH_POLL_INTERVAL_MS)
            }
        }
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            val result = runCatching { repository.triggerSyncNow() }
            pcReachable.value = repository.checkHealth()
            refreshing.value = false
            result.onFailure { message.value = it.message ?: "Sync failed — will retry automatically." }
        }
    }

    fun dismissMessage() {
        message.value = null
    }
}
