package de.safetube.parent.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.safetube.core.api.Effect
import de.safetube.core.api.TargetType
import de.safetube.core.extractor.SearchFilter
import de.safetube.core.extractor.SearchHit
import de.safetube.core.format.Formats
import de.safetube.parent.container
import de.safetube.parent.ui.ChildFilter
import de.safetube.parent.ui.ScreenScaffold

@Composable
fun AddRuleScreen(onDone: () -> Unit) {
    val container = LocalContext.current.container
    val vm = viewModel { AddRuleViewModel(container.api, container.youtube).apply { load() } }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    AddRuleContent(state, vm, onDone)
}

@Composable
private fun AddRuleContent(state: AddRuleState, vm: AddRuleViewModel, onBack: () -> Unit) {
    ScreenScaffold(title = "Neue Regel", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = state.mode.ordinal) {
                Tab(selected = state.mode == AddMode.LINK, onClick = { vm.setMode(AddMode.LINK) }, text = { Text("Link einfügen") })
                Tab(selected = state.mode == AddMode.SEARCH, onClick = { vm.setMode(AddMode.SEARCH) }, text = { Text("Suchen") })
            }
            if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val draft = state.draft
                if (draft != null) {
                    item { DraftCard(state, draft, vm) }
                } else if (state.mode == AddMode.LINK) {
                    item {
                        OutlinedTextField(
                            value = state.link,
                            onValueChange = vm::onLink,
                            label = { Text("YouTube-Link (Video oder Kanal)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { vm.inspectLink() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item { Button(onClick = vm::inspectLink, enabled = !state.working) { Text("Prüfen") } }
                } else {
                    item {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = vm::onSearchQuery,
                            label = { Text("Suchbegriff") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(SearchFilter.ALL to "Alles", SearchFilter.VIDEOS to "Videos", SearchFilter.CHANNELS to "Kanäle")
                                .forEach { (filter, label) ->
                                    FilterChip(
                                        selected = state.searchFilter == filter,
                                        onClick = { vm.setSearchFilter(filter); vm.search() },
                                        label = { Text(label) },
                                    )
                                }
                        }
                    }
                    items(state.results) { hit -> SearchHitRow(hit) { vm.pick(hit) } }
                }
                state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            }
        }
    }
}

@Composable
private fun SearchHitRow(hit: SearchHit, onPick: () -> Unit) {
    when (hit) {
        is SearchHit.Video -> ListItem(
            modifier = Modifier.clickable(onClick = onPick),
            overlineContent = { Text("Video" + if (hit.item.isShort) " (Short)" else "") },
            headlineContent = { Text(hit.item.title) },
            supportingContent = { Text("${hit.item.channelName} · ${Formats.duration(hit.item.durationSeconds)}") },
        )
        is SearchHit.Channel -> ListItem(
            modifier = Modifier.clickable(onClick = onPick),
            overlineContent = { Text("Kanal") },
            headlineContent = { Text(hit.item.name) },
        )
    }
    HorizontalDivider()
}

@Composable
private fun DraftCard(state: AddRuleState, draft: RuleDraft, vm: AddRuleViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (draft.targetType == TargetType.VIDEO) "Video" else "Kanal", style = MaterialTheme.typography.labelMedium)
            Text(draft.title.ifEmpty { draft.targetId }, style = MaterialTheme.typography.titleMedium)
            if (draft.targetType == TargetType.VIDEO && draft.channelName.isNotEmpty()) Text(draft.channelName)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.effect == Effect.ALLOW, onClick = { vm.setEffect(Effect.ALLOW) }, label = { Text("Erlauben") })
                FilterChip(selected = state.effect == Effect.BLOCK, onClick = { vm.setEffect(Effect.BLOCK) }, label = { Text("Sperren") })
            }
            Text("Gilt für", style = MaterialTheme.typography.labelMedium)
        }
        ChildFilter(state.children, state.scopeChildId, "Alle Kinder", vm::setScopeChild)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = state.note, onValueChange = vm::onNote, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::save, enabled = !state.saving) { Text("Regel speichern") }
                TextButton(onClick = vm::clearDraft) { Text("Anderes wählen") }
            }
        }
    }
}
