package de.safetube.parent.parents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.MeUpdate
import de.safetube.core.api.Parent
import de.safetube.core.api.ParentApi
import de.safetube.core.api.ParentCreate
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ParentsState(
    val loading: Boolean = true,
    val me: Parent? = null,
    val parents: List<Parent> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val loggedOut: Boolean = false,
)

/** [logout] meldet ab und löscht die Sitzung (Auth.logout), stoppt ab Task 22 auch den Push-Dienst. */
class ParentsViewModel(private val api: ParentApi, private val logout: suspend () -> Unit) : ViewModel() {
    private val _state = MutableStateFlow(ParentsState())
    val state: StateFlow<ParentsState> = _state.asStateFlow()

    var logoutJob: Job? = null
        private set

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            attempt { api.me() to api.parents() }
                .onSuccess { (me, parents) -> _state.update { it.copy(loading = false, me = me, parents = parents) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    fun addParent(name: String, email: String, password: String) {
        val cleanName = name.trim()
        val cleanEmail = email.trim()
        val problem = when {
            cleanName.isEmpty() -> "Bitte einen Namen angeben."
            !cleanEmail.contains('@') || cleanEmail.length < 3 -> "Bitte eine gültige E-Mail-Adresse angeben."
            password.length < 8 -> "Das Passwort braucht mindestens 8 Zeichen."
            else -> null
        }
        if (problem != null) {
            _state.update { it.copy(error = problem) }
            return
        }
        viewModelScope.launch {
            attempt { api.createParent(ParentCreate(cleanName, cleanEmail, password)) }
                .onSuccess { parent ->
                    _state.update { it.copy(error = null, parents = it.parents + parent, message = "${parent.name} angelegt") }
                }
                .onFailure { e -> _state.update { it.copy(error = e.userMessage()) } }
        }
    }

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        updateMe(MeUpdate(name = trimmed), "Name gespeichert")
    }

    fun setMailNewRequest(enabled: Boolean) = updateMe(MeUpdate(mailNewRequest = enabled), "Gespeichert")

    fun setMailAlerts(enabled: Boolean) = updateMe(MeUpdate(mailAlerts = enabled), "Gespeichert")

    fun changePassword(password: String, repeat: String) {
        val problem = when {
            password.length < 8 -> "Das Passwort braucht mindestens 8 Zeichen."
            password != repeat -> "Die beiden Passwörter sind verschieden."
            else -> null
        }
        if (problem != null) {
            _state.update { it.copy(error = problem) }
            return
        }
        updateMe(MeUpdate(password = password), "Passwort geändert")
    }

    fun logout() {
        logoutJob = viewModelScope.launch {
            logout.invoke()
            _state.update { it.copy(loggedOut = true) }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun updateMe(update: MeUpdate, message: String) {
        viewModelScope.launch {
            attempt { api.updateMe(update) }
                .onSuccess { me ->
                    _state.update { s ->
                        s.copy(
                            me = me,
                            parents = s.parents.map { if (it.id == me.id) me else it },
                            error = null,
                            message = message,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.userMessage()) } }
        }
    }
}
