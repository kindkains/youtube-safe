package de.safetube.parent.child

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.Child
import de.safetube.core.api.ChildUpdate
import de.safetube.core.api.Device
import de.safetube.core.api.DisplayMode
import de.safetube.core.api.PairingCode
import de.safetube.core.api.ParentApi
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChildSettingsState(
    val loading: Boolean = true,
    val child: Child? = null,
    val devices: List<Device> = emptyList(),
    val pairing: PairingCode? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    /** Kind ist gelöscht, der Bildschirm schließt sich. */
    val deleted: Boolean = false,
)

class ChildSettingsViewModel(private val api: ParentApi, private val childId: Int) : ViewModel() {
    private val _state = MutableStateFlow(ChildSettingsState())
    val state: StateFlow<ChildSettingsState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            attempt { api.children().first { it.id == childId } to api.devices(childId) }
                .onSuccess { (child, devices) -> _state.update { it.copy(loading = false, child = child, devices = devices) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        change(ChildUpdate(name = trimmed), "Name gespeichert")
    }

    fun setDisplayMode(mode: DisplayMode) = change(ChildUpdate(displayMode = mode), "Anzeige geändert")

    fun setShorts(enabled: Boolean) = change(ChildUpdate(shortsEnabled = enabled), if (enabled) "Shorts an" else "Shorts aus")

    fun createPairingCode() {
        viewModelScope.launch {
            attempt { api.pairingCode(childId) }
                .onSuccess { code -> _state.update { it.copy(pairing = code) } }
                .onFailure { e -> _state.update { it.copy(message = e.userMessage()) } }
        }
    }

    fun closePairing() = _state.update { it.copy(pairing = null) }

    fun unpair(device: Device) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            attempt {
                api.unpairDevice(device.id)
                api.devices(childId)
            }
                .onSuccess { devices -> _state.update { it.copy(busy = false, devices = devices, message = "Gerät entkoppelt") } }
                .onFailure { e -> _state.update { it.copy(busy = false, message = e.userMessage()) } }
        }
    }

    fun deleteChild() {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            attempt { api.deleteChild(childId) }
                .onSuccess { _state.update { it.copy(busy = false, deleted = true) } }
                .onFailure { e -> _state.update { it.copy(busy = false, message = e.userMessage()) } }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun change(update: ChildUpdate, message: String) {
        viewModelScope.launch {
            attempt { api.updateChild(childId, update) }
                .onSuccess { child -> _state.update { it.copy(child = child, message = message) } }
                .onFailure { e -> _state.update { it.copy(message = e.userMessage()) } }
        }
    }
}
