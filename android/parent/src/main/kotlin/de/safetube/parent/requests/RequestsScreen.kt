package de.safetube.parent.requests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import de.safetube.core.api.ContentRequest
import de.safetube.core.api.RequestStatus
import de.safetube.core.api.TargetType
import de.safetube.core.format.Formats
import de.safetube.parent.container
import de.safetube.parent.ui.ChildFilter
import de.safetube.parent.ui.ErrorMessage
import de.safetube.parent.ui.Loading
import de.safetube.parent.ui.ScreenScaffold

@Composable
fun RequestsScreen(onOpen: (Int) -> Unit) {
    val container = LocalContext.current.container
    val vm = viewModel { RequestsViewModel(container.api) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }
    RequestsContent(state, onOpen, vm::setChild, vm::setStatus, vm::setPeriod, vm::load)
}

@Composable
fun RequestsContent(
    state: RequestsState,
    onOpen: (Int) -> Unit,
    onChild: (Int?) -> Unit,
    onStatus: (StatusFilter) -> Unit,
    onPeriod: (Period) -> Unit,
    onRefresh: () -> Unit,
) {
    ScreenScaffold(
        title = "Anfragen",
        actions = { IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Neu laden") } },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ChildFilter(state.children, state.childId, "Alle Kinder", onChild)
            ChipRow(StatusFilter.entries, state.status, { it.label }, onStatus)
            ChipRow(Period.entries, state.period, { it.label }, onPeriod)
            when {
                state.loading && state.open.isEmpty() && state.history.isEmpty() -> Loading()
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    state.error?.let { message -> item { ErrorMessage(message, onRefresh) } }
                    if (state.status == StatusFilter.ALL || state.status == StatusFilter.OPEN) {
                        item { SectionTitle("Offen (${state.open.size})") }
                        if (state.open.isEmpty()) item { Text("Keine offenen Anfragen.", Modifier.padding(16.dp)) }
                        items(state.open, key = { "o${it.id}" }) { RequestRow(it, onOpen) }
                    }
                    if (state.status != StatusFilter.OPEN) {
                        item { SectionTitle("Entschieden") }
                        if (state.history.isEmpty()) item { Text("Nichts im gewählten Zeitraum.", Modifier.padding(16.dp)) }
                        items(state.history, key = { "h${it.id}" }) { RequestRow(it, onOpen) }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun RequestRow(request: ContentRequest, onOpen: (Int) -> Unit) {
    val kind = (if (request.targetType == TargetType.VIDEO) "Video" else "Kanal") +
        (if (request.liftsBlock) " (gesperrt)" else "")
    val status = when (request.status) {
        RequestStatus.OPEN -> "offen"
        RequestStatus.APPROVED -> "freigegeben" + (request.decidedByName?.let { " von $it" } ?: "")
        RequestStatus.REJECTED -> "abgelehnt" + (request.decidedByName?.let { " von $it" } ?: "")
    }
    ListItem(
        modifier = Modifier.clickable { onOpen(request.id) },
        leadingContent = if (request.thumbnailUrl.isNotBlank()) {
            {
                AsyncImage(
                    model = request.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(96.dp).height(54.dp),
                )
            }
        } else {
            null
        },
        headlineContent = { Text(request.title.ifEmpty { request.targetId }) },
        supportingContent = { Text("$kind · ${request.childName} · ${Formats.dateTime(request.createdAt)} · $status") },
    )
    HorizontalDivider()
}
