package de.safetube.parent.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.safetube.core.api.ContentRequest
import de.safetube.core.api.Decision
import de.safetube.core.api.DecisionAction
import de.safetube.core.api.Effect
import de.safetube.core.api.ParentApi
import de.safetube.core.api.RuleCreate
import de.safetube.core.api.Scope
import de.safetube.core.api.TargetType
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import de.safetube.core.extractor.PlaybackSource
import de.safetube.core.extractor.StreamChoice
import de.safetube.core.extractor.VideoItem
import de.safetube.core.extractor.YouTubeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Preview(
    val videoId: String,
    val title: String,
    val source: PlaybackSource? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

data class RequestDetailState(
    val loading: Boolean = true,
    val request: ContentRequest? = null,
    val error: String? = null,
    val scope: Scope = Scope.CHILD,
    val busy: Boolean = false,
    /** Kurze Rückmeldung (Snackbar), danach messageShown() aufrufen. */
    val message: String? = null,
    val channelVideos: List<VideoItem> = emptyList(),
    val channelVideosError: String? = null,
    val preview: Preview? = null,
)

class RequestDetailViewModel(
    private val api: ParentApi,
    private val youtube: YouTubeSource,
    private val requestId: Int,
    private val onDecided: (Int) -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow(RequestDetailState())
    val state: StateFlow<RequestDetailState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            attempt { api.request(requestId) }
                .onSuccess { request ->
                    _state.update { it.copy(loading = false, request = request) }
                    if (request.targetType == TargetType.CHANNEL) loadChannelVideos(request.targetId)
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    private fun loadChannelVideos(channelId: String) {
        viewModelScope.launch {
            attempt { youtube.channel(channelId, maxVideos = 10).videos }
                .onSuccess { videos -> _state.update { it.copy(channelVideos = videos, channelVideosError = null) } }
                .onFailure { e -> _state.update { it.copy(channelVideosError = e.userMessage()) } }
        }
    }

    fun setScope(scope: Scope) = _state.update { it.copy(scope = scope) }

    fun approve() = decide(DecisionAction.APPROVE, "", "Freigegeben")

    fun approveVideoOnly() = decide(DecisionAction.APPROVE_VIDEO_ONLY, "", "Nur das Video freigegeben")

    fun reject(reason: String) = decide(DecisionAction.REJECT, reason, "Abgelehnt")

    /** Lehnt ab und sperrt genau das angefragte Ziel (Video bzw. Kanal). */
    fun rejectAndBlockTarget(reason: String) {
        val request = _state.value.request ?: return
        val what = if (request.targetType == TargetType.CHANNEL) "Kanal" else "Video"
        decide(DecisionAction.REJECT_AND_BLOCK, reason, "Abgelehnt, $what gesperrt")
    }

    /** Lehnt ab und sperrt den Kanal — bei Video-Anfragen über eine zusätzliche Regel (Festlegung 10). */
    fun rejectAndBlockChannel(reason: String) {
        val request = _state.value.request ?: return
        if (request.targetType == TargetType.CHANNEL) {
            rejectAndBlockTarget(reason)
            return
        }
        val scope = _state.value.scope
        perform("Abgelehnt, Kanal gesperrt") {
            val result = api.decide(request.id, Decision(DecisionAction.REJECT, scope, reason.trim()))
            api.createRule(
                RuleCreate(
                    childId = if (scope == Scope.ALL) null else request.childId,
                    targetType = TargetType.CHANNEL,
                    targetId = request.channelId,
                    effect = Effect.BLOCK,
                    title = request.channelName,
                    channelName = request.channelName,
                    note = "Gesperrt bei Anfrage #${request.id}",
                ),
            )
            result
        }
    }

    fun preview(videoId: String, title: String) {
        _state.update { it.copy(preview = Preview(videoId, title)) }
        viewModelScope.launch {
            attempt { StreamChoice.pick(youtube.stream(videoId).candidates) }
                .onSuccess { source ->
                    val error = if (source == null) "Für dieses Video wurde kein abspielbarer Stream gefunden." else null
                    _state.update { it.copy(preview = Preview(videoId, title, source, loading = false, error = error)) }
                }
                .onFailure { e ->
                    _state.update { it.copy(preview = Preview(videoId, title, loading = false, error = e.userMessage())) }
                }
        }
    }

    fun closePreview() = _state.update { it.copy(preview = null) }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun decide(action: DecisionAction, reason: String, message: String) {
        val scope = _state.value.scope
        perform(message) { api.decide(requestId, Decision(action, scope, reason.trim())) }
    }

    private fun perform(message: String, block: suspend () -> ContentRequest) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            attempt { block() }
                .onSuccess { request ->
                    _state.update { it.copy(busy = false, request = request, message = message) }
                    onDecided(request.id)
                }
                .onFailure { e -> _state.update { it.copy(busy = false, message = e.userMessage()) } }
        }
    }
}
