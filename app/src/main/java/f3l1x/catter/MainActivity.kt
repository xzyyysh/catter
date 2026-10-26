package f3l1x.catter

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import f3l1x.catter.data.AppPreferences
import f3l1x.catter.data.DownloadStore
import f3l1x.catter.data.LocalAppPreferences
import f3l1x.catter.data.ThemeMode
import f3l1x.catter.service.Links
import f3l1x.catter.ui.CatterApp
import f3l1x.catter.ui.theme.CatterTheme
import f3l1x.catter.ui.theme.Themes
import f3l1x.catter.util.UpdateChecker
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val incomingLink = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = AppPreferences(applicationContext)
        DownloadStore.init(applicationContext)
        UpdateChecker.cleanup(applicationContext)
        UpdateChecker.start(applicationContext, preferences.autoUpdateCheck)
        if (savedInstanceState == null) {
            incomingLink.value = sharedText(intent)?.let(Links::fromText)
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            val variant = Themes.byId(preferences.themePreset)
            val darkTheme = when {
                variant != null -> variant.isDark
                preferences.amoled -> true
                preferences.themeMode == ThemeMode.System -> isSystemInDarkTheme()
                preferences.themeMode == ThemeMode.Dark -> true
                else -> false
            }
            val pending by incomingLink.collectAsState()

            CompositionLocalProvider(LocalAppPreferences provides preferences) {
                CatterTheme(
                    darkTheme = darkTheme,
                    dynamicColor = preferences.dynamicColor && !preferences.amoled,
                    amoled = preferences.amoled,
                    variant = variant,
                ) {
                    CatterApp(
                        incomingLink = pending,
                        onIncomingLinkConsumed = { incomingLink.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText(intent)?.let(Links::fromText)?.let { incomingLink.value = it }
    }

    private fun sharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
    }
}
