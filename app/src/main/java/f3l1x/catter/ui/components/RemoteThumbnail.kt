package f3l1x.catter.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import f3l1x.catter.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun RemoteThumbnail(url: String, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    val candidates = remember(url) {
        listOf(url, url.replace("maxresdefault", "hqdefault"))
    }

    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.IO) {
            candidates.firstNotNullOfOrNull { candidate ->
                runCatching {
                    val connection = URL(candidate).openConnection() as HttpURLConnection
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    connection.inputStream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                }.onFailure {
                    Logger.w("thumbnail failed: ${candidate.substringAfterLast('/')}", it)
                }.getOrNull()
            }
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
