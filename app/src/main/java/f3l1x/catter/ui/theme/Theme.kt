package f3l1x.catter.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private const val THEME_ANIMATION_MS = 420

@Composable
fun CatterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    amoled: Boolean = false,
    variant: ThemeVariant? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val baseScheme = when {
        variant != null -> variant.scheme

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> ChocolateDarkColorScheme
        else -> ChocolateLightColorScheme
    }
    val colorScheme = (if (amoled && darkTheme) baseScheme.toAmoled() else baseScheme).animated()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CatterTypography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

@Composable
private fun ColorScheme.animated(): ColorScheme {
    val spec = tween<Color>(durationMillis = THEME_ANIMATION_MS, easing = FastOutSlowInEasing)

    return copy(
        primary = primary.animate(spec),
        onPrimary = onPrimary.animate(spec),
        primaryContainer = primaryContainer.animate(spec),
        onPrimaryContainer = onPrimaryContainer.animate(spec),
        inversePrimary = inversePrimary.animate(spec),
        secondary = secondary.animate(spec),
        onSecondary = onSecondary.animate(spec),
        secondaryContainer = secondaryContainer.animate(spec),
        onSecondaryContainer = onSecondaryContainer.animate(spec),
        tertiary = tertiary.animate(spec),
        onTertiary = onTertiary.animate(spec),
        tertiaryContainer = tertiaryContainer.animate(spec),
        onTertiaryContainer = onTertiaryContainer.animate(spec),
        background = background.animate(spec),
        onBackground = onBackground.animate(spec),
        surface = surface.animate(spec),
        onSurface = onSurface.animate(spec),
        surfaceVariant = surfaceVariant.animate(spec),
        onSurfaceVariant = onSurfaceVariant.animate(spec),
        surfaceDim = surfaceDim.animate(spec),
        surfaceBright = surfaceBright.animate(spec),
        surfaceContainerLowest = surfaceContainerLowest.animate(spec),
        surfaceContainerLow = surfaceContainerLow.animate(spec),
        surfaceContainer = surfaceContainer.animate(spec),
        surfaceContainerHigh = surfaceContainerHigh.animate(spec),
        surfaceContainerHighest = surfaceContainerHighest.animate(spec),
        inverseSurface = inverseSurface.animate(spec),
        inverseOnSurface = inverseOnSurface.animate(spec),
        error = error.animate(spec),
        onError = onError.animate(spec),
        errorContainer = errorContainer.animate(spec),
        onErrorContainer = onErrorContainer.animate(spec),
        outline = outline.animate(spec),
        outlineVariant = outlineVariant.animate(spec),
    )
}

@Composable
private fun Color.animate(spec: AnimationSpec<Color>): Color =
    animateColorAsState(
        targetValue = this,
        animationSpec = spec,
        label = "theme_color",
    ).value

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
