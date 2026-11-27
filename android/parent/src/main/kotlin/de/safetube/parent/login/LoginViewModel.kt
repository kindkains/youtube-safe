package de.safetube.parent.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.ApiException
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import de.safetube.parent.session.Auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginState(
    val server: String = "",
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
)

class LoginViewModel(private val auth: Auth, defaultServer: String) : ViewModel() {
    private val _state = MutableStateFlow(LoginState(server = defaultServer))
    val state: StateFlow<LoginState> = _state.asStateFlow()

    /** Fertig, sobald die zuletzt benutzte Server-Adresse geladen ist (für Tests). */
    val ready: Job = viewModelScope.launch {
        auth.lastServer()?.let { last -> _state.update { it.copy(server = last) } }
    }

    /** Laufender Anmeldeversuch (für Tests). */
    var busyJob: Job? = null
        private set

    fun onServer(value: String) = _state.update { it.copy(server = value, error = null) }
    fun onEmail(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val current = _state.value
        if (current.busy) return
        if (current.email.isBlank() || current.password.isEmpty()) {
            _state.update { it.copy(error = "Bitte E-Mail und Passwort eingeben.") }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        busyJob = viewModelScope.launch {
            attempt { auth.login(current.server, current.email, current.password) }
                .onSuccess { _state.update { it.copy(busy = false, loggedIn = true, password = "") } }
                .onFailure { e ->
                    val message = if (e is ApiException && e.status == 401) "E-Mail oder Passwort falsch." else e.userMessage()
                    _state.update { it.copy(busy = false, error = message) }
                }
        }
    }
}
