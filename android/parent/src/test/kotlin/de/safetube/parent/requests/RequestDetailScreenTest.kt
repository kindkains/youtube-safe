package de.safetube.parent.requests

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.safetube.android.ui.SafeTubeTheme
import de.safetube.core.api.TargetType
import de.safetube.parent.testing.Samples
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RequestDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val clicks = mutableListOf<String>()

    private val actions = RequestDetailActions(
        onBack = {},
        onScope = { clicks += "scope $it" },
        onApprove = { clicks += "approve" },
        onApproveVideoOnly = { clicks += "videoOnly" },
        onReject = { clicks += "reject $it" },
        onRejectBlockTarget = { clicks += "blockTarget $it" },
        onRejectBlockChannel = { clicks += "blockChannel $it" },
        onPreview = { id, _ -> clicks += "preview $id" },
        onClosePreview = {},
        onOpenYouTube = { clicks += "open $it" },
        onMessageShown = {},
    )

    @Test
    fun `Video-Anfrage bietet Video- und Kanalsperre`() {
        val request = Samples.request(1)
        compose.setContent { SafeTubeTheme { RequestDetailContent(RequestDetailState(loading = false, request = request), actions) } }

        compose.onNodeWithText("Freigeben").performScrollTo().performClick()
        compose.onNodeWithText("Ablehnen + Video sperren").performScrollTo().assertExists()
        compose.onNodeWithText("Ablehnen + Kanal sperren").performScrollTo().assertExists()
        compose.onNodeWithText("Vorschau abspielen").performScrollTo().performClick()

        assertEquals(listOf("approve", "preview ${request.targetId}"), clicks)
    }

    @Test
    fun `Kanal-Anfrage mit Ursprungsvideo bietet nur dieses Video`() {
        val request = Samples.request(2, type = TargetType.CHANNEL, sourceVideoId = "srcvideo123")
        compose.setContent { SafeTubeTheme { RequestDetailContent(RequestDetailState(loading = false, request = request), actions) } }

        compose.onNodeWithText("Nur das Video „Ursprungsvideo“ freigeben").performScrollTo().performClick()
        compose.onNodeWithText("Ablehnen + Video sperren").assertDoesNotExist()

        assertEquals(listOf("videoOnly"), clicks)
    }

    @Test
    fun `Aufhebung einer Sperre ist erkennbar und zeigt die Begründung`() {
        val request = Samples.request(1, liftsBlock = true, childMessage = "Brauche ich für Bio")
        compose.setContent { SafeTubeTheme { RequestDetailContent(RequestDetailState(loading = false, request = request), actions) } }

        compose.onNodeWithText("Bittet um Aufhebung einer Sperre").assertExists()
        compose.onNodeWithText("„Brauche ich für Bio“").assertExists()
    }
}
