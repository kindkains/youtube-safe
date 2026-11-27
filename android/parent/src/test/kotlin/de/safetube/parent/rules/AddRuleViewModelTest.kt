package de.safetube.parent.rules

import de.safetube.core.api.Effect
import de.safetube.core.api.TargetType
import de.safetube.core.api.YouTubeException
import de.safetube.core.extractor.ChannelItem
import de.safetube.core.extractor.SearchFilter
import de.safetube.core.extractor.SearchHit
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

class AddRuleViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val channelId = "UCuAXFkgsw1L7xaCfnd5JJOw"
    private val api = FakeParentApi().apply { childrenList += Samples.child(1, "Mia") }
    private val youtube = FakeYouTubeSource().apply {
        streams["dQw4w9WgXcQ"] = FakeYouTubeSource.stream("dQw4w9WgXcQ", channelId, title = "Never Gonna")
        channels[channelId] = FakeYouTubeSource.channel(channelId, name = "Rick Astley")
        channels["https://www.youtube.com/@RickAstleyYT"] = FakeYouTubeSource.channel(channelId, name = "Rick Astley")
    }

    private fun viewModel() = AddRuleViewModel(api, youtube).apply { load() }

    @Test
    fun `Video-Link wird mit Titel zum Entwurf`() {
        val vm = viewModel()

        vm.onLink("https://youtu.be/dQw4w9WgXcQ")
        vm.inspectLink()

        assertEquals(RuleDraft(TargetType.VIDEO, "dQw4w9WgXcQ", "Never Gonna", "Kanal"), vm.state.value.draft)
    }

    @Test
    fun `Kanal-Alias wird zur Kanal-ID aufgelöst`() {
        val vm = viewModel()

        vm.onLink("https://www.youtube.com/@RickAstleyYT")
        vm.inspectLink()

        assertEquals(RuleDraft(TargetType.CHANNEL, channelId, "Rick Astley", "Rick Astley"), vm.state.value.draft)
    }

    @Test
    fun `unbekannter Link`() {
        val vm = viewModel()

        vm.onLink("https://example.com")
        vm.inspectLink()

        assertNull(vm.state.value.draft)
        assertEquals("Das ist kein YouTube-Link, den ich verstehe.", vm.state.value.error)
    }

    @Test
    fun `ohne Titel weiter, wenn YouTube gerade nicht antwortet`() {
        val vm = viewModel()
        youtube.failure = YouTubeException("YouTube ist gerade nicht erreichbar.")

        vm.onLink("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        vm.inspectLink()

        assertEquals(RuleDraft(TargetType.VIDEO, "dQw4w9WgXcQ", "", ""), vm.state.value.draft)
        assertEquals("Titel konnte nicht geladen werden: YouTube ist gerade nicht erreichbar.", vm.state.value.error)
    }

    @Test
    fun `Suche und Kanal ohne ID aus dem Treffer auflösen`() {
        val hit = SearchHit.Channel(ChannelItem(null, "Rick Astley", "https://www.youtube.com/@RickAstleyYT", null, 0))
        youtube.searchResults["rick"] = listOf(hit)
        val vm = viewModel()

        vm.onSearchQuery("rick")
        vm.setSearchFilter(SearchFilter.CHANNELS)
        vm.search()
        assertEquals(listOf(hit), vm.state.value.results)
        assertTrue("search rick CHANNELS" in youtube.calls)

        vm.pick(hit)
        assertEquals(channelId, vm.state.value.draft!!.targetId)
    }

    @Test
    fun `speichern mit Bereich, Wirkung und Notiz`() {
        val vm = viewModel()
        vm.onLink("https://youtu.be/dQw4w9WgXcQ")
        vm.inspectLink()

        vm.setEffect(Effect.BLOCK)
        vm.setScopeChild(1)
        vm.onNote(" nervt ")
        vm.save()

        val rule = api.lastRuleCreate!!
        assertEquals(1, rule.childId)
        assertEquals(TargetType.VIDEO, rule.targetType)
        assertEquals("dQw4w9WgXcQ", rule.targetId)
        assertEquals(Effect.BLOCK, rule.effect)
        assertEquals("Never Gonna", rule.title)
        assertEquals("nervt", rule.note)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `ohne Entwurf wird nichts gespeichert`() {
        val vm = viewModel()

        vm.save()

        assertNull(api.lastRuleCreate)
        assertFalse(vm.state.value.saved)
    }
}
