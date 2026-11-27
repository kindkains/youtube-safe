package de.safetube.parent.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import de.safetube.core.format.Formats
import de.safetube.parent.container
import de.safetube.parent.ui.ErrorMessage
import de.safetube.parent.ui.Loading
import de.safetube.parent.ui.ScreenScaffold
import de.safetube.parent.ui.TextInputDialog
import java.time.Instant

class OverviewActions(
    val onRequests: () -> Unit,
    val onHistory: (Int) -> Unit,
    val onStats: (Int) -> Unit,
    val onReward: (Int) -> Unit,
    val onChild: (Int) -> Unit,
)

@Composable
fun OverviewScreen(actions: OverviewActions, header: @Composable () -> Unit = {}) {
    val container = LocalContext.current.container
    val vm = viewModel { OverviewViewModel(container.api) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    OverviewContent(state, Instant.now(), actions, header, vm::refresh, vm::createChild)
}

@Composable
fun OverviewContent(
    state: OverviewState,
    now: Instant,
    actions: OverviewActions,
    header: @Composable () -> Unit,
    onRefresh: () -> Unit,
    onCreateChild: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    ScreenScaffold(
        title = "Übersicht",
        actions = { IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Neu laden") } },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Kind anlegen") },
            )
        },
    ) { padding ->
        when {
            state.loading && state.cards.isEmpty() -> Loading(Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { header() }
                state.error?.let { message -> item { ErrorMessage(message, onRefresh) } }
                if (state.cards.isEmpty() && state.error == null) {
                    item { Text("Noch keine Kinder angelegt. Unten rechts geht's los.") }
                }
                items(state.cards, key = { it.child.id }) { card -> ChildCardView(card, now, actions) }
            }
        }
    }
    if (creating) {
        TextInputDialog(
            title = "Kind anlegen",
            label = "Name",
            confirm = "Anlegen",
            onConfirm = onCreateChild,
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun ChildCardView(card: ChildCard, now: Instant, actions: OverviewActions) {
    val child = card.child
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(child.name, style = MaterialTheme.typography.titleLarge)
            Text("Heute geschaut: ${Formats.duration(card.todaySeconds)}")
            if (card.openRequests > 0) {
                TextButton(onClick = actions.onRequests, contentPadding = PaddingValues(0.dp)) {
                    Text("Offene Anfragen: ${card.openRequests}")
                }
            } else {
                Text("Keine offenen Anfragen")
            }
            val until = child.rewardUntil
            if (child.rewardActive && until != null) {
                val extra = if (child.rewardIncludesBlocked) " (auch Gesperrtes)" else ""
                Text("Belohnungsmodus: ${Formats.remaining(until, now)}$extra", color = MaterialTheme.colorScheme.primary)
            }
            Row {
                TextButton(onClick = { actions.onHistory(child.id) }) { Text("Verlauf") }
                TextButton(onClick = { actions.onStats(child.id) }) { Text("Statistik") }
            }
            Row {
                TextButton(onClick = { actions.onReward(child.id) }) { Text("Belohnung") }
                TextButton(onClick = { actions.onChild(child.id) }) { Text("Einstellungen") }
            }
        }
    }
}
