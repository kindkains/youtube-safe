package de.safetube.parent.child

import de.safetube.core.api.DisplayMode
import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.MainDispatcherRule
import de.safetube.parent.testing.Samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChildSettingsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val api = FakeParentApi().apply {
        childrenList += Samples.child(1, "Mia")
        devicesByChild[1] = mutableListOf(Samples.device(4), Samples.device(5, unpaired = true))
    }

    private fun viewModel() = ChildSettingsViewModel(api, childId = 1).apply { load() }

    @Test
    fun `lädt Kind und Geräte`() {
        val state = viewModel().state.value

        assertEquals("Mia", state.child!!.name)
        assertEquals(listOf(4, 5), state.devices.map { it.id })
    }

    @Test
    fun `Anzeige, Shorts und Name ändern`() {
        val vm = viewModel()

        vm.setDisplayMode(DisplayMode.FULL)
        vm.setShorts(true)
        vm.rename("  Mia Sophie ")
        vm.rename("   ")

        val child = vm.state.value.child!!
        assertEquals(DisplayMode.FULL, child.displayMode)
        assertTrue(child.shortsEnabled)
        assertEquals("Mia Sophie", child.name)
        assertEquals(3, api.calls.count { it == "updateChild 1" })
    }

    @Test
    fun `Kopplungscode erzeugen`() {
        val vm = viewModel()

        vm.createPairingCode()

        assertNotNull(vm.state.value.pairing)
        assertEquals("AB12CD34", vm.state.value.pairing!!.code)
    }

    @Test
    fun `Gerät entkoppeln`() {
        val vm = viewModel()

        vm.unpair(vm.state.value.devices.first { it.id == 4 })

        assertNotNull(vm.state.value.devices.first { it.id == 4 }.unpairedAt)
        assertEquals("Gerät entkoppelt", vm.state.value.message)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `Kind löschen`() {
        val vm = viewModel()

        vm.deleteChild()

        assertTrue(vm.state.value.deleted)
        assertTrue("deleteChild 1" in api.calls)
        assertTrue(api.childrenList.none { it.id == 1 })
    }
}
