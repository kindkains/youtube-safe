package de.safetube.parent

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.safetube.android.ui.SafeTubeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmokeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Compose mit unserem Theme läuft unter Robolectric`() {
        compose.setContent { SafeTubeTheme { Text("Hallo SafeTube") } }

        compose.onNodeWithText("Hallo SafeTube").assertIsDisplayed()
    }
}
