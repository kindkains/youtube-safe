package de.safetube.parent.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.Child
import de.safetube.core.api.ContentRequest
import de.safetube.core.api.ParentApi
import de.safetube.core.api.RequestFilter
import de.safetube.core.api.RequestStatus
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Period(val label: String, val days: Long?) {
    WEEK("7 Tage", 7),
    MONTH("30 Tage", 30),
    ALL("Alle", null),
}

enum class StatusFilter(val label: String, val status: RequestStatus?) {
    ALL("Alle", null),
    OPEN("Offen", RequestStatus.OPEN),
    APPROVED("Freigegeben", RequestStatus.APPROVED),
    REJECTED("Abgelehnt", RequestStatus.REJECTED),
}

data class RequestsState(
    val loading: Boolean = true,
    val children: List<Child> = emptyList(),
    val childId: Int? = null,
    val status: StatusFilter = StatusFilter.ALL,
    val period: Period = Period.MONTH,
    val open: List<ContentRequest> = emptyList(),
    val history: List<ContentRequest> = emptyList(),
    val error: String? = null,
)

class RequestsViewModel(
    private val api: ParentApi,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {
    private val _state = MutableStateFlow(RequestsState())
    val state: StateFlow<RequestsState> = _state.asStateFlow()

    fun setChild(childId: Int?) {
        _state.update { it.copy(childId = childId) }
        load()
    }

    fun setStatus(status: StatusFilter) {
        _state.update { it.copy(status = status) }
        load()
    }

    fun setPeriod(period: Period) {
        _state.update { it.copy(period = period) }
        load()
    }

    fun load() {
        val current = _state.value
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            attempt {
                val children = current.children.ifEmpty { api.children() }
                val since = current.period.days?.let { clock().minus(Duration.ofDays(it)) }
                val showOpen = current.status == StatusFilter.ALL || current.status == StatusFilter.OPEN
                val open = if (showOpen) {
                    api.requests(RequestFilter(childId = current.childId, status = RequestStatus.OPEN))
                } else {
                    emptyList()
                }
                val history = if (current.status == StatusFilter.OPEN) {
                    emptyList()
                } else {
                    api.requests(RequestFilter(childId = current.childId, status = current.status.status, since = since))
                        .filter { it.status != RequestStatus.OPEN }
                }
                Triple(children, open, history)
            }
                .onSuccess { (children, open, history) ->
                    _state.update { it.copy(loading = false, children = children, open = open, history = history) }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }
}
