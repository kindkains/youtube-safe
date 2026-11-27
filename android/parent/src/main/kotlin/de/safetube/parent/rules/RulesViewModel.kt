package de.safetube.parent.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.Child
import de.safetube.core.api.Effect
import de.safetube.core.api.ParentApi
import de.safetube.core.api.Rule
import de.safetube.core.api.RuleUpdate
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RuleScope {
    data object All : RuleScope
    data object Global : RuleScope
    data class ForChild(val childId: Int) : RuleScope
}

data class RulesState(
    val loading: Boolean = true,
    val children: List<Child> = emptyList(),
    val scope: RuleScope = RuleScope.All,
    val query: String = "",
    val rules: List<Rule> = emptyList(),
    val error: String? = null,
    val message: String? = null,
) {
    val visible: List<Rule>
        get() {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return rules
            return rules.filter { rule ->
                listOf(rule.title, rule.channelName, rule.targetId).any { it.lowercase().contains(q) }
            }
        }

    fun scopeLabel(childId: Int?): String =
        if (childId == null) "alle Kinder" else children.firstOrNull { it.id == childId }?.name ?: "Kind $childId"
}

class RulesViewModel(private val api: ParentApi) : ViewModel() {
    private val _state = MutableStateFlow(RulesState())
    val state: StateFlow<RulesState> = _state.asStateFlow()

    fun setScope(scope: RuleScope) {
        _state.update { it.copy(scope = scope) }
        load()
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun load() {
        val current = _state.value
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            attempt {
                val children = current.children.ifEmpty { api.children() }
                val rules = when (val scope = current.scope) {
                    RuleScope.All -> api.rules()
                    RuleScope.Global -> api.rules(globalOnly = true)
                    is RuleScope.ForChild -> api.rules(childId = scope.childId)
                }
                children to rules
            }
                .onSuccess { (children, rules) -> _state.update { it.copy(loading = false, children = children, rules = rules) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    fun update(rule: Rule, effect: Effect, note: String) {
        viewModelScope.launch {
            attempt { api.updateRule(rule.id, RuleUpdate(effect = effect, note = note.trim())) }
                .onSuccess { changed ->
                    _state.update { s -> s.copy(rules = s.rules.map { if (it.id == changed.id) changed else it }, message = "Gespeichert") }
                }
                .onFailure { e -> _state.update { it.copy(message = e.userMessage()) } }
        }
    }

    fun delete(rule: Rule) {
        viewModelScope.launch {
            attempt { api.deleteRule(rule.id) }
                .onSuccess { _state.update { s -> s.copy(rules = s.rules.filterNot { it.id == rule.id }, message = "Regel gelöscht") } }
                .onFailure { e -> _state.update { it.copy(message = e.userMessage()) } }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
