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
    val retryingFailed: Boolean = false,
    val message: String? = null,
)

class StatusViewModel(private val repository: PhotoSyncRepository) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val retryingFailed = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val pcReachable = MutableStateFlow<Boolean?>(null)

    val state: StateFlow<StatusUiState> = combine(
        repository.observeStatus(),
        repository.observePending(),
        combine(refreshing, retryingFailed, message, pcReachable) { isRefreshing, isRetrying, msg, reachable ->
            AuxState(isRefreshing, isRetrying, msg, reachable)
        },
    ) { snapshot, pending, aux ->
        StatusUiState(
            snapshot = snapshot.copy(pcReachable = aux.reachable),
            pending = pending,
            refreshing = aux.refreshing,
            retryingFailed = aux.retryingFailed,
            message = aux.message,
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
        if (refreshing.value || retryingFailed.value) return
        viewModelScope.launch {
            refreshing.value = true
            val result = runCatching { repository.triggerSyncNow() }
            pcReachable.value = repository.checkHealth()
            refreshing.value = false
            result.onFailure { message.value = it.message ?: "Sync failed — will retry automatically." }
        }
    }

    /** Explicit Retry on the failed-uploads banner. */
    fun retryFailed() {
        if (retryingFailed.value || refreshing.value) return
        viewModelScope.launch {
            retryingFailed.value = true
            val result = runCatching { repository.retryFailedUploads() }
            pcReachable.value = repository.checkHealth()
            retryingFailed.value = false
            result
                .onSuccess { count ->
                    message.value = if (count > 0) {
                        "Retrying $count failed upload(s)…"
                    } else {
                        "Sync started"
                    }
                }
                .onFailure { message.value = it.message ?: "Couldn't start retry." }
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    private data class AuxState(
        val refreshing: Boolean,
        val retryingFailed: Boolean,
        val message: String?,
        val reachable: Boolean?,
    )
}
