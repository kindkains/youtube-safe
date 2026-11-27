package de.safetube.parent.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.safetube.core.api.Effect
import de.safetube.core.api.Rule
import de.safetube.core.api.TargetType
import de.safetube.parent.container
import de.safetube.parent.ui.ConfirmDialog
import de.safetube.parent.ui.ErrorMessage
import de.safetube.parent.ui.Loading
import de.safetube.parent.ui.ScreenScaffold

@Composable
fun RulesScreen(onAdd: () -> Unit) {
    val container = LocalContext.current.container
    val vm = viewModel { RulesViewModel(container.api) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }
    RulesContent(state, onAdd, vm::setScope, vm::setQuery, vm::update, vm::delete, vm::messageShown, vm::load)
}

@Composable
fun RulesContent(
    state: RulesState,
    onAdd: () -> Unit,
    onScope: (RuleScope) -> Unit,
    onQuery: (String) -> Unit,
    onUpdate: (Rule, Effect, String) -> Unit,
    onDelete: (Rule) -> Unit,
    onMessageShown: () -> Unit,
    onRetry: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Rule?>(null) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    ScreenScaffold(
        title = "Regeln",
        snackbar = snackbar,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Neue Regel") })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = state.scope == RuleScope.All, onClick = { onScope(RuleScope.All) }, label = { Text("Alle Regeln") })
                FilterChip(selected = state.scope == RuleScope.Global, onClick = { onScope(RuleScope.Global) }, label = { Text("Für alle Kinder") })
                state.children.forEach { child ->
                    FilterChip(
                        selected = state.scope == RuleScope.ForChild(child.id),
                        onClick = { onScope(RuleScope.ForChild(child.id)) },
                        label = { Text(child.name) },
                    )
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Suchen") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                state.loading && state.rules.isEmpty() -> Loading()
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    state.error?.let { message -> item { ErrorMessage(message, onRetry) } }
                    if (state.visible.isEmpty() && state.error == null) {
                        item { Text("Keine Regeln.", Modifier.padding(16.dp)) }
                    }
                    items(state.visible, key = { it.id }) { rule ->
                        val kind = if (rule.targetType == TargetType.VIDEO) "Video" else "Kanal"
                        val effect = if (rule.effect == Effect.ALLOW) "erlaubt" else "gesperrt"
                        ListItem(
                            modifier = Modifier.clickable { editing = rule },
                            headlineContent = { Text(rule.title.ifEmpty { rule.targetId }) },
                            overlineContent = { Text("$kind · ${state.scopeLabel(rule.childId)}") },
                            supportingContent = {
                                Column {
                                    if (rule.targetType == TargetType.VIDEO && rule.channelName.isNotEmpty()) Text(rule.channelName)
                                    if (rule.note.isNotEmpty()) Text(rule.note, style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            trailingContent = {
                                Text(
                                    effect,
                                    color = if (rule.effect == Effect.ALLOW) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    editing?.let { rule ->
        RuleEditDialog(rule, onSave = { effect, note -> onUpdate(rule, effect, note) }, onDelete = { onDelete(rule) }, onDismiss = { editing = null })
    }
}

@Composable
private fun RuleEditDialog(rule: Rule, onSave: (Effect, String) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var effect by remember { mutableStateOf(rule.effect) }
    var note by remember { mutableStateOf(rule.note) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(rule.title.ifEmpty { rule.targetId }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = effect == Effect.ALLOW, onClick = { effect = Effect.ALLOW }, label = { Text("Erlauben") })
                    FilterChip(selected = effect == Effect.BLOCK, onClick = { effect = Effect.BLOCK }, label = { Text("Sperren") })
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notiz") })
                TextButton(onClick = { confirmDelete = true }) { Text("Regel löschen", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(effect, note); onDismiss() }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
    if (confirmDelete) {
        ConfirmDialog(
            title = "Regel löschen?",
            text = "Danach gilt für dieses Ziel wieder die nächste passende Regel bzw. „gesperrt“.",
            confirm = "Löschen",
            onConfirm = { onDelete(); onDismiss() },
            onDismiss = { confirmDelete = false },
        )
    }
}
