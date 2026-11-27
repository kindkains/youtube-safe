package de.safetube.parent.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.safetube.core.api.DayStat
import de.safetube.core.format.Formats
import de.safetube.parent.container
import de.safetube.parent.ui.ErrorMessage
import de.safetube.parent.ui.Loading
import de.safetube.parent.ui.ScreenScaffold

@Composable
fun StatsScreen(childId: Int, onBack: () -> Unit) {
    val container = LocalContext.current.container
    val vm = viewModel(key = "stats-$childId") { StatsViewModel(container.api, childId) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }
    ScreenScaffold(title = "Statistik ${state.childName}".trim(), onBack = onBack) { padding ->
        val stats = state.stats
        when {
            state.loading && stats == null -> Loading(Modifier.padding(padding))
            stats == null -> ErrorMessage(state.error ?: "Keine Daten.", vm::load, Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 30).forEach { days ->
                            FilterChip(selected = state.days == days, onClick = { vm.setDays(days) }, label = { Text("$days Tage") })
                        }
                    }
                }
                item {
                    Text("Gesamt: ${Formats.duration(stats.totalSeconds.toLong())}", style = MaterialTheme.typography.titleMedium)
                    Text("davon im Belohnungsmodus: ${Formats.duration(stats.rewardSeconds.toLong())} (${state.rewardPercent} %)")
                }
                item { Text("Pro Tag", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(stats.days.reversed(), key = { it.date }) { day -> DayBar(day, state.maxDaySeconds) }
                item { Text("Top-Kanäle", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                if (stats.topChannels.isEmpty()) item { Text("Noch keine.") }
                items(stats.topChannels, key = { it.channelId }) { channel ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(channel.channelName.ifEmpty { channel.channelId }, modifier = Modifier.weight(1f))
                        Text(Formats.duration(channel.seconds.toLong()))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayBar(day: DayStat, max: Int) {
    val fraction = if (max > 0) day.seconds.toFloat() / max else 0f
    val rewardFraction = if (day.seconds > 0) day.rewardSeconds.toFloat() / day.seconds else 0f
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(Formats.dayLabel(day.date), modifier = Modifier.width(72.dp))
        Box(Modifier.weight(1f).height(16.dp)) {
            if (fraction > 0f) {
                Row(Modifier.fillMaxWidth(fraction).fillMaxHeight()) {
                    Box(Modifier.weight(1f - rewardFraction + 0.0001f).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                    if (rewardFraction > 0f) {
                        Box(Modifier.weight(rewardFraction).fillMaxHeight().background(MaterialTheme.colorScheme.tertiary))
                    }
                }
            }
        }
        Text(Formats.duration(day.seconds.toLong()), modifier = Modifier.width(80.dp))
    }
}
