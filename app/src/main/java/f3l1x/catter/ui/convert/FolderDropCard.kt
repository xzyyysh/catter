package f3l1x.catter.ui.convert

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import f3l1x.catter.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SUCCESS = Color(0xFF27A05C)
private const val FOLDER_UNITS = 120f

@Composable
fun FolderDropCard(
    files: Int,
    ready: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    val flap = remember { Animatable(1f) }
    val paper = remember { Animatable(27f) }
    val trace = remember { Animatable(0f) }
    val badge = remember { Animatable(0f) }

    LaunchedEffect(ready, files) {
        if (!ready) {
            flap.snapTo(1f)
            paper.snapTo(27f)
            trace.snapTo(0f)
            badge.snapTo(0f)
            return@LaunchedEffect
        }

        flap.animateTo(0.93f, spring(dampingRatio = 0.9f, stiffness = 380f))
        paper.animateTo(2f, spring(dampingRatio = 0.9f, stiffness = 380f))
        delay(260)
        launch { paper.animateTo(38f, tween(900, easing = LinearEasing)) }
        trace.animateTo(1f, tween(900, easing = LinearEasing))
        flap.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 380f))
        badge.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f))
        trace.animateTo(0f, tween(250))
        delay(900)
        onDone()
    }

    val scheme = MaterialTheme.colorScheme
    val back = scheme.primary
    val lip = lerp(scheme.primary, Color.Black, 0.18f)
    val frontTop = lerp(scheme.primary, Color.White, 0.22f)
    val frontBottom = lerp(scheme.primary, Color.White, 0.08f)
    val traceColor = scheme.primary
    val borderColor = scheme.outlineVariant

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.5f)
            .clickable(onClick = onClick),
    ) {
        Box {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawBorder(trace.value, ready, traceColor, borderColor)
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth(0.38f)
                        .aspectRatio(1.2f),
                ) {
                    drawFolder(flap.value, paper.value, badge.value, back, lip, frontTop, frontBottom)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (ready) {
                        pluralStringResource(R.plurals.converter_ready, files, files)
                    } else {
                        stringResource(R.string.converter_card_title)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (ready) {
                        stringResource(R.string.converter_choose_format)
                    } else {
                        stringResource(R.string.converter_card_subtitle)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun DrawScope.drawBorder(trace: Float, ready: Boolean, traceColor: Color, borderColor: Color) {
    val radius = 22.dp.toPx()
    val stroke = 1.5.dp.toPx()
    val border = Path().apply {
        addRoundRect(RoundRect(rect = Rect(0f, 0f, size.width, size.height), cornerRadius = CornerRadius(radius)))
    }

    if (trace > 0f) {
        val measure = PathMeasure().apply { setPath(border, false) }
        val segment = Path()
        measure.getSegment(0f, measure.length * trace, segment, true)
        drawPath(segment, traceColor, style = Stroke(stroke))
    }

    drawPath(
        border,
        borderColor,
        style = Stroke(
            width = stroke,
            pathEffect = if (ready) {
                null
            } else {
                PathEffect.dashPathEffect(floatArrayOf(12f, 9f))
            },
        ),
    )
}

private fun DrawScope.drawFolder(
    flap: Float,
    paper: Float,
    badge: Float,
    back: Color,
    lip: Color,
    frontTop: Color,
    frontBottom: Color,
) {
    val scale = size.width / FOLDER_UNITS
    withTransform({
        translate((size.width - FOLDER_UNITS * scale) / 2f, (size.height - 100f * scale) / 2f)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        drawOval(
            Color.Black.copy(alpha = 0.07f),
            topLeft = Offset(20f, 86f),
            size = Size(80f, 6f),
        )

        val panel = Path().apply {
            moveTo(14f, 76f)
            lineTo(14f, 20f)
            quadraticBezierTo(14f, 15f, 19f, 15f)
            lineTo(40f, 15f)
            quadraticBezierTo(44f, 15.5f, 47f, 20f)
            quadraticBezierTo(49f, 21.5f, 50.5f, 21.5f)
            lineTo(101f, 21.5f)
            quadraticBezierTo(106f, 21.5f, 106f, 26.5f)
            lineTo(106f, 76f)
            close()
        }
        drawPath(panel, back)

        val top = paper
        drawRoundRect(
            Color.White,
            topLeft = Offset(28f, top),
            size = Size(64f, 40f),
            cornerRadius = CornerRadius(3f, 3f),
        )
        drawRoundRect(
            Color(0xFFDFE5F2),
            topLeft = Offset(28f, top),
            size = Size(64f, 40f),
            cornerRadius = CornerRadius(3f, 3f),
            style = Stroke(0.6f),
        )
        drawRoundRect(back, topLeft = Offset(34f, top + 7f), size = Size(6f, 6f), cornerRadius = CornerRadius(1.5f))
        drawRoundRect(Color(0xFFC9D2E6), topLeft = Offset(45f, top + 7.5f), size = Size(26f, 1.8f), cornerRadius = CornerRadius(0.9f))
        drawRoundRect(Color(0xFFE0E5F0), topLeft = Offset(45f, top + 11.5f), size = Size(16f, 1.4f), cornerRadius = CornerRadius(0.7f))
        drawRoundRect(Color(0xFFE0E5F0), topLeft = Offset(34f, top + 21f), size = Size(52f, 1.6f), cornerRadius = CornerRadius(0.8f))
        drawRoundRect(Color(0xFFE0E5F0), topLeft = Offset(34f, top + 26f), size = Size(40f, 1.6f), cornerRadius = CornerRadius(0.8f))

        val open = ((1f - flap) / 0.07f).coerceIn(0f, 1f)
        if (open > 0f) {
            drawRoundRect(
                lip.copy(alpha = open),
                topLeft = Offset(10f, 74f),
                size = Size(100f, 9f),
                cornerRadius = CornerRadius(4f, 4f),
            )
        }

        drawRoundRect(
            Brush.verticalGradient(
                listOf(frontTop, frontBottom),
                startY = 32f,
                endY = 80f,
            ),
            topLeft = Offset(10f, 32f),
            size = Size(100f, 48f * flap),
            cornerRadius = CornerRadius(7f, 7f),
        )

        if (badge > 0.01f) {
            withTransform({ scale(badge, badge, pivot = Offset(60f, 57f)) }) {
                drawCircle(SUCCESS, radius = 10f, center = Offset(60f, 57f))
                drawCircle(Color.White, radius = 10f, center = Offset(60f, 57f), style = Stroke(2f))
                val progress = ((badge - 0.6f) / 0.4f).coerceIn(0f, 1f)
                if (progress > 0f) {
                    val check = Path().apply {
                        moveTo(55f, 57.5f)
                        lineTo(58.5f, 61f)
                        lineTo(65f, 53.5f)
                    }
                    val measure = PathMeasure().apply { setPath(check, false) }
                    val segment = Path()
                    measure.getSegment(0f, measure.length * progress, segment, true)
                    drawPath(segment, Color.White, style = Stroke(2.2f))
                }
            }
        }
    }
}
