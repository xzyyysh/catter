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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import f3l1x.catter.R
import f3l1x.catter.data.LocalAppPreferences
import f3l1x.catter.service.download.DownloadManager
import f3l1x.catter.service.download.DownloadRequest
import f3l1x.catter.service.download.Downloader
import f3l1x.catter.service.download.Requests
import f3l1x.catter.service.download.SaveLocation
import f3l1x.catter.service.model.MediaItem
import f3l1x.catter.service.model.MediaPost
import f3l1x.catter.service.source.Quality
import f3l1x.catter.service.source.ResolveState
import f3l1x.catter.service.source.Resolver
import f3l1x.catter.service.source.VideoDetails
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
    var pendingDownload by remember { mutableStateOf<(() -> Unit)?>(null) }

    val loading = state is ResolveState.Loading
    val hasCard = state is ResolveState.Video || state is ResolveState.Post
    val canClear = hasCard || loading || url.isNotEmpty()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingDownload
        pendingDownload = null
        if (granted && action != null) {
            action()
        } else if (!granted) {
            message = context.getString(R.string.message_storage_permission_required)
            messageIsError = true
        }
    }

    fun withStoragePermission(action: () -> Unit) {
        val missing = Downloader.requiresLegacyPermission &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (missing) {
            pendingDownload = action
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            action()
        }
    }

    fun queue(request: DownloadRequest) {
        DownloadManager.start(context, request, SaveLocation.destination(preferences.saveTreeUri, request.kind))
    }

    fun startDownload(details: VideoDetails, quality: Quality) {
        withStoragePermission {
            Logger.d("download queued: ${quality.name}")
            queue(Requests.youtube(details, quality))
            Resolver.markQueued(context.getString(R.string.message_added_to_queue))
        }
    }

    fun startItem(post: MediaPost, item: MediaItem, index: Int) {
        withStoragePermission {
            Logger.d("post queued: ${post.id}")
            queue(Requests.post(context, post, item, index))
            Haptics.lightSuccess(context)
            message = context.getString(R.string.message_added_to_queue)
            messageIsError = false
        }
    }

    fun startAll(post: MediaPost) {
        withStoragePermission {
            post.items.forEachIndexed { index, item ->
                queue(Requests.post(context, post, item, index))
            }
            Resolver.markQueued(
                context.getString(R.string.message_added_to_queue_count, post.items.size),
            )
        }
    }

    fun startThumbnail(title: String, author: String, url: String) {
        if (url.isBlank()) return
        withStoragePermission {
            Logger.d("thumbnail queued: $url")
            queue(Requests.thumbnail(context, title, author, url))
            Haptics.lightSuccess(context)
            message = context.getString(R.string.message_added_to_queue)
            messageIsError = false
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
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))
            CatterTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = stringResource(R.string.home_placeholder),
                leadingIcon = TablerIcons.Link,
                trailingIcon = if (canClear) TablerIcons.X else TablerIcons.Clipboard,
                trailingContentDescription = stringResource(
                    if (canClear) R.string.action_clear else R.string.action_paste,
                ),
                onTrailingClick = {
                    if (canClear) {
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
                        onSelect = { quality -> startDownload(current.details, quality) },
                        onDownloadThumbnail = {
                            startThumbnail(
                                title = current.details.title,
                                author = current.details.author,
                                url = current.details.thumbnailUrl,
                            )
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    )

                    is ResolveState.Post -> PostDetails(
                        post = current.post,
                        onDownload = { item, index -> startItem(current.post, item, index) },
                        onDownloadAll = { startAll(current.post) },
                        onDownloadThumbnail = {
                            startThumbnail(
                                title = current.post.title,
                                author = current.post.author,
                                url = current.post.thumbnailUrl,
                            )
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    )

                    else -> Spacer(Modifier.height(0.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_supported_platforms),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = stringResource(R.string.home_click_here),
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
        title = stringResource(R.string.home_supported_platforms),
        onDismissRequest = onDismissRequest,
    ) {
        Spacer(Modifier.height(12.dp))
        PlatformRow(
            icon = TablerIcons.Youtube,
            name = stringResource(R.string.platform_youtube),
            formats = stringResource(R.string.platform_formats_mp4_mp3),
        )
        Spacer(Modifier.height(10.dp))
        PlatformRow(
            icon = TablerIcons["brand-instagram"],
            name = stringResource(R.string.platform_instagram),
            formats = stringResource(R.string.platform_formats_mp4_jpg),
        )
        Spacer(Modifier.height(10.dp))
        PlatformRow(
            icon = TablerIcons["brand-tiktok"],
            name = stringResource(R.string.platform_tiktok),
            formats = stringResource(R.string.platform_formats_mp4_jpg),
        )
        Spacer(Modifier.height(10.dp))
        PlatformRow(
            icon = TablerIcons["brand-pinterest"],
            name = stringResource(R.string.platform_pinterest),
            formats = stringResource(R.string.platform_formats_mp4_gif_jpg),
        )
        Spacer(Modifier.height(10.dp))
        PlatformRow(
            icon = TablerIcons["brand-reddit"],
            name = stringResource(R.string.platform_reddit),
            formats = stringResource(R.string.platform_formats_mp4_gif_jpg),
        )
        Spacer(Modifier.height(10.dp))
        PlatformRow(
            icon = TablerIcons["brand-x"],
            name = stringResource(R.string.platform_x),
            formats = stringResource(R.string.platform_formats_mp4),
        )
        Text(
            text = stringResource(R.string.home_more_sources),
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
