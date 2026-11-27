package de.safetube.parent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.safetube.android.ui.SafeTubeTheme
import de.safetube.parent.nav.ParentNavHost
import de.safetube.parent.push.PushControl
import de.safetube.parent.push.PushSetupHints
import de.safetube.parent.ui.Loading

class MainActivity : ComponentActivity() {

    /** Anfrage, die über eine Benachrichtigung geöffnet werden soll. */
    private val openRequest = mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openRequest.value = requestIdFrom(intent)
        val appContainer = container
        setContent {
            SafeTubeTheme {
                var loggedIn by remember { mutableStateOf<Boolean?>(null) }
                LaunchedEffect(Unit) {
                    loggedIn = appContainer.sessionStore.load() != null
                    if (loggedIn == true) PushControl.start(this@MainActivity)
                }
                when (val state = loggedIn) {
                    null -> Loading()
                    else -> ParentNavHost(
                        loggedIn = state,
                        openRequestId = openRequest.value,
                        onRequestOpened = { openRequest.value = null },
                        onLoggedIn = {
                            loggedIn = true
                            PushControl.start(this@MainActivity)
                        },
                        onLoggedOut = {
                            PushControl.stop(this@MainActivity)
                            loggedIn = false
                        },
                        overviewHeader = { PushSetupHints() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequest.value = requestIdFrom(intent)
    }

    private fun requestIdFrom(intent: Intent?): Int? =
        intent?.getIntExtra(EXTRA_REQUEST_ID, -1)?.takeIf { it > 0 }

    companion object {
        const val EXTRA_REQUEST_ID = "request_id"
    }
}
