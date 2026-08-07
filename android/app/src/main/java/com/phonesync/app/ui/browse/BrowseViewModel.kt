package com.phonesync.app.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonesync.app.data.remote.AssetDto
import com.phonesync.app.data.repository.PhotoSyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BrowseFilter(val serverValue: String?, val label: String) {
    ALL(null, "All"),
    BACKED_UP("backed_up", "Backed up"),
    ARCHIVED("archived", "Archived"),
}

data class BrowseUiState(
    val filter: BrowseFilter = BrowseFilter.ALL,
    val items: List<AssetDto> = emptyList(),
    val cursor: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val discardTarget: AssetDto? = null,
) {
    val canLoadMore: Boolean get() = !loading && cursor != null
}

class BrowseViewModel(private val repository: PhotoSyncRepository) : ViewModel() {

    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    init {
        load(reset = true)
    }

    fun authHeader() = repository.authHeader()
    fun thumbnailUrl(id: String) = repository.thumbnailUrl(id)
    fun originalUrl(id: String) = repository.originalUrl(id)

    fun setFilter(filter: BrowseFilter) {
        if (filter == _state.value.filter) return
        _state.update { it.copy(filter = filter, items = emptyList(), cursor = null, error = null) }
        load(reset = true)
    }

    fun retry() = load(reset = _state.value.items.isEmpty())

    fun loadMore() {
        if (_state.value.canLoadMore) load(reset = false)
    }

    private fun load(reset: Boolean) {
        val current = _state.value
        if (current.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repository.browseAssets(
                    state = current.filter.serverValue,
                    cursor = if (reset) null else current.cursor,
                )
            }.onSuccess { (page, next) ->
                _state.update {
                    it.copy(
                        items = if (reset) page else it.items + page,
                        cursor = next,
                        loading = false,
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Browse failed — is the PC online?") }
            }
        }
    }

    fun requestDiscard(asset: AssetDto) {
        _state.update { it.copy(discardTarget = asset) }
    }

    fun cancelDiscard() {
        _state.update { it.copy(discardTarget = null) }
    }

    fun confirmDiscard() {
        val target = _state.value.discardTarget ?: return
        _state.update { it.copy(discardTarget = null) }
        viewModelScope.launch {
            runCatching { repository.discardServerAsset(target.id, local = null) }
                .onSuccess {
                    _state.update { s -> s.copy(items = s.items.filterNot { it.id == target.id }) }
                }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Couldn't discard item") } }
        }
    }
}
