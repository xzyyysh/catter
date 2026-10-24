package f3l1x.catter.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import f3l1x.catter.data.LocalAppPreferences
import f3l1x.catter.service.DownloadManager
import f3l1x.catter.ui.components.ProgressLine
import f3l1x.catter.ui.downloads.DownloadsScreen
import f3l1x.catter.ui.home.HomeScreen
import f3l1x.catter.ui.settings.SettingsScreen
import io.tabler.icons.TablerIcons
import kotlin.math.roundToInt

private enum class CatterDestination(val label: String, val icon: ImageVector) {
    Home("Home", TablerIcons.Home),
    Downloads("Downloads", TablerIcons["download"]),
    Settings("Settings", TablerIcons.Settings),
}

@Composable
fun CatterApp() {
    RequestStartupPermissions()

    var current by rememberSaveable { mutableStateOf(CatterDestination.Home) }
    var appeared by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { appeared = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AnimatedVisibility(
            visible = appeared,
            enter = fadeIn(tween(420)) + slideInVertically(tween(480)) { it / 32 },
            label = "content_entrance",
        ) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val direction = if (forward) 1 else -1
                    (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                        slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)) {
                            direction * it / 6
                        }) togetherWith
                        (fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                            slideOutHorizontally(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) {
                                -direction * it / 6
                            })
                },
                label = "catter_content",
            ) { destination ->
                when (destination) {
                    CatterDestination.Home -> HomeScreen()
                    CatterDestination.Downloads -> DownloadsScreen()
                    CatterDestination.Settings -> SettingsScreen()
                }
            }
        }

        AnimatedVisibility(
            visible = appeared,
            enter = fadeIn(tween(300)) + slideInVertically(
                animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow),
            ) { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            label = "bottom_bar_entrance",
        ) {
            FloatingNavigationBar(
                selected = current,
                onSelect = { current = it },
            )
        }
    }
}

@Composable
private fun FloatingNavigationBar(
    selected: CatterDestination,
    onSelect: (CatterDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val itemBounds = remember { mutableStateMapOf<CatterDestination, Rect>() }
    val containerBounds = remember { mutableStateOf(Rect.Zero) }
    val indicator = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var indicatorReady by remember { mutableStateOf(false) }

    val origin = containerBounds.value.topLeft
    val targetBounds = itemBounds[selected]?.translate(-origin.x, -origin.y)

    LaunchedEffect(targetBounds) {
        val target = targetBounds ?: return@LaunchedEffect
        if (indicatorReady) {
            indicator.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        } else {
            indicator.snapTo(target)
            indicatorReady = true
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
    ) {
        Box(
            modifier = Modifier
                .padding(6.dp)
                .onGloballyPositioned { containerBounds.value = it.boundsInRoot() },
        ) {
            if (indicatorReady) {
                val rect = indicator.value
                Box(
                    modifier = Modifier
                        .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                        .size(
                            width = with(density) { rect.width.toDp() },
                            height = with(density) { rect.height.toDp() },
                        )
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CatterDestination.entries.forEach { destination ->
                    FloatingNavigationItem(
                        destination = destination,
                        selected = destination == selected,
                        onClick = { onSelect(destination) },
                        onBoundsChange = { itemBounds[destination] = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestStartupPermissions() {
    val preferences = LocalAppPreferences.current
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {}

    LaunchedEffect(Unit) {
        if (preferences.permissionsRequested) return@LaunchedEffect

        val missing = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !granted(context, Manifest.permission.POST_NOTIFICATIONS)
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                !granted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            ) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        preferences.markPermissionsRequested()
        if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray())
    }
}

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
private fun NavDownloadProgress() {
    val tasks by DownloadManager.tasks.collectAsState()
    if (tasks.isEmpty()) return

    ProgressLine(
        fraction = null,
        modifier = Modifier
            .width(16.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.error,
        trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.22f),
    )
}

@Composable
private fun FloatingNavigationItem(
    destination: CatterDestination,
    selected: Boolean,
    onClick: () -> Unit,
    onBoundsChange: (Rect) -> Unit,
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
        label = "nav_item_content",
    )

    Row(
        modifier = Modifier
            .onGloballyPositioned { onBoundsChange(it.boundsInRoot()) }
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = destination.icon,
                contentDescription = destination.label,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(2.dp))
            Box(modifier = Modifier.height(2.dp)) {
                if (destination == CatterDestination.Downloads) {
                    NavDownloadProgress()
                }
            }
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = destination.label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}
