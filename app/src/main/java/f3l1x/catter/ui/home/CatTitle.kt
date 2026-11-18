package f3l1x.catter.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.abs
import kotlin.math.pow

private const val PERIOD_MS = 1800
private const val START_OFFSET_MS = -133

private const val EASE_IN = 1
private const val EASE_OUT = 2
private const val EASE_IN_OUT = 3

private val CAT_Y_STOPS = floatArrayOf(
    0f, 29.63f, 38.889f, 42.593f, 48.148f, 51.852f, 55.556f, 57.407f, 59.259f, 61.111f,
    62.963f, 64.815f, 66.667f, 68.519f, 70.37f, 72.222f, 74.074f, 75.926f, 77.778f, 79.63f,
    81.481f, 83.333f, 85.185f, 87.037f, 88.889f, 90.741f, 92.593f, 94.444f, 96.296f, 98.148f, 100f,
)
private val CAT_Y_VALUES = floatArrayOf(
    -151f, 31f, 41f, 41f, 31f, 27f, 27f, -1f, -23f, -38f,
    -51f, -62f, -72f, -80f, -88f, -96f, -102f, -109f, -114f, -120f,
    -125f, -129f, -133f, -137f, -140f, -143f, -145f, -147f, -149f, -150f, -151f,
)
private val CAT_Y_EASES = intArrayOf(
    EASE_IN, EASE_OUT, 0, EASE_IN_OUT, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
)

private val SQUASH_STOPS = floatArrayOf(
    0f, 22.222f, 27.778f, 29.63f, 38.889f, 42.593f, 48.148f, 51.852f, 55.556f, 57.407f,
    62.963f, 72.222f, 100f,
)
private val SQUASH_X = floatArrayOf(1f, 1f, 0.96f, 1.02f, 1.15f, 1.14f, 1.05f, 1f, 1.03f, 0.96f, 0.96f, 1f, 1f)
private val SQUASH_Y = floatArrayOf(1f, 1f, 1.05f, 0.97f, 0.9f, 0.9f, 0.97f, 1f, 0.97f, 1.04f, 1.05f, 1f, 1f)
private val SQUASH_EASES = intArrayOf(0, 0, 0, EASE_OUT, 0, EASE_IN_OUT, 0, 0, 0, 0, EASE_IN_OUT, 0, 0)

private val PAD_STOPS = floatArrayOf(
    0f, 25.926f, 27.778f, 29.63f, 31.481f, 33.333f, 35.185f, 37.037f, 38.889f, 40.741f,
    42.593f, 44.444f, 46.296f, 48.148f, 50f, 51.852f, 53.704f, 55.556f, 57.407f, 59.259f,
    61.111f, 62.963f, 64.815f, 66.667f, 68.519f, 70.37f, 72.222f, 74.074f, 75.926f, 77.778f,
    79.63f, 81.481f, 83.333f, 85.185f, 87.037f, 88.889f, 100f,
)
private val PAD_WIDTH = floatArrayOf(
    279f, 279f, 287f, 295f, 307f, 319f, 331f, 342f, 349f, 355f,
    357f, 355f, 349f, 337f, 319f, 307f, 271f, 246f, 227f, 211f,
    203f, 199f, 203f, 215f, 236f, 263f, 286f, 303f, 310f, 309f,
    307f, 301f, 293f, 287f, 281f, 279f, 279f,
)
private val PAD_HEIGHT = floatArrayOf(
    13f, 13f, 18f, 32f, 35f, 38f, 41f, 46f, 51f, 51f,
    51f, 51f, 51f, 49f, 44f, 42f, 32f, 26f, 20f, 17f,
    14f, 13f, 13f, 13f, 13f, 13f, 13f, 13f, 13f, 13f,
    13f, 13f, 13f, 13f, 13f, 13f, 13f,
)
private val PAD_OPACITY_STOPS = floatArrayOf(0f, 25.926f, 27.778f, 29.63f, 38.889f, 51.852f, 53.704f, 61.111f, 72.222f, 100f)
private val PAD_OPACITY = floatArrayOf(0.73f, 0.73f, 0.66f, 0.5f, 0.47f, 0.5f, 0.56f, 0.62f, 0.73f, 0.73f)

private val PUFF_BIG_STOPS = floatArrayOf(
    0f, 58.333f, 59.259f, 61.111f, 62.963f, 64.815f, 66.667f, 68.519f, 70.37f, 72.222f,
    74.074f, 77.778f, 79.63f, 81.481f, 83.333f, 85.185f, 87.037f, 88.889f, 100f,
)
private val PUFF_BIG_X = floatArrayOf(
    176f, 176f, 177f, 170f, 155f, 105f, 90f, 82f, 78f, 75f,
    72f, 68f, 66f, 64f, 63f, 63f, 63f, 63f, 63f,
)
private val PUFF_BIG_Y = floatArrayOf(
    226f, 226f, 228f, 218f, 208f, 166f, 152f, 147f, 142f, 140f,
    137f, 134f, 132f, 130f, 128f, 128f, 128f, 128f, 128f,
)
private val PUFF_BIG_SCALE = floatArrayOf(
    0f, 0f, 0.6f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
    1f, 1f, 1f, 0.77f, 0.55f, 0.23f, 0.11f, 0f, 0f,
)

private val PUFF_SMALL_STOPS = floatArrayOf(
    0f, 58.333f, 59.259f, 64.815f, 66.667f, 68.519f, 70.37f, 72.222f, 74.074f, 75.926f,
    77.778f, 79.63f, 81.481f, 83.333f, 85.185f, 87.037f, 88.889f, 92.593f, 100f,
)
private val PUFF_SMALL_X = floatArrayOf(
    188f, 188f, 188f, 178f, 160f, 141f, 136f, 132f, 130f, 128f,
    127f, 125f, 125f, 124f, 123f, 123f, 123f, 123f, 123f,
)
private val PUFF_SMALL_Y = floatArrayOf(
    240f, 240f, 240f, 233f, 227f, 218f, 216f, 214f, 213f, 212f,
    211f, 211f, 211f, 210f, 210f, 209f, 209f, 209f, 209f,
)
private val PUFF_SMALL_SCALE = floatArrayOf(
    0f, 0f, 0.7f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
    1f, 1f, 0.95f, 0.85f, 0.7f, 0.45f, 0.2f, 0f, 0f,
)

private const val PUFF_MIRROR = 396f

@Composable
fun CatTitleGlyph(style: TextStyle, color: Color) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val glyphWidth = remember(style) { measurer.measure("e", style).size.width.toFloat() }
    val slotWidth = glyphWidth * 2.55f

    val transition = rememberInfiniteTransition(label = "cat_bounce")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(PERIOD_MS, easing = LinearEasing),
            initialStartOffset = StartOffset(START_OFFSET_MS),
        ),
        label = "cat_bounce_t",
    )

    val eye = MaterialTheme.colorScheme.background
    val pad = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val puff = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)

    Box(
        modifier = Modifier
            .width(with(density) { slotWidth.toDp() })
            .height(with(density) { (slotWidth * 298f / 400f).toDp() }),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
        ) {
            val u = size.width / 400f
            drawPad(t, u, pad)
            drawPuff(t, u, puff, big = true, mirror = false)
            drawPuff(t, u, puff, big = true, mirror = true)
            drawPuff(t, u, puff, big = false, mirror = false)
            drawPuff(t, u, puff, big = false, mirror = true)
            drawCat(t, u, color, eye)
        }
    }
}

private fun DrawScope.drawPad(t: Float, u: Float, color: Color) {
    val width = sample(PAD_STOPS, PAD_WIDTH, null, t) * u
    val height = sample(PAD_STOPS, PAD_HEIGHT, null, t) * u
    val opacity = sample(PAD_OPACITY_STOPS, PAD_OPACITY, null, t)
    val left = 198f * u - width / 2f
    val top = 243f * u
    val radius = 7f * u
    val path = Path().apply {
        moveTo(left + radius, top)
        lineTo(left + width - radius, top)
        quadraticTo(left + width, top, left + width, top + radius)
        cubicTo(left + width, top + height, left, top + height, left, top + radius)
        quadraticTo(left, top, left + radius, top)
        close()
    }
    drawPath(path, color.copy(alpha = color.alpha * opacity))
}

private fun DrawScope.drawPuff(t: Float, u: Float, color: Color, big: Boolean, mirror: Boolean) {
    val stops = if (big) PUFF_BIG_STOPS else PUFF_SMALL_STOPS
    val xs = if (big) PUFF_BIG_X else PUFF_SMALL_X
    val ys = if (big) PUFF_BIG_Y else PUFF_SMALL_Y
    val scales = if (big) PUFF_BIG_SCALE else PUFF_SMALL_SCALE
    val scale = sample(stops, scales, null, t)
    if (scale <= 0.01f) return
    val x = sample(stops, xs, null, t).let { if (mirror) PUFF_MIRROR - it else it } * u
    val y = sample(stops, ys, null, t) * u
    drawCircle(color, radius = 22f * u * scale, center = Offset(x, y))
}

private fun DrawScope.drawCat(t: Float, u: Float, body: Color, eye: Color) {
    val y = sample(CAT_Y_STOPS, CAT_Y_VALUES, CAT_Y_EASES, t) * u
    val sx = sample(SQUASH_STOPS, SQUASH_X, SQUASH_EASES, t)
    val sy = sample(SQUASH_STOPS, SQUASH_Y, SQUASH_EASES, t)

    withTransform({
        translate(124f * u, 141f * u + y)
        scale(sx, sy, pivot = Offset(64f * u, 102f * u))
    }) {
        val ears = Path().apply {
            moveTo(23f * u, 38f * u)
            lineTo(29f * u, 4f * u)
            lineTo(52f * u, 22f * u)
            close()
            moveTo(76f * u, 22f * u)
            lineTo(99f * u, 4f * u)
            lineTo(105f * u, 38f * u)
            close()
        }
        drawPath(ears, eye, style = Stroke(6f * u, join = StrokeJoin.Round))
        val tail = Path().apply {
            moveTo(106f * u, 96f * u)
            cubicTo(121f * u, 101f * u, 134f * u, 100f * u, 143f * u, 87f * u)
        }
        drawPath(tail, body, style = Stroke(7f * u, cap = StrokeCap.Round))
        val mochi = Path().apply {
            moveTo(64f * u, 18f * u)
            cubicTo(101f * u, 18f * u, 124f * u, 44f * u, 124f * u, 71f * u)
            cubicTo(124f * u, 93f * u, 99f * u, 102f * u, 64f * u, 102f * u)
            cubicTo(29f * u, 102f * u, 4f * u, 93f * u, 4f * u, 71f * u)
            cubicTo(4f * u, 44f * u, 27f * u, 18f * u, 64f * u, 18f * u)
            close()
        }
        drawPath(mochi, body)
        val eyeRadius = 5.5f * u
        drawRoundRect(eye, topLeft = Offset(39.5f * u, 38f * u), size = Size(11f * u, 20f * u), cornerRadius = CornerRadius(eyeRadius, eyeRadius))
        drawRoundRect(eye, topLeft = Offset(70.5f * u, 38f * u), size = Size(11f * u, 20f * u), cornerRadius = CornerRadius(eyeRadius, eyeRadius))
    }
}

private fun sample(stops: FloatArray, values: FloatArray, eases: IntArray?, t: Float): Float {
    val progress = (t * 100f).coerceIn(0f, 100f)
    if (progress <= stops.first()) return values.first()
    if (progress >= stops.last()) return values.last()
    var i = 0
    while (i < stops.size - 2 && progress > stops[i + 1]) i++
    val span = stops[i + 1] - stops[i]
    var local = if (span <= 0f) 1f else (progress - stops[i]) / span
    if (eases != null) {
        local = when (eases[i]) {
            EASE_IN -> bezier(0.32f, 0f, 0.67f, 0f, local)
            EASE_OUT -> bezier(0.5f, 1f, 0.89f, 1f, local)
            EASE_IN_OUT -> bezier(0.37f, 0f, 0.63f, 1f, local)
            else -> local
        }
    }
    return values[i] + (values[i + 1] - values[i]) * local
}

private fun bezier(x1: Float, y1: Float, x2: Float, y2: Float, x: Float): Float {
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f
    var guess = x
    repeat(8) {
        val px = 3f * guess * (1f - guess).pow(2) * x1 +
            3f * guess.pow(2) * (1f - guess) * x2 +
            guess.pow(3)
        val dx = 3f * (1f - guess).pow(2) * x1 +
            6f * guess * (1f - guess) * (x2 - x1) +
            3f * guess.pow(2) * (1f - x2)
        if (abs(dx) < 1e-6f) return@repeat
        guess -= (px - x) / dx
        guess = guess.coerceIn(0f, 1f)
    }
    return 3f * guess * (1f - guess).pow(2) * y1 +
        3f * guess.pow(2) * (1f - guess) * y2 +
        guess.pow(3)
}
