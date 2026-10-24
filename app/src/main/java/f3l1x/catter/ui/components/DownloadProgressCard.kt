package f3l1x.catter.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import f3l1x.catter.service.DownloadProgress
import f3l1x.catter.util.formatBytes
import f3l1x.catter.util.formatSpeed

@Composable
fun DownloadProgressCard(
    title: String,
    thumbnailUrl: String,
    qualityLabel: String,
    progress: DownloadProgress?,
    modifier: Modifier = Modifier,
) {
    val fraction by animateFloatAsState(
        targetValue = progress?.fraction ?: 0f,
        animationSpec = tween(220),
        label = "download_fraction",
    )

    val current = progress
    val status = when {
        current == null -> "Preparing $qualityLabel"
        current.isDeterminate -> "${formatBytes(current.bytes)} / ${formatBytes(current.total)}"
        else -> formatBytes(current.bytes)
    }
    val speed = current?.speed ?: 0L

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RemoteThumbnail(
                    url = thumbnailUrl,
                    modifier = Modifier
                        .size(width = 92.dp, height = 52.dp)
                        .clip(RoundedCornerShape(10.dp)),
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
                    Spacer(Modifier.height(3.dp))
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
                }
            }

            Spacer(Modifier.height(10.dp))

            if (current?.isDeterminate == true) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    gapSize = 0.dp,
                )
            }
        }
    }
}
