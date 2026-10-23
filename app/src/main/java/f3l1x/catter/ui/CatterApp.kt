package f3l1x.catter.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import f3l1x.catter.ui.home.HomeScreen
import f3l1x.catter.ui.settings.SettingsScreen
import io.tabler.icons.TablerIcons
import kotlin.math.roundToInt

private enum class CatterDestination(val label: String, val icon: ImageVector) {
    Home("Home", TablerIcons.Home),
    Settings("Settings", TablerIcons.Settings),
}

@Composable
fun CatterApp() {
    var current by rememberSaveable { mutableStateOf(CatterDestination.Home) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
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
                CatterDestination.Settings -> SettingsScreen()
            }
        }

        FloatingNavigationBar(
            selected = current,
            onSelect = { current = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        )
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
    val indicator = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var indicatorReady by remember { mutableStateOf(false) }
    val targetBounds = itemBounds[selected]

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
        Box {
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
                modifier = Modifier.padding(6.dp),
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
            .onGloballyPositioned { onBoundsChange(it.boundsInParent()) }
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.label,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}
