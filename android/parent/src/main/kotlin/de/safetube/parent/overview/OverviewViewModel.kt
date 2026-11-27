package de.safetube.parent.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.Child
import de.safetube.core.api.ParentApi
import de.safetube.core.api.RequestFilter
import de.safetube.core.api.RequestStatus
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChildCard(val child: Child, val todaySeconds: Long, val openRequests: Int)

data class OverviewState(
    val loading: Boolean = true,
    val cards: List<ChildCard> = emptyList(),
    val error: String? = null,
)

class OverviewViewModel(private val api: ParentApi) : ViewModel() {
    private val _state = MutableStateFlow(OverviewState())
    val state: StateFlow<OverviewState> = _state.asStateFlow()

    /** Lädt alles neu; der Bildschirm ruft das bei jedem Erscheinen auf (LifecycleResumeEffect). */
    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            attempt {
                val children = api.children()
                val open = api.requests(RequestFilter(status = RequestStatus.OPEN))
                children.map { child ->
                    ChildCard(
                        child = child,
                        todaySeconds = api.stats(child.id, days = 1).days.lastOrNull()?.seconds?.toLong() ?: 0L,
                        openRequests = open.count { it.childId == child.id },
                    )
                }
            }
                .onSuccess { cards -> _state.value = OverviewState(loading = false, cards = cards) }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    fun createChild(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            attempt { api.createChild(trimmed) }
                .onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(error = e.userMessage()) } }
        }
    }
}
