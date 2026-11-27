package de.safetube.parent.parents

import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.MainDispatcherRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ParentsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val api = FakeParentApi()
    private var loggedOut = 0

    private fun viewModel() = ParentsViewModel(api, logout = { loggedOut++ }).apply { load() }

    @Test
    fun `eigenes Konto und alle Eltern`() {
        val state = viewModel().state.value

        assertEquals("Sandro", state.me!!.name)
        assertEquals(1, state.parents.size)
    }

    @Test
    fun `Elternteil anlegen mit Prüfung`() {
        val vm = viewModel()

        vm.addParent("Anna", "anna-ohne-at", "langespasswort")
        assertEquals("Bitte eine gültige E-Mail-Adresse angeben.", vm.state.value.error)

        vm.addParent("Anna", "anna@example.org", "kurz")
        assertEquals("Das Passwort braucht mindestens 8 Zeichen.", vm.state.value.error)

        vm.addParent(" Anna ", " anna@example.org ", "langespasswort")
        assertNull(vm.state.value.error)
        assertTrue("createParent anna@example.org" in api.calls)
        assertEquals(2, vm.state.value.parents.size)
        assertEquals("Anna angelegt", vm.state.value.message)
    }

    @Test
    fun `Mail-Einstellungen`() {
        val vm = viewModel()

        vm.setMailAlerts(false)

        assertEquals(false, api.lastMeUpdate!!.mailAlerts)
        assertNull(api.lastMeUpdate!!.mailNewRequest)
        assertFalse(vm.state.value.me!!.mailAlerts)
    }

    @Test
    fun `Passwort ändern`() {
        val vm = viewModel()

        vm.changePassword("neuespasswort", "anderes")
        assertEquals("Die beiden Passwörter sind verschieden.", vm.state.value.error)
        assertNull(api.lastMeUpdate)

        vm.changePassword("neuespasswort", "neuespasswort")
        assertEquals("neuespasswort", api.lastMeUpdate!!.password)
        assertEquals("Passwort geändert", vm.state.value.message)
    }

    @Test
    fun abmelden() = runBlocking {
        val vm = viewModel()

        vm.logout()
        vm.logoutJob?.join()

        assertEquals(1, loggedOut)
        assertTrue(vm.state.value.loggedOut)
    }
}
