package de.safetube.parent.history

import de.safetube.core.api.Effect
import de.safetube.core.api.TargetType
import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.MainDispatcherRule
import de.safetube.parent.testing.Samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HistoryViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val api = FakeParentApi().apply {
        childrenList += Samples.child(1, "Mia")
        historyByChild[1] = listOf(Samples.history(1), Samples.history(2), Samples.history(3))
    }

    private fun viewModel() = HistoryViewModel(api, childId = 1, pageSize = 2).apply { load() }

    @Test
    fun `erste Seite und weiterblättern`() {
        val vm = viewModel()
        assertEquals("Mia", vm.state.value.childName)
        assertEquals(listOf(1, 2), vm.state.value.entries.map { it.id })
        assertTrue(vm.state.value.canLoadMore)

        vm.loadMore()

        assertEquals(listOf(1, 2, 3), vm.state.value.entries.map { it.id })
        assertFalse(vm.state.value.canLoadMore)
        assertTrue("history 1 ${Samples.history(2).startedAt}" in api.calls)
    }

    @Test
    fun `Video aus dem Verlauf sperren`() {
        val vm = viewModel()
        val entry = vm.state.value.entries.first()

        vm.setRule(entry, TargetType.VIDEO, Effect.BLOCK)

        val rule = api.lastRuleCreate!!
        assertEquals(1, rule.childId)
        assertEquals(TargetType.VIDEO, rule.targetType)
        assertEquals(entry.videoId, rule.targetId)
        assertEquals(entry.title, rule.title)
        assertEquals(Effect.BLOCK, rule.effect)
        assertEquals("Video gesperrt", vm.state.value.message)
    }

    @Test
    fun `Kanal aus dem Verlauf freigeben`() {
        val vm = viewModel()
        val entry = vm.state.value.entries.first()

        vm.setRule(entry, TargetType.CHANNEL, Effect.ALLOW)

        val rule = api.lastRuleCreate!!
        assertEquals(entry.channelId, rule.targetId)
        assertEquals(entry.channelName, rule.title)
        assertEquals("Kanal freigegeben", vm.state.value.message)
    }
}
