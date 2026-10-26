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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import f3l1x.catter.service.DownloadRequest
import f3l1x.catter.service.Downloader
import f3l1x.catter.service.MediaItem
import f3l1x.catter.service.MediaPost
import f3l1x.catter.service.Quality
import f3l1x.catter.service.Requests
import f3l1x.catter.service.ResolveState
import f3l1x.catter.service.Resolver
import f3l1x.catter.service.SaveLocation
import f3l1x.catter.service.VideoDetails
import f3l1x.catter.ui.components.CatterDialog
import f3l1x.catter.ui.components.CatterTextField
import f3l1x.catter.ui.components.ItemDetails
import f3l1x.catter.ui.components.PostDetails
import f3l1x.catter.ui.components.ProgressLine
import f3l1x.catter.util.Haptics
import f3l1x.catter.util.Logger
import io.tabler.icons.TablerIcons
import kotlinx.coroutines.delay
import kotlin.random.Random

private val GlitchGlyphs = listOf("e", "é", "è", "ê", "ë", "ē", "ė", "ę", "ε", "е")

private data class GlitchFrame(
    val glyph: String,
    val offsetY: Dp = 0.dp,
    val scale: Float = 1f,
    val rotation: Float = 0f,
)

private val RestFrame = GlitchFrame("e")

private fun cardKey(state: ResolveState): String = when (state) {
    is ResolveState.Video -> "video"
    is ResolveState.Post -> "post"
    else -> "empty"
}

@Composable
fun HomeScreen(
    incomingLink: String?,
    onIncomingLinkConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val preferences = LocalAppPreferences.current

    val state by Resolver.state.collectAsState()
    var url by rememberSaveable { mutableStateOf("") }
    var showPlatforms by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var requestedQuality by remember { mutableStateOf<Quality?>(null) }

    val loading = state is ResolveState.Loading
    val hasCard = state is ResolveState.Video || state is ResolveState.Post

    fun queue(request: DownloadRequest) {
        DownloadManager.start(context, request, SaveLocation.destination(preferences.saveTreeUri, request.kind))
    }

    fun startDownload(details: VideoDetails, quality: Quality) {
        Logger.d("download queued: ${quality.name}")
        queue(Requests.youtube(details, quality))
        Resolver.markQueued("Added to queue")
    }

    fun startItem(post: MediaPost, item: MediaItem, index: Int) {
        Logger.d("post queued: ${post.id}")
        queue(Requests.post(post, item, index))
        Haptics.lightSuccess(context)
        message = "Added to queue"
        messageIsError = false
    }

    fun startAll(post: MediaPost) {
        post.items.forEachIndexed { index, item -> queue(Requests.post(post, item, index)) }
        Resolver.markQueued("${post.items.size} added to queue")
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val quality = requestedQuality
        requestedQuality = null
        val details = (state as? ResolveState.Video)?.details
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

    LaunchedEffect(state) {
        when (val current = state) {
            is ResolveState.Queued -> {
                Haptics.lightSuccess(context)
                url = ""
                message = current.message
                messageIsError = false
                Resolver.clear()
            }

            is ResolveState.Failed -> {
                message = current.message
                messageIsError = true
                Resolver.clear()
            }

            else -> Unit
        }
    }

    LaunchedEffect(incomingLink) {
        val link = incomingLink ?: return@LaunchedEffect
        focusManager.clearFocus()
        url = link
        Resolver.resolve(context, link, preferences.saveTreeUri)
        onIncomingLinkConsumed()
    }

    fun submit() {
        focusManager.clearFocus()
        Resolver.resolve(context, url, preferences.saveTreeUri)
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
                trailingIcon = if (hasCard || loading) TablerIcons.X else TablerIcons.Clipboard,
                onTrailingClick = {
                    if (hasCard || loading) {
                        Logger.d("clear item")
                        message = null
                        Resolver.clear()
                        url = ""
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
                ProgressLine(
                    fraction = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .height(3.dp),
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
                targetState = state,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(initialScale = 0.97f, animationSpec = tween(220))) togetherWith
                        (fadeOut(tween(160)) + scaleOut(targetScale = 0.98f, animationSpec = tween(160))) using
                        SizeTransform(clip = false)
                },
                contentKey = { cardKey(it) },
                label = "home_card",
            ) { current ->
                when (current) {
                    is ResolveState.Video -> ItemDetails(
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

                    is ResolveState.Post -> PostDetails(
                        post = current.post,
                        onDownload = { item, index -> startItem(current.post, item, index) },
                        onDownloadAll = { startAll(current.post) },
                        modifier = Modifier.padding(top = 12.dp),
                    )

                    else -> Spacer(Modifier.height(0.dp))
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
private fun SupportedPlatformsDialog(onDismissRequest: () -> Unit) {
    CatterDialog(
        title = "Supported platforms",
        onDismissRequest = onDismissRequest,
    ) {
        Spacer(Modifier.height(12.dp))
        PlatformRow(
            icon = TablerIcons.Youtube,
            name = "YouTube + Music",
            formats = "MP4 · MP3",
        )
        Spacer(Modifier.height(10.dp))
        PlatformRow(
            icon = TablerIcons["brand-instagram"],
            name = "Instagram",
            formats = "MP4 · JPG",
        )
        Spacer(Modifier.height(10.dp))
        PlatformRow(
            icon = TablerIcons["brand-tiktok"],
            name = "TikTok",
            formats = "MP4 · JPG",
        )
        Spacer(Modifier.height(10.dp))
        PlatformRow(
            icon = TablerIcons["brand-pinterest"],
            name = "Pinterest",
            formats = "MP4 · GIF · JPG",
        )
        Spacer(Modifier.height(10.dp))
        PlatformRow(
            icon = TablerIcons["brand-reddit"],
            name = "Reddit",
            formats = "MP4 · GIF · JPG",
        )
        Text(
            text = "More sources soon!",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun PlatformRow(icon: ImageVector, name: String, formats: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formats,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
