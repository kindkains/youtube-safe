package de.safetube.parent.login

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.safetube.android.ui.SafeTubeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoginScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Eingaben und Anmelden gehen an das ViewModel`() {
        val typed = mutableListOf<String>()
        var submitted = 0
        compose.setContent {
            // Zustand halten wie das ViewModel, sonst meldet das Feld beim Fokuswechsel den alten (leeren) Wert.
            var state by remember { mutableStateOf(LoginState(server = "https://s")) }
            SafeTubeTheme {
                LoginContent(
                    state = state,
                    onServer = { typed += "server:$it"; state = state.copy(server = it) },
                    onEmail = { typed += "email:$it"; state = state.copy(email = it) },
                    onPassword = { typed += "password:$it"; state = state.copy(password = it) },
                    onSubmit = { submitted++ },
                )
            }
        }

        compose.onNodeWithTag("email").performTextInput("a@b.de")
        compose.onNodeWithTag("password").performTextInput("pw")
        compose.onNodeWithText("Anmelden").performClick()

        assertEquals(listOf("email:a@b.de", "password:pw"), typed)
        assertEquals(1, submitted)
    }

    @Test
    fun `Fehler wird angezeigt, Knopf ist beim Anmelden gesperrt`() {
        compose.setContent {
            SafeTubeTheme {
                LoginContent(
                    state = LoginState(busy = true, error = "E-Mail oder Passwort falsch."),
                    onServer = {}, onEmail = {}, onPassword = {}, onSubmit = {},
                )
            }
        }

        compose.onNodeWithText("E-Mail oder Passwort falsch.").assertExists()
        compose.onNodeWithText("Anmelden …").assertIsNotEnabled()
    }
}
