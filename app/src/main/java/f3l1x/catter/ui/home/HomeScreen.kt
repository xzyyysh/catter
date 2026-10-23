package f3l1x.catter.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlitchTitle()
        Spacer(Modifier.height(14.dp))
        Text(
            text = "A simple native media downloader for a change that uses direct API services.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
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
