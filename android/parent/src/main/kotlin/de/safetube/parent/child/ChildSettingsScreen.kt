package de.safetube.parent.child

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.safetube.core.api.Device
import de.safetube.core.api.DisplayMode
import de.safetube.core.api.PairingCode
import de.safetube.core.format.Formats
import de.safetube.parent.container
import de.safetube.parent.ui.ConfirmDialog
import de.safetube.parent.ui.Loading
import de.safetube.parent.ui.ScreenScaffold

@Composable
fun ChildSettingsScreen(childId: Int, onBack: () -> Unit) {
    val container = LocalContext.current.container
    val vm = viewModel(key = "child-$childId") { ChildSettingsViewModel(container.api, childId).apply { load() } }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var unpairing by remember { mutableStateOf<Device?>(null) }
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }
    ScreenScaffold(title = "Einstellungen ${state.child?.name.orEmpty()}".trim(), onBack = onBack, snackbar = snackbar) { padding ->
        val child = state.child
        if (child == null) {
            Loading(Modifier.padding(padding))
            return@ScreenScaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            var name by remember(child.name) { mutableStateOf(child.name) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.rename(name) }, enabled = name.trim() != child.name) { Text("Speichern") }
            }

            Text("Gesperrte Inhalte in Suche und Vorschlägen", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = child.displayMode == DisplayMode.COMPACT, onClick = { vm.setDisplayMode(DisplayMode.COMPACT) }, label = { Text("Kompakt") })
                FilterChip(selected = child.displayMode == DisplayMode.FULL, onClick = { vm.setDisplayMode(DisplayMode.FULL) }, label = { Text("Vollständig") })
            }
            Text(
                if (child.displayMode == DisplayMode.COMPACT) "Nur Titel und Kanal, kein Vorschaubild." else "Mit Vorschaubild, nur Abspielen ist gesperrt.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Shorts", style = MaterialTheme.typography.titleMedium)
                    Text("Laufen wie normale Videos, ohne Endlos-Wischen.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = child.shortsEnabled, onCheckedChange = vm::setShorts)
            }

            HorizontalDivider()
            Text("Kinderhandy koppeln", style = MaterialTheme.typography.titleMedium)
            val pairing = state.pairing
            if (pairing == null) {
                Button(onClick = vm::createPairingCode) { Text("Kopplungscode erzeugen") }
            } else {
                PairingCard(pairing, vm::closePairing)
            }

            HorizontalDivider()
            Text("Geräte", style = MaterialTheme.typography.titleMedium)
            if (state.devices.isEmpty()) Text("Noch kein Gerät gekoppelt.")
            state.devices.forEach { device -> DeviceRow(device, enabled = !state.busy) { unpairing = device } }

            HorizontalDivider()
            OutlinedButton(onClick = { deleting = true }, enabled = !state.busy) {
                Text("${child.name} löschen", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (deleting) {
        ConfirmDialog(
            title = "${state.child?.name.orEmpty()} löschen?",
            text = "Gelöscht werden die eigenen Regeln, alle Anfragen, Verlauf und Schauzeit; gekoppelte Geräte " +
                "werden abgemeldet. Regeln für alle Kinder bleiben. Das lässt sich nicht rückgängig machen.",
            confirm = "Endgültig löschen",
            onConfirm = { vm.deleteChild() },
            onDismiss = { deleting = false },
        )
    }
    unpairing?.let { device ->
        ConfirmDialog(
            title = "Gerät entkoppeln?",
            text = "Die Kinder-App auf diesem Gerät zeigt danach nur noch „Bitte koppeln lassen“.",
            confirm = "Entkoppeln",
            onConfirm = { vm.unpair(device) },
            onDismiss = { unpairing = null },
        )
    }
}

@Composable
private fun PairingCard(pairing: PairingCode, onClose: () -> Unit) {
    val image = remember(pairing.qrPayload) { QrCode.bitmap(pairing.qrPayload) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(bitmap = image, contentDescription = "Kopplungs-QR-Code", modifier = Modifier.size(240.dp))
            Text(pairing.code, style = MaterialTheme.typography.headlineMedium)
            Text("Gültig bis ${Formats.time(pairing.expiresAt)} Uhr. In der Kinder-App scannen oder abtippen.")
            OutlinedButton(onClick = onClose) { Text("Fertig") }
        }
    }
}

@Composable
private fun DeviceRow(device: Device, enabled: Boolean, onUnpair: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("App ${device.appVersion.ifEmpty { "?" }} · gekoppelt ${Formats.dateTime(device.pairedAt)}")
            val unpaired = device.unpairedAt
            if (unpaired != null) {
                Text("entkoppelt ${Formats.dateTime(unpaired)}", color = MaterialTheme.colorScheme.error)
            } else {
                Text("zuletzt gemeldet ${Formats.dateTime(device.lastSeen)}", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (device.unpairedAt == null) {
            TextButton(onClick = onUnpair, enabled = enabled) { Text("Entkoppeln") }
        }
    }
}
