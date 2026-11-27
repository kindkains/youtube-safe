package de.safetube.parent.push

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

object PushControl {
    private const val TAG = "PushControl"

    /** Nach Login, App-Start mit Sitzung und Neustart: Dienst und 15-Minuten-Abgleich. */
    fun start(context: Context) {
        OpenRequestsWorker.schedule(context)
        startService(context)
    }

    /** Darf scheitern, wenn Android den Start aus dem Hintergrund verbietet (ohne Akku-Ausnahme). */
    fun startService(context: Context) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, ParentPushService::class.java))
        } catch (e: IllegalStateException) { // ForegroundServiceStartNotAllowedException
            Log.w(TAG, "Push-Dienst konnte nicht gestartet werden", e)
        }
    }

    fun stop(context: Context) {
        OpenRequestsWorker.cancel(context)
        context.stopService(Intent(context, ParentPushService::class.java))
    }
}
