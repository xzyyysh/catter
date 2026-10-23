package f3l1x.catter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

@Composable
fun CatterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChocolateDarkColorScheme,
        typography = CatterTypography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
