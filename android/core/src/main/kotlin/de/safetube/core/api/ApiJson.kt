package de.safetube.core.api

import kotlinx.serialization.json.Json

/** Eine Json-Konfiguration für alle Server-Aufrufe. */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true // neuere Server dürfen Felder ergänzen
    encodeDefaults = true // z. B. scope="child" und note="" immer mitschicken
    explicitNulls = true // child_id=null heißt „alle Kinder“ und muss ankommen
}
