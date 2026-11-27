package de.safetube.parent.parents

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.safetube.parent.container
import de.safetube.parent.ui.ConfirmDialog
import de.safetube.parent.ui.Loading
import de.safetube.parent.ui.ScreenScaffold

@Composable
fun ParentsScreen(onLoggedOut: () -> Unit, logout: suspend () -> Unit) {
    val container = LocalContext.current.container
    val vm = viewModel { ParentsViewModel(container.api, logout) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }
    LaunchedEffect(state.loggedOut) { if (state.loggedOut) onLoggedOut() }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }
    var adding by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Eltern", snackbar = snackbar) { padding ->
        val me = state.me
        if (me == null) {
            Loading(Modifier.padding(padding))
            return@ScreenScaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Mein Konto", style = MaterialTheme.typography.titleMedium)
            var name by remember(me.name) { mutableStateOf(me.name) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.rename(name) }, enabled = name.trim() != me.name) { Text("Speichern") }
            }
            Text(me.email, style = MaterialTheme.typography.bodySmall)
            SwitchRow("Mail bei neuer Anfrage", me.mailNewRequest, vm::setMailNewRequest)
            SwitchRow("Mail bei Warnungen (Gerät, Störung)", me.mailAlerts, vm::setMailAlerts)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { changingPassword = true }) { Text("Passwort ändern") }
                TextButton(onClick = { confirmLogout = true }) { Text("Abmelden") }
            }

            HorizontalDivider()
            Text("Alle Eltern (gleichberechtigt)", style = MaterialTheme.typography.titleMedium)
            state.parents.forEach { parent ->
                Column {
                    Text(parent.name + if (parent.id == me.id) " (ich)" else "")
                    Text(parent.email, style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = { adding = true }) { Text("Elternteil hinzufügen") }
        }
    }

    if (adding) AddParentDialog(onAdd = vm::addParent, onDismiss = { adding = false })
    if (changingPassword) PasswordDialog(onChange = vm::changePassword, onDismiss = { changingPassword = false })
    if (confirmLogout) {
        ConfirmDialog(
            title = "Abmelden?",
            text = "Danach kommen auf diesem Handy keine Benachrichtigungen mehr an.",
            confirm = "Abmelden",
            onConfirm = vm::logout,
            onDismiss = { confirmLogout = false },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AddParentDialog(onAdd: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elternteil hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-Mail") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Startpasswort (mind. 8 Zeichen)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, email, password); onDismiss() }) { Text("Anlegen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun PasswordDialog(onChange: (String, String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Passwort ändern") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Neues Passwort") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = repeat,
                    onValueChange = { repeat = it },
                    label = { Text("Wiederholen") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onChange(password, repeat); onDismiss() }) { Text("Ändern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
