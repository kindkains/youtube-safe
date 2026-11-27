package de.safetube.parent.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.safetube.core.api.Decision
import de.safetube.core.api.DecisionAction
import de.safetube.core.api.RequestStatus
import de.safetube.core.api.Scope
import de.safetube.core.api.attempt
import de.safetube.core.api.userMessage
import de.safetube.parent.MainActivity
import de.safetube.parent.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** [Freigeben]/[Ablehnen] direkt aus der Benachrichtigung (Spec 7: Freigeben gilt nur für das anfragende Kind). */
class DecisionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getIntExtra(MainActivity.EXTRA_REQUEST_ID, -1).takeIf { it > 0 } ?: return
        val action = when (intent.action) {
            ACTION_APPROVE -> DecisionAction.APPROVE
            ACTION_REJECT -> DecisionAction.REJECT
            else -> return
        }
        val pending = goAsync()
        val app = context.container
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (app.sessionStore.load() == null) {
                    app.notifier.showDecisionResult(requestId, "Nicht angemeldet – bitte in der App entscheiden.")
                    return@launch
                }
                attempt { app.api.decide(requestId, Decision(action, Scope.CHILD)) }
                    .onSuccess { request ->
                        val text = if (request.status == RequestStatus.APPROVED) "Freigegeben ✓" else "Abgelehnt"
                        app.notifier.showDecisionResult(requestId, text)
                    }
                    .onFailure { e -> app.notifier.showDecisionResult(requestId, "Hat nicht geklappt: ${e.userMessage()}") }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_APPROVE = "de.safetube.parent.APPROVE"
        const val ACTION_REJECT = "de.safetube.parent.REJECT"
    }
}
