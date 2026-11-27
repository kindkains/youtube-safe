package de.safetube.parent

import android.app.Application
import android.content.Context

class ParentApp : Application() {
    /** Erst beim ersten Zugriff gebaut — Robolectric-Tests, die ihn nicht brauchen, bleiben schnell. */
    val container: AppContainer by lazy { AppContainer(this) }
}

val Context.container: AppContainer
    get() = (applicationContext as ParentApp).container
