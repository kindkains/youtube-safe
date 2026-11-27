package de.safetube.android.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** Ausnahme von der Akku-Optimierung, damit die Push-Verbindung nicht schlafen gelegt wird (Spec 8). */
object BatteryOptimization {

    fun isIgnoring(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true

    /** Öffnet den System-Dialog direkt für diese App (nicht für den Play Store gedacht, hier privat). */
    @SuppressLint("BatteryLife")
    fun requestIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
}
