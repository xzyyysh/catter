package f3l1x.catter.ui.downloads

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import f3l1x.catter.data.DownloadRecord
import f3l1x.catter.data.DownloadStore
import f3l1x.catter.service.DownloadFiles
import f3l1x.catter.service.DownloadManager
import f3l1x.catter.service.DownloadProgress
import f3l1x.catter.ui.components.CatterDialog
import f3l1x.catter.ui.components.ProgressLine
import f3l1x.catter.ui.components.RemoteThumbnail
import f3l1x.catter.util.Logger
import f3l1x.catter.util.formatBytes
import f3l1x.catter.util.formatDate
import f3l1x.catter.util.formatSpeed
import io.tabler.icons.TablerIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DownloadsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val records by DownloadStore.records.collectAsState()
    val tasks by DownloadManager.tasks.collectAsState()

    var existence by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<DownloadRecord?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
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

    fun deleteRecord(record: DownloadRecord) {
        scope.launch {
            withContext(Dispatchers.IO) { DownloadFiles.delete(context, record) }
            DownloadStore.remove(record.id)
            message = "Deleted"
        }
    }

    fun clearAll() {
        scope.launch {
            withContext(Dispatchers.IO) {
                records.forEach { record ->
                    if (existence[record.id] != false) DownloadFiles.delete(context, record)
                }
            }
            DownloadStore.clear()
            message = "History cleared"
        }
    }

    fun locate(record: DownloadRecord) {
        val intent = DownloadFiles.viewIntent(context, record) ?: return
        runCatching { context.startActivity(intent) }
            .onFailure {
                Logger.w("no activity for ${record.fileName}", it)
                message = "No app can open this file"
            }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            DownloadsHeader(
                count = records.size,
                totalSize = records.sumOf { it.size },
                onClearAll = { confirmClearAll = true },
            )
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
            item(key = "in_progress") { SectionLabel("In progress") }
            items(tasks, key = { "task-${it.id}" }) { task ->
                ActiveDownloadRow(
                    title = task.title,
                    thumbnailUrl = task.thumbnailUrl,
                    qualityLabel = task.qualityLabel,
                    progress = task.progress,
                )
            }
        }

        if (records.isNotEmpty()) {
            item(key = "history") { SectionLabel("History") }
            items(records, key = { "record-${it.id}" }) { record ->
                HistoryRow(
                    record = record,
                    missing = existence[record.id] == false,
                    expanded = expandedId == record.id,
                    onExpand = { expanded -> expandedId = if (expanded) record.id else null },
                    onLocate = {
                        expandedId = null
                        locate(record)
                    },
                    onDelete = {
                        expandedId = null
                        pendingDelete = record
                    },
                )
            }
        }

        if (tasks.isEmpty() && records.isEmpty()) {
            item(key = "empty") { EmptyState() }
        }
    }

    pendingDelete?.let { record ->
        val missing = existence[record.id] == false
        CatterDialog(
            title = if (missing) "Remove from history?" else "Delete file?",
            onDismissRequest = { pendingDelete = null },
            actions = { dismiss ->
                TextButton(onClick = dismiss) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = {
                    deleteRecord(record)
                    dismiss()
                }) {
                    Text(
                        text = if (missing) "Remove" else "Delete",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = record.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (missing) {
                    "The file is already gone from your device. This removes the entry from history."
                } else {
                    "${formatBytes(record.size)} will be permanently deleted from your device."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmClearAll) {
        val present = records.filter { existence[it.id] != false }
        val missingCount = records.size - present.size
        val totalSize = present.sumOf { it.size }

        CatterDialog(
            title = "Delete all downloads?",
            onDismissRequest = { confirmClearAll = false },
            actions = { dismiss ->
                TextButton(onClick = dismiss) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = {
                    clearAll()
                    dismiss()
                }) {
                    Text(
                        text = "Delete all",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = buildString {
                    if (present.isNotEmpty()) {
                        append("${present.size} files (${formatBytes(totalSize)}) will be permanently deleted.")
                    } else {
                        append("No files are left on your device.")
                    }
                    if (missingCount > 0) {
                        append(" ")
                        append("$missingCount entries already marked as deleted will be removed from history.")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadsHeader(count: Int, totalSize: Long, onClearAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Downloads",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (count > 0) {
                Text(
                    text = "$count files · ${formatBytes(totalSize)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (count > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClearAll)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = TablerIcons["trash"],
                    contentDescription = "Delete all downloads",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
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

@Composable
private fun HistoryRow(
    record: DownloadRecord,
    missing: Boolean,
    expanded: Boolean,
    onExpand: (Boolean) -> Unit,
    onLocate: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
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
                    text = "${record.qualityLabel} ${record.format} · ${formatBytes(record.size)} · ${formatDate(record.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (missing) {
                    Text(
                        text = "File is deleted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Box {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onExpand(true) }
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = TablerIcons["dots-vertical"],
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpand(false) },
                ) {
                    if (!missing) {
                        DropdownMenuItem(
                            text = {
                                Text("Locate file", style = MaterialTheme.typography.bodySmall)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = TablerIcons["folder-open"],
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = onLocate,
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (missing) "Remove from history" else "Delete",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = TablerIcons["trash"],
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveDownloadRow(
    title: String,
    thumbnailUrl: String,
    qualityLabel: String,
    progress: DownloadProgress?,
) {
    val status = when {
        progress == null -> "Preparing $qualityLabel"
        progress.isDeterminate -> "${formatBytes(progress.bytes)} / ${formatBytes(progress.total)}"
        else -> formatBytes(progress.bytes)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteThumbnail(
                url = thumbnailUrl,
                modifier = Modifier
                    .size(width = 76.dp, height = 44.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    val speed = progress?.speed ?: 0L
                    if (speed > 0L) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = formatSpeed(speed),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                ProgressLine(
                    fraction = if (progress?.isDeterminate == true) progress.fraction else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Text(
        text = "No downloads yet",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
    )
}
