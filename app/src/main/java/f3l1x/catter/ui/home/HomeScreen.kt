package f3l1x.catter.ui.home

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import f3l1x.catter.data.LocalAppPreferences
import f3l1x.catter.service.DownloadManager
import f3l1x.catter.service.Downloader
import f3l1x.catter.service.Quality
import f3l1x.catter.service.SaveLocation
import f3l1x.catter.service.VideoDetails
import f3l1x.catter.service.YouTube
import f3l1x.catter.ui.components.CatterDialog
import f3l1x.catter.ui.components.CatterTextField
import f3l1x.catter.ui.components.DownloadProgressCard
import f3l1x.catter.ui.components.ItemDetails
import f3l1x.catter.util.Haptics
import f3l1x.catter.util.Logger
import io.tabler.icons.TablerIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private val GlitchGlyphs = listOf("e", "é", "è", "ê", "ë", "ē", "ė", "ę", "ε", "е")

private data class GlitchFrame(
    val glyph: String,
    val offsetY: Dp = 0.dp,
    val scale: Float = 1f,
    val rotation: Float = 0f,
)

private val RestFrame = GlitchFrame("e")

private sealed interface HomeCard {
    data object Empty : HomeCard
    data class Details(val details: VideoDetails) : HomeCard
    data class Download(val details: VideoDetails, val quality: Quality, val id: String) : HomeCard
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val preferences = LocalAppPreferences.current

    var url by rememberSaveable { mutableStateOf("") }
    var showPlatforms by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var card by remember { mutableStateOf<HomeCard>(HomeCard.Empty) }
    var requestedQuality by remember { mutableStateOf<Quality?>(null) }

    val busy = card is HomeCard.Download
    val hasDetails = card is HomeCard.Details

    fun startDownload(details: VideoDetails, quality: Quality) {
        if (card is HomeCard.Download) return
        Logger.d("download start: ${quality.name}")
        val destination = SaveLocation.destination(preferences.saveTreeUri, quality)
        val id = DownloadManager.start(context, details, quality, destination)
        card = HomeCard.Download(details, quality, id)
    }

    LaunchedEffect(Unit) {
        DownloadManager.finished.collect { event ->
            val current = card as? HomeCard.Download ?: return@collect
            if (current.id != event.id) return@collect

            if (event.error == null) {
                Haptics.lightSuccess(context)
                message = "Saved to Download"
                messageIsError = false
                card = HomeCard.Empty
                url = ""
            } else {
                message = event.error
                messageIsError = true
                card = HomeCard.Details(current.details)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val quality = requestedQuality
        requestedQuality = null
        val details = (card as? HomeCard.Details)?.details
        if (granted && quality != null && details != null) {
            startDownload(details, quality)
        } else if (!granted) {
            message = "Storage permission is required"
            messageIsError = true
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3200)
            message = null
        }
    }

    fun submit() {
        if (card is HomeCard.Download) return
        focusManager.clearFocus()
        val videoId = YouTube.videoId(url)
        if (videoId == null) {
            Logger.w("unsupported link")
            card = HomeCard.Empty
            message = "Only YouTube links are supported"
            messageIsError = true
            return
        }
        Logger.d("resolve link: $videoId")
        scope.launch {
            loading = true
            runCatching { YouTube.details(videoId) }
                .onSuccess { loaded ->
                    card = HomeCard.Details(loaded)
                    scope.launch { YouTube.prefetchKey(videoId) }
                }
                .onFailure {
                    card = HomeCard.Empty
                    message = "Could not load this video"
                    messageIsError = true
                }
            loading = false
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GlitchTitle()
            Spacer(Modifier.height(6.dp))
            Text(
                text = "A simple native media downloader for a change that uses direct API services.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))
            CatterTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = "Paste a link",
                leadingIcon = TablerIcons.Link,
                trailingIcon = when {
                    hasDetails -> TablerIcons.X
                    busy -> null
                    else -> TablerIcons.Clipboard
                },
                onTrailingClick = {
                    if (hasDetails) {
                        Logger.d("clear item")
                        card = HomeCard.Empty
                        url = ""
                        message = null
                    } else {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val pasted = clipboard.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                            ?.trim()
                        if (!pasted.isNullOrEmpty()) {
                            Logger.d("pasted link: ${pasted.take(40)}")
                            url = pasted
                            submit()
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }

            message?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (messageIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            AnimatedContent(
                targetState = card,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(initialScale = 0.97f, animationSpec = tween(220))) togetherWith
                        (fadeOut(tween(160)) + scaleOut(targetScale = 0.98f, animationSpec = tween(160))) using
                        SizeTransform(clip = false)
                },
                contentKey = { it::class },
                label = "home_card",
            ) { current ->
                when (current) {
                    HomeCard.Empty -> Spacer(Modifier.height(0.dp))

                    is HomeCard.Details -> ItemDetails(
                        details = current.details,
                        onSelect = { quality ->
                            val needsPermission = Downloader.requiresLegacyPermission &&
                                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                                PackageManager.PERMISSION_GRANTED
                            if (needsPermission) {
                                requestedQuality = quality
                                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                startDownload(current.details, quality)
                            }
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    )

                    is HomeCard.Download -> HomeDownloadCard(
                        id = current.id,
                        title = current.details.title,
                        thumbnailUrl = current.details.thumbnailUrl,
                        qualityLabel = current.quality.label,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Supported platforms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = "click here",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showPlatforms = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }

    if (showPlatforms) {
        SupportedPlatformsDialog(onDismissRequest = { showPlatforms = false })
    }
}

@Composable
private fun HomeDownloadCard(
    id: String,
    title: String,
    thumbnailUrl: String,
    qualityLabel: String,
    modifier: Modifier = Modifier,
) {
    val tasks by DownloadManager.tasks.collectAsState()
    val progress = tasks.firstOrNull { it.id == id }?.progress

    DownloadProgressCard(
        title = title,
        thumbnailUrl = thumbnailUrl,
        qualityLabel = qualityLabel,
        progress = progress,
        modifier = modifier,
    )
}

@Composable
private fun SupportedPlatformsDialog(onDismissRequest: () -> Unit) {
    CatterDialog(
        title = "Supported platforms",
        onDismissRequest = onDismissRequest,
    ) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = TablerIcons.Youtube,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "YouTube",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "MP4 · MP3",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "More sources soon!",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun GlitchTitle() {
    val style = MaterialTheme.typography.displayMedium
    val color = MaterialTheme.colorScheme.primary

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Catt", style = style, color = color)
        GlitchGlyph(style = style, color = color)
        Text(text = "r", style = style, color = color)
    }
}

@Composable
private fun GlitchGlyph(style: TextStyle, color: Color) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val glyphWidth = remember(style) { measurer.measure("e", style).size.width }
    var frame by remember { mutableStateOf(RestFrame) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(600L, 1100L))
            frame = if (Random.nextFloat() < 0.4f) {
                RestFrame
            } else {
                GlitchFrame(
                    glyph = GlitchGlyphs.random(),
                    offsetY = (Random.nextFloat() * 4f - 2f).dp,
                    scale = 0.9f + Random.nextFloat() * 0.18f,
                    rotation = Random.nextFloat() * 8f - 4f,
                )
            }
        }
    }

    val offsetY by animateDpAsState(
        targetValue = frame.offsetY,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
        label = "glitch_offset",
    )
    val scale by animateFloatAsState(
        targetValue = frame.scale,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
        label = "glitch_scale",
    )
    val rotation by animateFloatAsState(
        targetValue = frame.rotation,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
        label = "glitch_rotation",
    )

    Box(
        modifier = Modifier
            .width(with(density) { glyphWidth.toDp() })
            .graphicsLayer {
                translationY = offsetY.toPx()
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = frame.glyph,
            animationSpec = tween(280),
            label = "glitch_glyph",
        ) { current ->
            Text(
                text = current,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
        }
    }
}
