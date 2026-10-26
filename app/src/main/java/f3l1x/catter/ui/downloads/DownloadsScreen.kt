package f3l1x.catter.ui.downloads

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import f3l1x.catter.R
import f3l1x.catter.data.DownloadRecord
import f3l1x.catter.data.DownloadStore
import f3l1x.catter.service.download.DownloadFiles
import f3l1x.catter.service.download.DownloadManager
import f3l1x.catter.service.download.DownloadStatus
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

private const val LONG_ERROR = 90

@Composable
fun DownloadsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val records by DownloadStore.records.collectAsState()
    val tasks by DownloadManager.tasks.collectAsState()
    val failures by DownloadManager.failures.collectAsState()

    var existence by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<DownloadRecord?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failedDetails by remember { mutableStateOf<DownloadManager.Failure?>(null) }

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
            message = context.getString(R.string.message_deleted)
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
            message = context.getString(R.string.message_history_cleared)
        }
    }

    fun locate(record: DownloadRecord) {
        val intent = DownloadFiles.viewIntent(context, record) ?: return
        runCatching { context.startActivity(intent) }
            .onFailure {
                Logger.w("no activity for ${record.fileName}", it)
                message = context.getString(R.string.message_no_app_for_file)
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
            item(key = "queue") { SectionLabel(stringResource(R.string.downloads_section_queue)) }
            items(tasks, key = { "task-${it.id}" }) { task ->
                QueueRow(task = task, onCancel = { DownloadManager.cancel(task.id) })
            }
        }

        if (failures.isNotEmpty()) {
            item(key = "failed") { SectionLabel(stringResource(R.string.downloads_section_failed)) }
            items(failures, key = { "failure-${it.id}" }) { failure ->
                FailedRow(
                    failure = failure,
                    onDetails = { failedDetails = failure },
                    onDismiss = { DownloadManager.dismissFailure(failure.id) },
                )
            }
        }

        if (records.isNotEmpty()) {
            item(key = "history") { SectionLabel(stringResource(R.string.downloads_section_history)) }
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

        if (tasks.isEmpty() && records.isEmpty() && failures.isEmpty()) {
            item(key = "empty") { EmptyState() }
        }
    }

    pendingDelete?.let { record ->
        val missing = existence[record.id] == false
        CatterDialog(
            title = stringResource(
                if (missing) R.string.downloads_remove_history_title else R.string.downloads_delete_file_title,
            ),
            onDismissRequest = { pendingDelete = null },
            actions = { dismiss ->
                TextButton(onClick = dismiss) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                TextButton(onClick = {
                    deleteRecord(record)
                    dismiss()
                }) {
                    Text(
                        text = stringResource(
                            if (missing) R.string.action_remove else R.string.action_delete,
                        ),
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
                    stringResource(R.string.downloads_missing_file_message)
                } else {
                    stringResource(R.string.downloads_delete_file_message, formatBytes(record.size))
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
            title = stringResource(R.string.downloads_delete_all_title),
            onDismissRequest = { confirmClearAll = false },
            actions = { dismiss ->
                TextButton(onClick = dismiss) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                TextButton(onClick = {
                    clearAll()
                    dismiss()
                }) {
                    Text(
                        text = stringResource(R.string.action_delete),
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
                        append(
                            context.resources.getQuantityString(
                                R.plurals.downloads_delete_all_message,
                                present.size,
                                present.size,
                                formatBytes(totalSize),
                            ),
                        )
                    } else {
                        append(context.getString(R.string.downloads_delete_all_none))
                    }
                    if (missingCount > 0) {
                        append(" ")
                        append(
                            context.resources.getQuantityString(
                                R.plurals.downloads_delete_all_missing,
                                missingCount,
                                missingCount,
                            ),
                        )
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    failedDetails?.let { failure ->
        CatterDialog(
            title = stringResource(R.string.downloads_failed_title),
            onDismissRequest = { failedDetails = null },
        ) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = failure.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            ErrorBlock(
                text = failure.message,
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("download error", failure.message))
                },
            )
        }
    }
}

@Composable
private fun ErrorBlock(text: String, onCopy: () -> Unit) {
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        if (copied) R.string.downloads_copied_label else R.string.downloads_error_label,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (copied) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            onCopy()
                            copied = true
                        }
                        .padding(4.dp),
                ) {
                    Icon(
                        imageVector = TablerIcons.Clipboard,
                        contentDescription = stringResource(R.string.downloads_copy_error),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
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
                text = stringResource(R.string.downloads_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (count > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.downloads_summary,
                        count,
                        count,
                        formatBytes(totalSize),
                    ),
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
                    contentDescription = stringResource(R.string.downloads_delete_all),
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
    val context = LocalContext.current

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
                    text = "${record.qualityLabel} ${record.format} · ${formatBytes(record.size)} · ${formatDate(context, record.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (missing) {
                    Text(
                        text = stringResource(R.string.downloads_file_deleted),
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
                        contentDescription = stringResource(R.string.downloads_more_options),
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
                                Text(
                                    text = stringResource(R.string.downloads_locate_file),
                                    style = MaterialTheme.typography.bodySmall,
                                )
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
                                text = stringResource(
                                    if (missing) {
                                        R.string.downloads_remove_from_history
                                    } else {
                                        R.string.action_delete
                                    },
                                ),
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
private fun QueueRow(task: DownloadManager.Task, onCancel: () -> Unit) {
    val progress = task.progress
    val status = when {
        task.status == DownloadStatus.Queued ->
            stringResource(R.string.downloads_status_queued, task.qualityLabel)

        progress == null -> stringResource(R.string.downloads_status_preparing, task.qualityLabel)
        progress.isDeterminate -> stringResource(
            R.string.downloads_progress,
            formatBytes(progress.bytes),
            formatBytes(progress.total),
        )

        else -> formatBytes(progress.bytes)
    }
    val speed = progress?.speed ?: 0L

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
                url = task.thumbnailUrl,
                modifier = Modifier
                    .size(width = 76.dp, height = 44.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
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
                    fraction = when {
                        task.status == DownloadStatus.Queued -> 0f
                        progress?.isDeterminate == true -> progress.fraction
                        else -> null
                    },
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
private fun FailedRow(
    failure: DownloadManager.Failure,
    onDetails: () -> Unit,
    onDismiss: () -> Unit,
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
                url = failure.thumbnailUrl,
                modifier = Modifier
                    .size(width = 76.dp, height = 44.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = failure.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(
                        failure.qualityLabel,
                        stringResource(R.string.downloads_failed_label),
                    )
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                )
                Text(
                    text = failure.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (failure.message.length > LONG_ERROR) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onDetails)
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = TablerIcons.InfoCircle,
                        contentDescription = stringResource(R.string.downloads_show_full_error),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
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
private fun EmptyState() {
    Text(
        text = stringResource(R.string.downloads_empty),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
    )
}
