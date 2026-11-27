package de.safetube.parent.overview

import de.safetube.core.api.RequestStatus
import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.MainDispatcherRule
import de.safetube.parent.testing.Samples
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OverviewViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val api = FakeParentApi().apply {
        childrenList += Samples.child(1, "Mia")
        childrenList += Samples.child(2, "Antonio")
        requestsList += Samples.request(1, childId = 1)
        requestsList += Samples.request(2, childId = 1)
        requestsList += Samples.request(3, childId = 1, status = RequestStatus.APPROVED)
        statsByChild[1] = Samples.stats(today = 3900)
    }

    @Test
    fun `Karten mit Schauzeit heute und offenen Anfragen`() {
        val vm = OverviewViewModel(api).apply { refresh() }

        val state = vm.state.value
        assertFalse(state.loading)
        assertNull(state.error)
        assertEquals(listOf("Mia", "Antonio"), state.cards.map { it.child.name })
        assertEquals(3900L, state.cards[0].todaySeconds)
        assertEquals(2, state.cards[0].openRequests)
        assertEquals(0, state.cards[1].openRequests)
        assertEquals(RequestStatus.OPEN, api.lastRequestFilter?.status)
        assertTrue("stats 1 1" in api.calls)
    }

    @Test
    fun `Kind anlegen lädt neu`() {
        val vm = OverviewViewModel(api).apply { refresh() }

        vm.createChild("  Lena ")

        assertTrue("createChild Lena" in api.calls)
        assertEquals(3, vm.state.value.cards.size)
    }

    @Test
    fun `leerer Name wird ignoriert`() {
        val vm = OverviewViewModel(api).apply { refresh() }
        api.calls.clear()

        vm.createChild("   ")

        assertEquals(emptyList<String>(), api.calls)
    }

    @Test
    fun `Fehler wird gemeldet`() {
        api.failure = IOException("weg")

        val vm = OverviewViewModel(api).apply { refresh() }

        assertEquals("Server nicht erreichbar. Internetverbindung prüfen.", vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }
}
