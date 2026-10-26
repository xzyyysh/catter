package f3l1x.catter.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import f3l1x.catter.R
import f3l1x.catter.service.model.MediaItem
import f3l1x.catter.service.model.MediaKind
import f3l1x.catter.service.model.MediaPost
import io.tabler.icons.TablerIcons

@Composable
fun PostDetails(
    post: MediaPost,
    onDownload: (MediaItem, Int) -> Unit,
    onDownloadAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RemoteThumbnail(
                    url = post.thumbnailUrl,
                    modifier = Modifier
                        .size(width = 92.dp, height = 52.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (post.author.isNotBlank()) {
                        Text(
                            text = post.author,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pluralStringResource(
                        R.plurals.post_item_count,
                        post.items.size,
                        post.items.size,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.post_download_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDownloadAll)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(4.dp))

            post.items.forEachIndexed { index, item ->
                PostItemRow(
                    item = item,
                    index = index,
                    onClick = { onDownload(item, index) },
                )
            }
        }
    }
}

@Composable
private fun PostItemRow(item: MediaItem, index: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = when (item.kind) {
                MediaKind.Video -> TablerIcons["video"]
                MediaKind.Gif -> TablerIcons["gif"]
                else -> TablerIcons["photo"]
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label(item.kind, index),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = TablerIcons["download"],
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun label(kind: MediaKind, index: Int): String = when (kind) {
    MediaKind.Video -> stringResource(R.string.item_video_numbered, index + 1)
    MediaKind.Gif -> stringResource(R.string.item_gif_numbered, index + 1)
    MediaKind.Audio -> stringResource(R.string.item_audio_numbered, index + 1)
    MediaKind.Photo -> stringResource(R.string.item_photo_numbered, index + 1)
}
