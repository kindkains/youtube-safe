package de.safetube.parent.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.Effect
import de.safetube.core.api.HistoryEntry
import de.safetube.core.api.ParentApi
import de.safetube.core.api.RuleCreate
import de.safetube.core.api.TargetType
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryState(
    val loading: Boolean = true,
    val childName: String = "",
    val entries: List<HistoryEntry> = emptyList(),
    val canLoadMore: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class HistoryViewModel(
    private val api: ParentApi,
    private val childId: Int,
    private val pageSize: Int = 50,
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            attempt {
                val name = api.children().firstOrNull { it.id == childId }?.name.orEmpty()
                name to api.history(childId, limit = pageSize)
            }
                .onSuccess { (name, entries) ->
                    _state.update {
                        it.copy(loading = false, childName = name, entries = entries, canLoadMore = entries.size == pageSize)
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    fun loadMore() {
        val current = _state.value
        val last = current.entries.lastOrNull() ?: return
        if (current.loadingMore || !current.canLoadMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            attempt { api.history(childId, limit = pageSize, before = last.startedAt) }
                .onSuccess { more ->
                    _state.update {
                        it.copy(loadingMore = false, entries = it.entries + more, canLoadMore = more.size == pageSize)
                    }
                }
                .onFailure { e -> _state.update { it.copy(loadingMore = false, message = e.userMessage()) } }
        }
    }

    fun setRule(entry: HistoryEntry, type: TargetType, effect: Effect) {
        val video = type == TargetType.VIDEO
        val targetId = if (video) entry.videoId else entry.channelId
        if (targetId.isBlank()) return
        viewModelScope.launch {
            attempt {
                api.createRule(
                    RuleCreate(
                        childId = childId,
                        targetType = type,
                        targetId = targetId,
                        effect = effect,
                        title = if (video) entry.title else entry.channelName,
                        channelName = entry.channelName,
                    ),
                )
            }
                .onSuccess {
                    val what = if (video) "Video" else "Kanal"
                    val verb = if (effect == Effect.ALLOW) "freigegeben" else "gesperrt"
                    _state.update { it.copy(message = "$what $verb") }
                    load()
                }
                .onFailure { e -> _state.update { it.copy(message = e.userMessage()) } }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
