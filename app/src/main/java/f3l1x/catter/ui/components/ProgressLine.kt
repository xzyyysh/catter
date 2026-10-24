package f3l1x.catter.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

@Composable
fun ProgressLine(
    fraction: Float?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val animated by animateFloatAsState(
        targetValue = fraction ?: 0f,
        animationSpec = tween(220),
        label = "progress_fraction",
    )

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(trackColor),
    ) {
        if (fraction == null) {
            LoopingSegment(color = color)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.LoopingSegment(color: Color) {
    val density = LocalDensity.current
    val segment = maxWidth * 0.3f
    val travel = maxWidth + segment

    val transition = rememberInfiniteTransition(label = "progress_loop")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "progress_phase",
    )

    Box(
        modifier = Modifier
            .offset {
                val start = with(density) { (-segment).toPx() }
                val distance = with(density) { travel.toPx() }
                IntOffset((start + distance * phase).roundToInt(), 0)
            }
            .width(segment)
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}
