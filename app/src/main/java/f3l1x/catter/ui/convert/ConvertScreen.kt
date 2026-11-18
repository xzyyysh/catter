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
import f3l1x.catter.service.convert.ConvertSourceKind
import f3l1x.catter.service.download.DownloadFiles
import f3l1x.catter.ui.components.ProgressLine
import f3l1x.catter.ui.components.RemoteThumbnail
import f3l1x.catter.util.formatBytes
import io.tabler.icons.TablerIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class SourceData(
    val title: String,
    val uri: String,
    val mimeType: String?,
    val fileName: String,
    val thumbnailUrl: String,
)

@Composable
fun ConvertScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = LocalAppPreferences.current

    val records by DownloadStore.records.collectAsState()
    val tasks by ConvertManager.tasks.collectAsState()
    val failures by ConvertManager.failures.collectAsState()

    var mode by rememberSaveable { mutableStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var imported by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var preset by remember { mutableStateOf<ConvertPreset?>(null) }
    var existence by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(records) {
        while (true) {
            existence = withContext(Dispatchers.IO) {
                records.associate { it.id to DownloadFiles.exists(context, it) }
            }
            delay(1200)
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(2400)
            message = null
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            imported = uri to displayName(context, uri)
            selectedId = null
        }
    }

    val source = when {
        mode == 0 -> records.firstOrNull { it.id == selectedId }?.let {
            SourceData(it.title, it.uri, it.mimeType, it.fileName, it.thumbnailUrl)
        }
        else -> imported?.let {
            SourceData(it.second, it.first.toString(), context.contentResolver.getType(it.first), it.second, "")
        }
    }

    val kind = source?.let { ConvertPresets.sourceKind(it.mimeType, it.fileName) }
    val presets = kind?.let { ConvertPresets.forKind(it) } ?: emptyList()
    LaunchedEffect(kind) {
        if (kind == null || preset !in presets) preset = null
    }

    fun add() {
        val selectedSource = source ?: return
        val selectedPreset = preset ?: return
        ConvertManager.start(
            context,
            ConvertJob(
                sourceTitle = selectedSource.title,
                sourceUri = selectedSource.uri,
                preset = selectedPreset,
                treeUri = preferences.saveTreeUri,
                thumbnailUrl = selectedSource.thumbnailUrl,
            ),
        )
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

        item(key = "mode") {
            ModeSelector(
                downloads = stringResource(R.string.converter_from_downloads),
                import = stringResource(R.string.converter_import),
                selected = mode,
                onSelect = {
                    mode = it
                    selectedId = null
                    imported = null
                },
            )
        }

        if (mode == 0) {
            val available = records.filter { existence[it.id] != false }
            if (available.isEmpty()) {
                item(key = "no-downloads") {
                    Text(
                        text = stringResource(R.string.downloads_empty),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(available, key = { "source-${it.id}" }) { record ->
                DownloadSourceRow(
                    record = record,
                    selected = selectedId == record.id,
                    onClick = { selectedId = if (selectedId == record.id) null else record.id },
                )
            }
        } else {
            item(key = "import") {
                ImportRow(
                    fileName = imported?.second,
                    onChoose = { importer.launch(arrayOf("*/*")) },
                )
            }
        }

        source?.let { selected ->
            item(key = "format-label") {
                SectionLabel(stringResource(R.string.converter_format))
            }
            if (kind == null) {
                item(key = "unsupported") {
                    Text(
                        text = stringResource(R.string.converter_unsupported),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
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

        if (source == null && tasks.isEmpty() && failures.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.converter_empty),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun ModeSelector(downloads: String, import: String, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeChip(text = downloads, selected = selected == 0, onClick = { onSelect(0) })
        ModeChip(text = import, selected = selected == 1, onClick = { onSelect(1) })
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun DownloadSourceRow(record: DownloadRecord, selected: Boolean, onClick: () -> Unit) {
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
                .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteThumbnail(
                url = record.thumbnailUrl,
                modifier = Modifier
                    .size(width = 76.dp, height = 44.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${record.format} · ${formatBytes(record.size)}",
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
private fun ImportRow(fileName: String?, onChoose: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onChoose)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = TablerIcons["file-import"],
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.converter_choose_file),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                fileName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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

private fun displayName(context: Context, uri: Uri): String =
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        ?: uri.lastPathSegment
        ?: "file"
