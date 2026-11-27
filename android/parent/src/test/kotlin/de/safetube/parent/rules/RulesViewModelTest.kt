package de.safetube.parent.rules

import de.safetube.core.api.Effect
import de.safetube.core.api.TargetType
import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.MainDispatcherRule
import de.safetube.parent.testing.Samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RulesViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val api = FakeParentApi().apply {
        childrenList += Samples.child(1, "Mia")
        childrenList += Samples.child(2, "Antonio")
        rulesList += Samples.rule(1, null, TargetType.CHANNEL, "UCmaus", Effect.ALLOW, title = "Die Maus", channelName = "Die Maus")
        rulesList += Samples.rule(2, 1, TargetType.VIDEO, "vid1", Effect.BLOCK, title = "Gruselvideo", channelName = "Horror TV")
        rulesList += Samples.rule(3, 2, TargetType.CHANNEL, "UClego", Effect.ALLOW, title = "LEGO", channelName = "LEGO")
    }

    private fun viewModel() = RulesViewModel(api).apply { load() }

    @Test
    fun `alle Regeln mit Bereich`() {
        val vm = viewModel()

        assertEquals(listOf(1, 2, 3), vm.state.value.visible.map { it.id })
        assertEquals("alle Kinder", vm.state.value.scopeLabel(null))
        assertEquals("Mia", vm.state.value.scopeLabel(1))
    }

    @Test
    fun `Regeln eines Kindes enthalten die für alle`() {
        val vm = viewModel()

        vm.setScope(RuleScope.ForChild(1))

        assertEquals(listOf(1, 2), vm.state.value.visible.map { it.id })
        assertTrue("rules 1 false" in api.calls)
    }

    @Test
    fun `nur Regeln für alle Kinder`() {
        val vm = viewModel()

        vm.setScope(RuleScope.Global)

        assertEquals(listOf(1), vm.state.value.visible.map { it.id })
        assertTrue("rules null true" in api.calls)
    }

    @Test
    fun `Suche über Titel, Kanal und ID ohne Groß-Klein`() {
        val vm = viewModel()

        vm.setQuery("horror")
        assertEquals(listOf(2), vm.state.value.visible.map { it.id })
        vm.setQuery("uclego")
        assertEquals(listOf(3), vm.state.value.visible.map { it.id })
        vm.setQuery("")
        assertEquals(3, vm.state.value.visible.size)
    }

    @Test
    fun `bearbeiten und löschen`() {
        val vm = viewModel()

        vm.update(api.rulesList.first { it.id == 2 }, Effect.ALLOW, "doch ok")
        assertEquals(Effect.ALLOW, vm.state.value.visible.first { it.id == 2 }.effect)
        assertEquals("doch ok", vm.state.value.visible.first { it.id == 2 }.note)

        vm.delete(api.rulesList.first { it.id == 3 })
        assertEquals(listOf(1, 2), vm.state.value.visible.map { it.id })
        assertEquals("Regel gelöscht", vm.state.value.message)
    }
}
