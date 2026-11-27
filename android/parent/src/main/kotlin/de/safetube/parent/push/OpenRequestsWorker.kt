package de.safetube.parent.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.safetube.core.api.ApiException
import de.safetube.core.api.RequestFilter
import de.safetube.core.api.RequestStatus
import de.safetube.core.push.OpenRequestCheck
import de.safetube.parent.container
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 15-Minuten-Abgleich (Festlegung 8): verpasste Anfragen nachmelden, Push-Dienst am Leben halten. */
class OpenRequestsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.container
        app.sessionStore.load() ?: return Result.success()
        return try {
            val open = app.api.requests(RequestFilter(status = RequestStatus.OPEN))
            app.sessionStore.newRequests(open).forEach { app.notifier.showRequest(OpenRequestCheck.notificationFor(it)) }
            PushControl.startService(applicationContext)
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: ApiException) {
            if (e.status == 401) Result.success() else Result.retry()
        }
    }

    companion object {
        private const val NAME = "offene-anfragen"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<OpenRequestsWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
