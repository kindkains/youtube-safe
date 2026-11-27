package de.safetube.parent.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.Child
import de.safetube.core.api.Effect
import de.safetube.core.api.ParentApi
import de.safetube.core.api.RuleCreate
import de.safetube.core.api.TargetType
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import de.safetube.core.extractor.SearchFilter
import de.safetube.core.extractor.SearchHit
import de.safetube.core.extractor.YouTubeSource
import de.safetube.core.youtube.YouTubeIds
import de.safetube.core.youtube.YouTubeLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RuleDraft(val targetType: TargetType, val targetId: String, val title: String, val channelName: String)

enum class AddMode { LINK, SEARCH }

data class AddRuleState(
    val children: List<Child> = emptyList(),
    val mode: AddMode = AddMode.LINK,
    val link: String = "",
    val searchQuery: String = "",
    val searchFilter: SearchFilter = SearchFilter.ALL,
    val results: List<SearchHit> = emptyList(),
    val working: Boolean = false,
    val draft: RuleDraft? = null,
    val effect: Effect = Effect.ALLOW,
    /** null = für alle Kinder. */
    val scopeChildId: Int? = null,
    val note: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

class AddRuleViewModel(private val api: ParentApi, private val youtube: YouTubeSource) : ViewModel() {
    private val _state = MutableStateFlow(AddRuleState())
    val state: StateFlow<AddRuleState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            attempt { api.children() }
                .onSuccess { children -> _state.update { it.copy(children = children) } }
                .onFailure { e -> _state.update { it.copy(error = e.userMessage()) } }
        }
    }

    fun setMode(mode: AddMode) = _state.update { it.copy(mode = mode, error = null) }
    fun onLink(value: String) = _state.update { it.copy(link = value, error = null) }
    fun onSearchQuery(value: String) = _state.update { it.copy(searchQuery = value) }
    fun setSearchFilter(filter: SearchFilter) = _state.update { it.copy(searchFilter = filter) }
    fun setEffect(effect: Effect) = _state.update { it.copy(effect = effect) }
    fun setScopeChild(childId: Int?) = _state.update { it.copy(scopeChildId = childId) }
    fun onNote(value: String) = _state.update { it.copy(note = value) }
    fun clearDraft() = _state.update { it.copy(draft = null) }

    fun inspectLink() {
        when (val link = YouTubeIds.parse(_state.value.link)) {
            null -> _state.update { it.copy(error = "Das ist kein YouTube-Link, den ich verstehe.", draft = null) }
            is YouTubeLink.Video -> resolve(fallback = RuleDraft(TargetType.VIDEO, link.videoId, "", "")) {
                val details = youtube.stream(link.videoId)
                RuleDraft(TargetType.VIDEO, link.videoId, details.title, details.channelName)
            }
            is YouTubeLink.Channel -> resolve(fallback = RuleDraft(TargetType.CHANNEL, link.channelId, "", "")) {
                val page = youtube.channel(link.channelId, maxVideos = 1)
                RuleDraft(TargetType.CHANNEL, page.channelId, page.name, page.name)
            }
            is YouTubeLink.ChannelAlias -> resolve(fallback = null) {
                val page = youtube.channel(link.url, maxVideos = 1)
                RuleDraft(TargetType.CHANNEL, page.channelId, page.name, page.name)
            }
        }
    }

    fun search() {
        val query = _state.value.searchQuery.trim()
        if (query.isEmpty()) return
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            attempt { youtube.search(query, _state.value.searchFilter) }
                .onSuccess { hits -> _state.update { it.copy(working = false, results = hits) } }
                .onFailure { e -> _state.update { it.copy(working = false, error = e.userMessage()) } }
        }
    }

    fun pick(hit: SearchHit) {
        when (hit) {
            is SearchHit.Video -> _state.update {
                it.copy(draft = RuleDraft(TargetType.VIDEO, hit.item.videoId, hit.item.title, hit.item.channelName), error = null)
            }
            is SearchHit.Channel -> {
                val id = hit.item.channelId
                if (id != null) {
                    _state.update { it.copy(draft = RuleDraft(TargetType.CHANNEL, id, hit.item.name, hit.item.name), error = null) }
                } else {
                    resolve(fallback = null) {
                        val page = youtube.channel(hit.item.url, maxVideos = 1)
                        RuleDraft(TargetType.CHANNEL, page.channelId, page.name, page.name)
                    }
                }
            }
        }
    }

    fun save() {
        val current = _state.value
        val draft = current.draft ?: return
        if (current.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            attempt {
                api.createRule(
                    RuleCreate(
                        childId = current.scopeChildId,
                        targetType = draft.targetType,
                        targetId = draft.targetId,
                        effect = current.effect,
                        title = draft.title,
                        channelName = draft.channelName,
                        note = current.note.trim(),
                    ),
                )
            }
                .onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.userMessage()) } }
        }
    }

    /** Lädt Details zum Entwurf; mit [fallback] entsteht bei Fehlern trotzdem ein Entwurf. */
    private fun resolve(fallback: RuleDraft?, block: suspend () -> RuleDraft) {
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            attempt { block() }
                .onSuccess { draft -> _state.update { it.copy(working = false, draft = draft) } }
                .onFailure { e ->
                    if (fallback != null) {
                        _state.update {
                            it.copy(working = false, draft = fallback, error = "Titel konnte nicht geladen werden: ${e.userMessage()}")
                        }
                    } else {
                        _state.update { it.copy(working = false, draft = null, error = e.userMessage()) }
                    }
                }
        }
    }
}
