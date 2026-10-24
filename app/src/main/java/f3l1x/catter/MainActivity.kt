package f3l1x.catter

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import f3l1x.catter.data.AppPreferences
import f3l1x.catter.data.DownloadStore
import f3l1x.catter.data.LocalAppPreferences
import f3l1x.catter.data.ThemeMode
import f3l1x.catter.ui.CatterApp
import f3l1x.catter.ui.theme.CatterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DownloadStore.init(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            val preferences = remember { AppPreferences(applicationContext) }
            val darkTheme = when {
                preferences.amoled -> true
                preferences.themeMode == ThemeMode.System -> isSystemInDarkTheme()
                preferences.themeMode == ThemeMode.Dark -> true
                else -> false
            }

            CompositionLocalProvider(LocalAppPreferences provides preferences) {
                CatterTheme(
                    darkTheme = darkTheme,
                    dynamicColor = preferences.dynamicColor && !preferences.amoled,
                    amoled = preferences.amoled,
                ) {
                    CatterApp()
                }
            }
        }
    }
}
