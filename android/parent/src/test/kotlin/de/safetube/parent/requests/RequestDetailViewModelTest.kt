package de.safetube.parent.requests

import de.safetube.core.api.ApiException
import de.safetube.core.api.DecisionAction
import de.safetube.core.api.Effect
import de.safetube.core.api.RequestStatus
import de.safetube.core.api.Scope
import de.safetube.core.api.TargetType
import de.safetube.core.api.YouTubeException
import de.safetube.core.extractor.PlaybackSource
import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.FakeYouTubeSource
import de.safetube.parent.testing.MainDispatcherRule
import de.safetube.parent.testing.Samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RequestDetailViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val videoRequest = Samples.request(1)
    private val channelRequest = Samples.request(2, type = TargetType.CHANNEL, sourceVideoId = "srcvideo123")
    private val api = FakeParentApi().apply {
        requestsList += videoRequest
        requestsList += channelRequest
    }
    private val youtube = FakeYouTubeSource().apply {
        channels[channelRequest.targetId] = FakeYouTubeSource.channel(channelRequest.targetId)
        streams[videoRequest.targetId] = FakeYouTubeSource.stream(videoRequest.targetId)
    }
    private val decided = mutableListOf<Int>()

    private fun viewModel(requestId: Int) = RequestDetailViewModel(api, youtube, requestId) { decided += it }

    @Test
    fun `Video-Anfrage lädt keine Kanalvideos`() {
        val vm = viewModel(1)

        assertEquals(videoRequest, vm.state.value.request)
        assertEquals(emptyList<String>(), youtube.calls)
    }

    @Test
    fun `Kanal-Anfrage zeigt die letzten 10 Videos`() {
        val vm = viewModel(2)

        assertEquals(10, vm.state.value.channelVideos.size)
        assertEquals(listOf("channel ${channelRequest.targetId} 10"), youtube.calls)
    }

    @Test
    fun `Freigeben für alle Kinder`() {
        val vm = viewModel(1)

        vm.setScope(Scope.ALL)
        vm.approve()

        val (id, decision) = api.lastDecision!!
        assertEquals(1, id)
        assertEquals(DecisionAction.APPROVE, decision.action)
        assertEquals(Scope.ALL, decision.scope)
        assertEquals(RequestStatus.APPROVED, vm.state.value.request!!.status)
        assertEquals("Freigegeben", vm.state.value.message)
        assertEquals(listOf(1), decided)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `nur das Ursprungsvideo einer Kanal-Anfrage freigeben`() {
        val vm = viewModel(2)

        vm.approveVideoOnly()

        assertEquals(DecisionAction.APPROVE_VIDEO_ONLY, api.lastDecision!!.second.action)
    }

    @Test
    fun `Video-Anfrage ablehnen und Kanal sperren`() {
        val vm = viewModel(1)

        vm.rejectAndBlockChannel("  zu gruselig ")

        val decision = api.lastDecision!!.second
        assertEquals(DecisionAction.REJECT, decision.action)
        assertEquals("zu gruselig", decision.reason)
        val rule = api.lastRuleCreate!!
        assertEquals(1, rule.childId)
        assertEquals(TargetType.CHANNEL, rule.targetType)
        assertEquals(videoRequest.channelId, rule.targetId)
        assertEquals(Effect.BLOCK, rule.effect)
        assertEquals("Abgelehnt, Kanal gesperrt", vm.state.value.message)
    }

    @Test
    fun `Kanal sperren für alle Kinder legt eine globale Regel an`() {
        val vm = viewModel(1)

        vm.setScope(Scope.ALL)
        vm.rejectAndBlockChannel("")

        assertNull(api.lastRuleCreate!!.childId)
    }

    @Test
    fun `Kanal-Anfrage ablehnen und sperren macht der Server`() {
        val vm = viewModel(2)

        vm.rejectAndBlockChannel("")

        assertEquals(DecisionAction.REJECT_AND_BLOCK, api.lastDecision!!.second.action)
        assertNull(api.lastRuleCreate)
    }

    @Test
    fun `Video-Anfrage ablehnen und Video sperren`() {
        val vm = viewModel(1)

        vm.rejectAndBlockTarget("")

        assertEquals(DecisionAction.REJECT_AND_BLOCK, api.lastDecision!!.second.action)
        assertEquals("Abgelehnt, Video gesperrt", vm.state.value.message)
    }

    @Test
    fun `Fehler beim Entscheiden wird gemeldet`() {
        val vm = viewModel(1)
        api.failure = ApiException(500, "kaputt")

        vm.approve()

        assertEquals("kaputt", vm.state.value.message)
        assertFalse(vm.state.value.busy)
        assertTrue(decided.isEmpty())
    }

    @Test
    fun `Vorschau wählt einen Stream, Fehler werden angezeigt`() {
        val vm = viewModel(1)

        vm.preview(videoRequest.targetId, "Titel")
        assertEquals(PlaybackSource.Progressive("https://v/${videoRequest.targetId}"), vm.state.value.preview!!.source)

        youtube.failure = YouTubeException("YouTube ist gerade nicht erreichbar.")
        vm.preview(videoRequest.targetId, "Titel")
        assertEquals("YouTube ist gerade nicht erreichbar.", vm.state.value.preview!!.error)

        vm.closePreview()
        assertNull(vm.state.value.preview)
    }
}
