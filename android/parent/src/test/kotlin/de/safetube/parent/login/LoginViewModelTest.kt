package de.safetube.parent.login

import de.safetube.core.api.ApiException
import de.safetube.parent.session.Auth
import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.MainDispatcherRule
import de.safetube.parent.testing.sessionStore
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LoginViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val api = FakeParentApi()

    private fun viewModel(): LoginViewModel {
        val auth = Auth(tmp.sessionStore()) { _, _ -> api }
        return LoginViewModel(auth, defaultServer = "https://safetube.isoletta.me").also {
            runBlocking { it.ready.join() }
        }
    }

    @Test
    fun `Standard-Server ist vorbelegt`() {
        assertEquals("https://safetube.isoletta.me", viewModel().state.value.server)
    }

    @Test
    fun `leere Felder werden nicht abgeschickt`() {
        val vm = viewModel()

        vm.submit()

        assertEquals("Bitte E-Mail und Passwort eingeben.", vm.state.value.error)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `erfolgreicher Login`() = runBlocking {
        val vm = viewModel()
        vm.onEmail("sandro@example.org")
        vm.onPassword("geheim123")

        vm.submit()
        vm.busyJob?.join()

        assertTrue(vm.state.value.loggedIn)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `falsches Passwort und Server weg`() = runBlocking {
        val vm = viewModel()
        vm.onEmail("sandro@example.org")
        vm.onPassword("falsch")

        api.failure = ApiException(401, "Anmeldung fehlgeschlagen")
        vm.submit()
        vm.busyJob?.join()
        assertEquals("E-Mail oder Passwort falsch.", vm.state.value.error)

        api.failure = IOException("weg")
        vm.submit()
        vm.busyJob?.join()
        assertEquals("Server nicht erreichbar. Internetverbindung prüfen.", vm.state.value.error)
        assertFalse(vm.state.value.loggedIn)
    }
}
