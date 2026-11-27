package de.safetube.parent.push

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.safetube.android.system.BatteryOptimization

/** Hinweiskarten oben in der Übersicht, solange Benachrichtigungen oder die Akku-Ausnahme fehlen. */
@Composable
fun PushSetupHints() {
    val context = LocalContext.current
    fun notificationsAllowed() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var allowed by remember { mutableStateOf(notificationsAllowed()) }
    var ignoringBattery by remember { mutableStateOf(BatteryOptimization.isIgnoring(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = notificationsAllowed()
        ignoringBattery = BatteryOptimization.isIgnoring(context)
        onPauseOrDispose { }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> allowed = granted }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!allowed) {
            HintCard(
                text = "Ohne Erlaubnis für Benachrichtigungen erfährst du nichts von neuen Anfragen.",
                button = "Benachrichtigungen erlauben",
                onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
        }
        if (!ignoringBattery) {
            HintCard(
                text = "Damit Anfragen sofort ankommen, darf Android die App nicht schlafen legen.",
                button = "Akku-Optimierung ausschalten",
                onClick = { context.startActivity(BatteryOptimization.requestIntent(context)) },
            )
        }
    }
}

@Composable
private fun HintCard(text: String, button: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text)
            Button(onClick = onClick) { Text(button) }
        }
    }
}
