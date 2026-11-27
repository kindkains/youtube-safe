package de.safetube.parent.requests

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import de.safetube.android.player.PreviewPlayer
import de.safetube.core.api.ContentRequest
import de.safetube.core.api.RequestStatus
import de.safetube.core.api.Scope
import de.safetube.core.api.TargetType
import de.safetube.core.format.Formats
import de.safetube.parent.container
import de.safetube.parent.ui.ErrorMessage
import de.safetube.parent.ui.Loading
import de.safetube.parent.ui.ScreenScaffold
import de.safetube.parent.ui.TextInputDialog

class RequestDetailActions(
    val onBack: () -> Unit,
    val onScope: (Scope) -> Unit,
    val onApprove: () -> Unit,
    val onApproveVideoOnly: () -> Unit,
    val onReject: (String) -> Unit,
    val onRejectBlockTarget: (String) -> Unit,
    val onRejectBlockChannel: (String) -> Unit,
    val onPreview: (videoId: String, title: String) -> Unit,
    val onClosePreview: () -> Unit,
    val onOpenYouTube: (String) -> Unit,
    val onMessageShown: () -> Unit,
)

@Composable
fun RequestDetailScreen(requestId: Int, onBack: () -> Unit, onDecided: (Int) -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val vm = viewModel(key = "request-$requestId") {
        RequestDetailViewModel(container.api, container.youtube, requestId, onDecided)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    RequestDetailContent(
        state,
        RequestDetailActions(
            onBack = onBack,
            onScope = vm::setScope,
            onApprove = vm::approve,
            onApproveVideoOnly = vm::approveVideoOnly,
            onReject = vm::reject,
            onRejectBlockTarget = vm::rejectAndBlockTarget,
            onRejectBlockChannel = vm::rejectAndBlockChannel,
            onPreview = vm::preview,
            onClosePreview = vm::closePreview,
            onOpenYouTube = { url ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            },
            onMessageShown = vm::messageShown,
        ),
    )
}

/** Welche Ablehnung im Dialog gerade bestätigt wird. */
private enum class RejectKind(val title: String) {
    PLAIN("Ablehnen"),
    BLOCK_TARGET("Ablehnen und sperren"),
    BLOCK_CHANNEL("Ablehnen und Kanal sperren"),
}

@Composable
fun RequestDetailContent(state: RequestDetailState, actions: RequestDetailActions) {
    val snackbar = remember { SnackbarHostState() }
    var rejecting by remember { mutableStateOf<RejectKind?>(null) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    ScreenScaffold(title = "Anfrage", onBack = actions.onBack, snackbar = snackbar) { padding ->
        val request = state.request
        when {
            state.loading && request == null -> Loading(Modifier.padding(padding))
            request == null -> ErrorMessage(state.error ?: "Anfrage nicht gefunden.", modifier = Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.preview?.let { preview -> item { PreviewBox(preview, actions.onClosePreview) } }
                item { Header(request) }
                item { DecisionBlock(request, state, actions) { rejecting = it } }
                if (request.targetType == TargetType.CHANNEL) {
                    item { Text("Letzte Videos des Kanals", style = MaterialTheme.typography.titleMedium) }
                    state.channelVideosError?.let { message -> item { ErrorMessage(message) } }
                    items(state.channelVideos, key = { it.videoId }) { video ->
                        ListItem(
                            modifier = Modifier.clickable { actions.onPreview(video.videoId, video.title) },
                            headlineContent = { Text(video.title) },
                            supportingContent = { Text(Formats.duration(video.durationSeconds) + (video.uploadedText?.let { " · $it" } ?: "")) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    rejecting?.let { kind ->
        TextInputDialog(
            title = kind.title,
            label = "Grund (optional, sieht das Kind)",
            confirm = kind.title,
            allowEmpty = true,
            onConfirm = { reason ->
                when (kind) {
                    RejectKind.PLAIN -> actions.onReject(reason)
                    RejectKind.BLOCK_TARGET -> actions.onRejectBlockTarget(reason)
                    RejectKind.BLOCK_CHANNEL -> actions.onRejectBlockChannel(reason)
                }
            },
            onDismiss = { rejecting = null },
        )
    }
}

@Composable
private fun PreviewBox(preview: Preview, onClose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val source = preview.source
        when {
            preview.loading -> Row(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) { CircularProgressIndicator(Modifier.padding(32.dp)) }
            source != null -> PreviewPlayer(source)
            else -> ErrorMessage(preview.error ?: "Vorschau nicht möglich.")
        }
        Row {
            Text(preview.title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(top = 12.dp))
            TextButton(onClick = onClose) { Text("Vorschau schließen") }
        }
    }
}

@Composable
private fun Header(request: ContentRequest) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (request.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = request.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
        }
        Text(request.title.ifEmpty { request.targetId }, style = MaterialTheme.typography.titleLarge)
        val kind = if (request.targetType == TargetType.VIDEO) "Video von ${request.channelName}" else "Ganzer Kanal"
        Text(kind)
        Text("Anfrage von ${request.childName} · ${Formats.dateTime(request.createdAt)}", style = MaterialTheme.typography.bodySmall)
        if (request.liftsBlock) {
            // Festlegung 15: Das Kind fragt zu etwas, das ausdrücklich gesperrt ist.
            Text(
                "Bittet um Aufhebung einer Sperre",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (request.childMessage.isNotBlank()) {
            Text("„${request.childMessage}“", style = MaterialTheme.typography.bodyLarge)
        }
        if (request.status != RequestStatus.OPEN) {
            val verb = if (request.status == RequestStatus.APPROVED) "Freigegeben" else "Abgelehnt"
            val by = request.decidedByName?.let { " von $it" } ?: ""
            val at = request.decidedAt?.let { " am ${Formats.dateTime(it)}" } ?: ""
            Text("$verb$by$at", color = MaterialTheme.colorScheme.primary)
            if (request.reason.isNotBlank()) Text("Grund: ${request.reason}")
        }
    }
}

@Composable
private fun DecisionBlock(
    request: ContentRequest,
    state: RequestDetailState,
    actions: RequestDetailActions,
    onReject: (RejectKind) -> Unit,
) {
    val enabled = !state.busy
    val previewId = if (request.targetType == TargetType.VIDEO) request.targetId else request.sourceVideoId
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (previewId.isNotBlank()) {
                OutlinedButton(onClick = { actions.onPreview(previewId, request.sourceVideoTitle.ifEmpty { request.title }) }) {
                    Text("Vorschau abspielen")
                }
            }
            if (request.url.isNotBlank()) {
                TextButton(onClick = { actions.onOpenYouTube(request.url) }) { Text("In YouTube öffnen") }
            }
        }
        Text(
            if (request.status == RequestStatus.OPEN) "Entscheiden" else "Neu entscheiden",
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.scope == Scope.CHILD, onClick = { actions.onScope(Scope.CHILD) }, label = { Text("Nur ${request.childName}") })
            FilterChip(selected = state.scope == Scope.ALL, onClick = { actions.onScope(Scope.ALL) }, label = { Text("Alle Kinder") })
        }
        Button(onClick = actions.onApprove, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Freigeben") }
        if (request.targetType == TargetType.CHANNEL && request.sourceVideoId.isNotBlank()) {
            OutlinedButton(onClick = actions.onApproveVideoOnly, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text("Nur das Video „${request.sourceVideoTitle.ifEmpty { request.sourceVideoId }}“ freigeben")
            }
        }
        OutlinedButton(onClick = { onReject(RejectKind.PLAIN) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Ablehnen") }
        if (request.targetType == TargetType.VIDEO) {
            OutlinedButton(onClick = { onReject(RejectKind.BLOCK_TARGET) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text("Ablehnen + Video sperren")
            }
        }
        if (request.targetType == TargetType.CHANNEL || request.channelId.isNotBlank()) {
            OutlinedButton(onClick = { onReject(RejectKind.BLOCK_CHANNEL) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text("Ablehnen + Kanal sperren")
            }
        }
    }
}
