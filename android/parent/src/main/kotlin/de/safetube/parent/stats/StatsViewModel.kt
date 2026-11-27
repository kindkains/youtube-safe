package de.safetube.parent.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.ParentApi
import de.safetube.core.api.Stats
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatsState(
    val loading: Boolean = true,
    val childName: String = "",
    val days: Int = 7,
    val stats: Stats? = null,
    val error: String? = null,
) {
    val maxDaySeconds: Int get() = stats?.days?.maxOfOrNull { it.seconds } ?: 0

    val rewardPercent: Int
        get() {
            val total = stats?.totalSeconds ?: 0
            return if (total > 0) (stats!!.rewardSeconds * 100) / total else 0
        }
}

class StatsViewModel(private val api: ParentApi, private val childId: Int) : ViewModel() {
    private val _state = MutableStateFlow(StatsState())
    val state: StateFlow<StatsState> = _state.asStateFlow()

    fun setDays(days: Int) {
        _state.update { it.copy(days = days) }
        load()
    }

    fun load() {
        val days = _state.value.days
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            attempt {
                val name = api.children().firstOrNull { it.id == childId }?.name.orEmpty()
                name to api.stats(childId, days)
            }
                .onSuccess { (name, stats) -> _state.update { it.copy(loading = false, childName = name, stats = stats) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }
}
