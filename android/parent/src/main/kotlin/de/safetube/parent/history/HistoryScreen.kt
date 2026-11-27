package de.safetube.parent.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import de.safetube.core.api.HistoryEntry
import de.safetube.core.api.TargetType
import de.safetube.core.format.Formats
import de.safetube.parent.container
import de.safetube.parent.ui.ErrorMessage
import de.safetube.parent.ui.Loading
import de.safetube.parent.ui.ScreenScaffold

@Composable
fun HistoryScreen(childId: Int, onBack: () -> Unit) {
    val container = LocalContext.current.container
    val vm = viewModel(key = "history-$childId") { HistoryViewModel(container.api, childId) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }
    ScreenScaffold(title = "Verlauf ${state.childName}".trim(), onBack = onBack, snackbar = snackbar) { padding ->
        when {
            state.loading && state.entries.isEmpty() -> Loading(Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                state.error?.let { message -> item { ErrorMessage(message, vm::load) } }
                if (state.entries.isEmpty() && state.error == null) {
                    item { Text("Noch nichts geschaut.", Modifier.padding(16.dp)) }
                }
                items(state.entries, key = { it.id }) { entry -> HistoryRow(entry, vm::setRule) }
                if (state.canLoadMore) {
                    item {
                        TextButton(onClick = vm::loadMore, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.loadingMore) "Lade …" else "Ältere laden")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onRule: (HistoryEntry, TargetType, Effect) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val details = buildList {
        add(Formats.dateTime(entry.startedAt))
        add(Formats.duration(entry.playedSeconds.toLong()))
        if (entry.inRewardMode) add("Belohnungsmodus")
    }.joinToString(" · ")
    ListItem(
        overlineContent = { Text(details) },
        headlineContent = { Text(entry.title.ifEmpty { entry.videoId }) },
        supportingContent = {
            Text(
                "${entry.channelName} · jetzt " + if (entry.allowedNow) "erlaubt" else "gesperrt",
                color = if (entry.allowedNow) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Aktionen") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Video freigeben") }, onClick = { menu = false; onRule(entry, TargetType.VIDEO, Effect.ALLOW) })
                    DropdownMenuItem(text = { Text("Video sperren") }, onClick = { menu = false; onRule(entry, TargetType.VIDEO, Effect.BLOCK) })
                    if (entry.channelId.isNotBlank()) {
                        DropdownMenuItem(text = { Text("Kanal freigeben") }, onClick = { menu = false; onRule(entry, TargetType.CHANNEL, Effect.ALLOW) })
                        DropdownMenuItem(text = { Text("Kanal sperren") }, onClick = { menu = false; onRule(entry, TargetType.CHANNEL, Effect.BLOCK) })
                    }
                }
            }
        },
    )
    HorizontalDivider()
}
