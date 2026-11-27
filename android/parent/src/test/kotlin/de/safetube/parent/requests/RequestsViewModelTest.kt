package de.safetube.parent.requests

import de.safetube.core.api.RequestStatus
import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.MainDispatcherRule
import de.safetube.parent.testing.Samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class RequestsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val api = FakeParentApi().apply {
        childrenList += Samples.child(1, "Mia")
        childrenList += Samples.child(2, "Antonio")
        // offen, aber 40 Tage alt
        requestsList += Samples.request(1, createdAt = Samples.NOW.minusSeconds(40L * 86400))
        requestsList += Samples.request(2, childId = 2)
        requestsList += Samples.request(3, status = RequestStatus.APPROVED)
        requestsList += Samples.request(4, status = RequestStatus.REJECTED, createdAt = Samples.NOW.minusSeconds(10L * 86400))
    }

    private fun viewModel() = RequestsViewModel(api, clock = { Samples.NOW }).apply { load() }

    @Test
    fun `offene oben unabhängig vom Zeitraum, darunter die letzten 30 Tage`() {
        val state = viewModel().state.value

        assertFalse(state.loading)
        assertEquals(listOf("Mia", "Antonio"), state.children.map { it.name })
        assertEquals(setOf(1, 2), state.open.map { it.id }.toSet())
        assertEquals(listOf(3, 4), state.history.map { it.id })
    }

    @Test
    fun `Zeitraum 7 Tage blendet ältere Entscheidungen aus`() {
        val vm = viewModel()

        vm.setPeriod(Period.WEEK)

        assertEquals(listOf(3), vm.state.value.history.map { it.id })
        assertEquals(2, vm.state.value.open.size)
    }

    @Test
    fun `Filter Kind`() {
        val vm = viewModel()

        vm.setChild(2)

        assertEquals(listOf(2), vm.state.value.open.map { it.id })
        assertEquals(emptyList<Int>(), vm.state.value.history.map { it.id })
        assertEquals(2, api.lastRequestFilter?.childId)
    }

    @Test
    fun `Filter Status`() {
        val vm = viewModel()

        vm.setStatus(StatusFilter.OPEN)
        assertEquals(2, vm.state.value.open.size)
        assertEquals(0, vm.state.value.history.size)

        vm.setStatus(StatusFilter.REJECTED)
        assertEquals(0, vm.state.value.open.size)
        assertEquals(listOf(4), vm.state.value.history.map { it.id })
    }
}
