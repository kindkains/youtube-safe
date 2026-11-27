package de.safetube.parent.reward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.safetube.core.format.Formats
import de.safetube.parent.container
import de.safetube.parent.ui.Loading
import de.safetube.parent.ui.ScreenScaffold
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun RewardScreen(childId: Int, onBack: () -> Unit) {
    val container = LocalContext.current.container
    val vm = viewModel(key = "reward-$childId") { RewardViewModel(container.api, childId).apply { load() } }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }
    ScreenScaffold(title = "Belohnung ${state.child?.name.orEmpty()}".trim(), onBack = onBack, snackbar = snackbar) { padding ->
        if (state.loading && state.child == null) {
            Loading(Modifier.padding(padding))
        } else {
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusCard(state, vm::stop)
                Text("Neu starten", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.mode == RewardMode.DURATION, onClick = { vm.setMode(RewardMode.DURATION) }, label = { Text("Dauer") })
                    FilterChip(selected = state.mode == RewardMode.UNTIL, onClick = { vm.setMode(RewardMode.UNTIL) }, label = { Text("Bis Uhrzeit") })
                }
                if (state.mode == RewardMode.DURATION) DurationFields(state, vm) else UntilFields(state, vm)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auch Gesperrtes erlauben")
                        Text("Sonst bleiben ausdrücklich gesperrte Videos und Kanäle gesperrt.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.includeBlocked, onCheckedChange = vm::setIncludeBlocked)
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = vm::start, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Belohnungsmodus starten") }
            }
        }
    }
}

@Composable
private fun StatusCard(state: RewardState, onStop: () -> Unit) {
    val child = state.child ?: return
    val until = child.rewardUntil
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (child.rewardActive && until != null) {
                Text("Läuft: ${Formats.remaining(until, Instant.now())}", style = MaterialTheme.typography.titleMedium)
                Text("bis ${Formats.dateTime(until)}" + if (child.rewardIncludesBlocked) " · auch Gesperrtes" else "")
                OutlinedButton(onClick = onStop, enabled = !state.busy) { Text("Sofort beenden") }
            } else {
                Text("Kein Belohnungsmodus aktiv.")
            }
        }
    }
}

@Composable
private fun DurationFields(state: RewardState, vm: RewardViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("Tage", state.days, vm::setDays, Modifier.weight(1f))
        NumberField("Stunden", state.hours, vm::setHours, Modifier.weight(1f))
        NumberField("Minuten", state.minutes, vm::setMinutes, Modifier.weight(1f))
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UntilFields(state: RewardState, vm: RewardViewModel) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pickDate = true }) { Text("%02d.%02d.%d".format(state.date.dayOfMonth, state.date.monthValue, state.date.year)) }
        OutlinedButton(onClick = { pickTime = true }) { Text("%02d:%02d Uhr".format(state.time.hour, state.time.minute)) }
    }
    if (pickDate) {
        // DatePicker rechnet in UTC-Mitternacht.
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        vm.setDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Abbrechen") } },
        ) { DatePicker(state = dateState) }
    }
    if (pickTime) {
        val timeState = rememberTimePickerState(initialHour = state.time.hour, initialMinute = state.time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    vm.setTime(LocalTime.of(timeState.hour, timeState.minute))
                    pickTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Abbrechen") } },
        )
    }
}
