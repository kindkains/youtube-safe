package de.safetube.parent.reward

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.Child
import de.safetube.core.api.ParentApi
import de.safetube.core.api.RewardStart
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import de.safetube.core.format.Formats
import de.safetube.core.format.RewardDuration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RewardMode { DURATION, UNTIL }

data class RewardState(
    val loading: Boolean = true,
    val child: Child? = null,
    val mode: RewardMode = RewardMode.DURATION,
    val days: String = "0",
    val hours: String = "1",
    val minutes: String = "0",
    val date: LocalDate = LocalDate.now(Formats.ZONE),
    val time: LocalTime = LocalTime.NOON,
    val includeBlocked: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class RewardViewModel(
    private val api: ParentApi,
    private val childId: Int,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {
    private val _state = MutableStateFlow(defaultState())
    val state: StateFlow<RewardState> = _state.asStateFlow()

    /** Endzeit-Vorschlag: in einer Stunde, auf die nächste Viertelstunde aufgerundet (Berliner Zeit). */
    private fun defaultState(): RewardState {
        val inOneHour = clock().plus(1, ChronoUnit.HOURS).atZone(Formats.ZONE)
        val rounded = inOneHour.truncatedTo(ChronoUnit.HOURS)
            .plusMinutes(((inOneHour.minute + 14) / 15) * 15L)
        return RewardState(date = rounded.toLocalDate(), time = rounded.toLocalTime())
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            attempt { api.children().first { it.id == childId } }
                .onSuccess { child -> _state.update { it.copy(loading = false, child = child) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    fun setMode(mode: RewardMode) = _state.update { it.copy(mode = mode, error = null) }
    fun setDays(value: String) = _state.update { it.copy(days = digits(value), error = null) }
    fun setHours(value: String) = _state.update { it.copy(hours = digits(value), error = null) }
    fun setMinutes(value: String) = _state.update { it.copy(minutes = digits(value), error = null) }
    fun setDate(date: LocalDate) = _state.update { it.copy(date = date, error = null) }
    fun setTime(time: LocalTime) = _state.update { it.copy(time = time, error = null) }
    fun setIncludeBlocked(value: Boolean) = _state.update { it.copy(includeBlocked = value) }
    fun messageShown() = _state.update { it.copy(message = null) }

    fun start() {
        val s = _state.value
        val reward = when (s.mode) {
            RewardMode.DURATION -> RewardDuration.minutes(s.days.toIntOrZero(), s.hours.toIntOrZero(), s.minutes.toIntOrZero())
                ?.let { RewardStart(minutes = it, includeBlocked = s.includeBlocked) }
                ?: return fail("Bitte eine Dauer zwischen 1 Minute und 30 Tagen angeben.")
            RewardMode.UNTIL -> RewardDuration.until(s.date, s.time, clock())
                ?.let { RewardStart(until = it, includeBlocked = s.includeBlocked) }
                ?: return fail("Die Endzeit muss in der Zukunft liegen (höchstens 30 Tage).")
        }
        perform { api.startReward(childId, reward) }
    }

    fun stop() = perform { api.stopReward(childId) }

    private fun perform(block: suspend () -> Child) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            attempt { block() }
                .onSuccess { child ->
                    val until = child.rewardUntil
                    val message = if (child.rewardActive && until != null) {
                        "Belohnungsmodus läuft bis ${Formats.dateTime(until)}"
                    } else {
                        "Belohnungsmodus beendet"
                    }
                    _state.update { it.copy(busy = false, child = child, message = message) }
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.userMessage()) } }
        }
    }

    private fun fail(message: String) = _state.update { it.copy(error = message) }

    private fun digits(value: String) = value.filter(Char::isDigit).take(4)

    private fun String.toIntOrZero() = toIntOrNull() ?: 0
}
