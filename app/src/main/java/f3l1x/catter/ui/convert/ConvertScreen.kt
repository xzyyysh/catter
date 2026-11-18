package f3l1x.catter.ui.convert

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import f3l1x.catter.R
import f3l1x.catter.data.DownloadRecord
import f3l1x.catter.data.DownloadStore
import f3l1x.catter.data.LocalAppPreferences
import f3l1x.catter.service.convert.ConvertJob
import f3l1x.catter.service.convert.ConvertManager
import f3l1x.catter.service.convert.ConvertPreset
import f3l1x.catter.service.convert.ConvertPresets
import f3l1x.catter.ui.components.CatterDialog
import f3l1x.catter.ui.components.ProgressLine
import f3l1x.catter.util.Logger
import f3l1x.catter.util.formatBytes
import io.tabler.icons.TablerIcons
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun ConvertScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = LocalAppPreferences.current

    val tasks by ConvertManager.tasks.collectAsState()
    val failures by ConvertManager.failures.collectAsState()
    val sources by ConvertPicker.selected.collectAsState()

    var optionsOpen by rememberSaveable { mutableStateOf(false) }
    var preset by remember { mutableStateOf<ConvertPreset?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        if (message != null) {
            delay(2400)
            message = null
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importFile(context, uri)
        }
    }

    val presets = remember(sources) {
        val kinds = sources.mapNotNull { ConvertPresets.sourceKind(it.mimeType, it.fileName) }
        val accepted = kinds.map { setOf(it) }
        ConvertPresets.all.filter { candidate ->
            accepted.all { candidate.accepts.intersect(it).isNotEmpty() }
        }
    }

    LaunchedEffect(presets) {
        if (preset !in presets) preset = null
    }

    fun add() {
        val selectedPreset = preset ?: return
        sources.forEach { record ->
            ConvertManager.start(
                context,
                ConvertJob(
                    sourceTitle = record.title,
                    sourceUri = record.uri,
                    preset = selectedPreset,
                    treeUri = preferences.saveTreeUri,
                    thumbnailUrl = record.thumbnailUrl,
                ),
            )
        }
        ConvertPicker.clear()
        preset = null
        message = context.getString(R.string.message_added_to_queue)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Text(
                text = stringResource(R.string.converter_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        item(key = "card") {
            FolderDropCard(
                files = sources.size,
                ready = sources.isNotEmpty(),
                onClick = { optionsOpen = true },
            )
        }

        if (sources.isNotEmpty() && presets.isEmpty()) {
            item(key = "unsupported") {
                Text(
                    text = stringResource(R.string.converter_unsupported),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (presets.isNotEmpty()) {
            item(key = "format-label") {
                SectionLabel(stringResource(R.string.converter_format))
            }
            items(presets, key = { "preset-${it.label}" }) { candidate ->
                PresetRow(
                    preset = candidate,
                    selected = preset == candidate,
                    onClick = { preset = if (preset == candidate) null else candidate },
                )
            }
            item(key = "add") {
                AddButton(
                    enabled = preset != null,
                    onClick = { add() },
                )
            }
        }

        message?.let { text ->
            item(key = "message") {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (tasks.isNotEmpty()) {
            item(key = "queue") { SectionLabel(stringResource(R.string.downloads_section_queue)) }
            items(tasks, key = { "task-${it.id}" }) { task ->
                QueueRow(task = task, onCancel = { ConvertManager.cancel(task.id) })
            }
        }

        if (failures.isNotEmpty()) {
            item(key = "failed") { SectionLabel(stringResource(R.string.downloads_section_failed)) }
            items(failures, key = { "failure-${it.id}" }) { failure ->
                FailedRow(
                    failure = failure,
                    onDismiss = { ConvertManager.dismissFailure(failure.id) },
                )
            }
        }
    }

    if (optionsOpen) {
        CatterDialog(
            title = stringResource(R.string.converter_source),
            onDismissRequest = { optionsOpen = false },
        ) {
            Spacer(Modifier.height(10.dp))
            OptionRow(
                icon = TablerIcons["download"],
                text = stringResource(R.string.converter_from_downloads),
                onClick = {
                    optionsOpen = false
                    ConvertPicker.open()
                },
            )
            OptionRow(
                icon = TablerIcons["file-import"],
                text = stringResource(R.string.converter_import),
                onClick = {
                    optionsOpen = false
                    importer.launch(arrayOf("*/*"))
                },
            )
        }
    }
}

private fun importFile(context: Context, uri: Uri) {
    runCatching {
        val name = displayName(context, uri)
        val size = sizeOf(context, uri)
        val mime = context.contentResolver.getType(uri).orEmpty()
        val record = DownloadRecord(
            id = UUID.randomUUID().toString(),
            title = name.substringBeforeLast('.'),
            author = "",
            thumbnailUrl = "",
            qualityLabel = "",
            format = name.substringAfterLast('.', "").uppercase(),
            mimeType = mime,
            uri = uri.toString(),
            fileName = name,
            size = size,
            createdAt = System.currentTimeMillis(),
        )
        DownloadStore.add(record)
        ConvertPicker.select(record)
    }.onFailure { Logger.w("import failed", it) }
}

private fun sizeOf(context: Context, uri: Uri): Long =
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
        ?: 0L

private fun displayName(context: Context, uri: Uri): String =
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        ?: uri.lastPathSegment
        ?: "file"

@Composable
private fun OptionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PresetRow(preset: ConvertPreset, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = preset.extension.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    imageVector = TablerIcons["check"],
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AddButton(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = stringResource(R.string.converter_add),
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 14.dp),
        )
    }
}

@Composable
private fun QueueRow(task: ConvertManager.Task, onCancel: () -> Unit) {
    val percent = task.progress?.let { "${(it * 100).roundToInt()}%" }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(task.presetLabel, percent).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                ProgressLine(
                    fraction = task.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onCancel)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = TablerIcons.X,
                    contentDescription = stringResource(R.string.downloads_cancel_download),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun FailedRow(failure: ConvertManager.Failure, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = failure.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${failure.presetLabel} · ${stringResource(R.string.downloads_failed_label)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = failure.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = TablerIcons.X,
                    contentDescription = stringResource(R.string.downloads_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}
