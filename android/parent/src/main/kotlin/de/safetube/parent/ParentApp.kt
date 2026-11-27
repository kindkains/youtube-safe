package de.safetube.parent

import android.app.Application
import android.content.Context
import de.safetube.parent.push.ParentNotifier

class ParentApp : Application() {
    /** Erst beim ersten Zugriff gebaut — Robolectric-Tests, die ihn nicht brauchen, bleiben schnell. */
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        ParentNotifier(this).createChannels()
    }
}

val Context.container: AppContainer
    get() = (applicationContext as ParentApp).container
