package de.safetube.parent.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.safetube.parent.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Nach Neustart oder App-Update den Push-Dienst wieder starten (beides ist vom Hintergrund-Startverbot ausgenommen). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (context.container.sessionStore.load() != null) PushControl.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}
