package com.phonesync.app.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonesync.app.data.local.LocalAssetEntity
import com.phonesync.app.data.repository.PhotoSyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArchiveUiState(
    val archivable: List<LocalAssetEntity> = emptyList(),
    val selected: Set<String> = emptySet(),
    val busy: Boolean = false,
    val message: String? = null,
) {
    val selectedBytes: Long get() = archivable.filter { it.clientAssetId in selected }.sumOf { it.sizeBytes }
    val allSelected: Boolean get() = archivable.isNotEmpty() && selected.size == archivable.size
}

/** Pure selection algebra, split out so it's trivially unit-testable without a repository. */
object SelectionReducer {
    fun toggle(selected: Set<String>, id: String): Set<String> =
        if (id in selected) selected - id else selected + id

    fun toggleAll(selected: Set<String>, allIds: List<String>): Set<String> =
        if (selected.size == allIds.size) emptySet() else allIds.toSet()
}

class ArchiveViewModel(private val repository: PhotoSyncRepository) : ViewModel() {

    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<ArchiveUiState> = combine(
        repository.observeArchivable(),
        selected,
        busy,
        message,
    ) { archivable, selectedIds, isBusy, msg ->
        // Drop selections for items that left the archivable set (e.g. already freed) —
        // read-only here; the actual reconciliation happens in the init{} collector below
        // so this transform stays a pure function of its inputs.
        val validIds = archivable.map { it.clientAssetId }.toSet()
        ArchiveUiState(archivable, selectedIds.intersect(validIds), isBusy, msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchiveUiState())

    init {
        // Keep `selected` itself in sync (not just the derived UI state) so a stale id
        // doesn't get "resurrected" into the selection if it reappears later.
        viewModelScope.launch {
            repository.observeArchivable().collect { archivable ->
                val validIds = archivable.map { it.clientAssetId }.toSet()
                selected.update { it.intersect(validIds) }
            }
        }
    }

    fun toggle(id: String) {
        selected.value = SelectionReducer.toggle(selected.value, id)
    }

    fun toggleAll() {
        selected.value = SelectionReducer.toggleAll(selected.value, state.value.archivable.map { it.clientAssetId })
    }

    fun dismissMessage() {
        message.value = null
    }

    /** Step 1: ask the server to confirm archive for the selection. Returns the confirmed
     * entities so the caller can attempt the on-device delete (which needs an Activity result
     * launcher, so it must happen in the composable, not here). */
    fun archiveOnServer(onConfirmed: (List<LocalAssetEntity>) -> Unit) {
        val toArchive = state.value.archivable.filter { it.clientAssetId in state.value.selected }
        if (toArchive.isEmpty()) return
        busy.value = true
        message.value = null
        viewModelScope.launch {
            val confirmed = runCatching { repository.archiveOnServer(toArchive) }.getOrElse {
                message.value = it.message ?: "Couldn't reach the server — try again."
                busy.value = false
                return@launch
            }
            if (confirmed.isEmpty()) {
                message.value = "Nothing confirmed by the server."
                busy.value = false
                return@launch
            }
            onConfirmed(confirmed)
        }
    }

    /** Step 2, after the on-device delete attempt completes (immediately or via system dialog). */
    fun onDeleteFinished(deleted: List<LocalAssetEntity>, failedCount: Int) {
        viewModelScope.launch {
            if (deleted.isNotEmpty()) repository.markLocalDeletedAfterArchive(deleted)
            selected.value = emptySet()
            busy.value = false
            message.value = when {
                failedCount == 0 -> "Freed ${deleted.size} item(s) from phone storage."
                deleted.isEmpty() -> "Delete cancelled — files remain on phone (still archived on PC)."
                else -> "Freed ${deleted.size} item(s); $failedCount couldn't be removed automatically."
            }
        }
    }

    fun onDeleteCancelled() {
        busy.value = false
        message.value = "Delete cancelled — files remain on phone (still archived on PC)."
    }
}
