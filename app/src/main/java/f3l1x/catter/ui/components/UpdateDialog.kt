package f3l1x.catter.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import f3l1x.catter.BuildConfig
import f3l1x.catter.R
import f3l1x.catter.update.UpdateInfo
import f3l1x.catter.update.UpdateState
import f3l1x.catter.util.formatBytes
import f3l1x.catter.util.formatSpeed
import io.tabler.icons.TablerIcons

@Composable
fun UpdateDialog(
    info: UpdateInfo,
    state: UpdateState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    val downloading = state is UpdateState.Downloading

    CatterDialog(
        title = stringResource(R.string.update_available),
        onDismissRequest = onDismiss,
        actions = { dismiss ->
            TextButton(onClick = dismiss) {
                Text(
                    text = stringResource(
                        if (downloading) R.string.action_hide else R.string.action_later,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDownload, enabled = !downloading) {
                Text(
                    text = stringResource(R.string.action_download),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (downloading) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
    ) {
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = TablerIcons["arrow-right"],
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = info.version,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(12.dp))

        MarkdownText(
            markdown = info.notes,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
        )

        when (state) {
            is UpdateState.Downloading -> {
                Spacer(Modifier.height(14.dp))
                ProgressLine(
                    fraction = if (state.total > 0L) {
                        state.bytes.toFloat() / state.total.toFloat()
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildString {
                        append(formatBytes(state.bytes))
                        if (state.total > 0L) append(" / ").append(formatBytes(state.total))
                        if (state.speed > 0L) append("  ·  ").append(formatSpeed(state.speed))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is UpdateState.Failed -> {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            UpdateState.Idle -> Unit
        }
    }
}
