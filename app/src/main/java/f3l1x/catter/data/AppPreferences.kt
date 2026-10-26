package f3l1x.catter.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import f3l1x.catter.util.Logger

enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var themeMode: ThemeMode by mutableStateOf(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME_MODE, null) }
            ?: ThemeMode.System,
    )
        private set

    var dynamicColor: Boolean by mutableStateOf(prefs.getBoolean(KEY_DYNAMIC_COLOR, false))
        private set

    var amoled: Boolean by mutableStateOf(prefs.getBoolean(KEY_AMOLED, false))
        private set

    var saveTreeUri: String? by mutableStateOf(prefs.getString(KEY_SAVE_TREE, null))
        private set

    var saveTreeLabel: String? by mutableStateOf(prefs.getString(KEY_SAVE_LABEL, null))
        private set

    var permissionsRequested: Boolean by mutableStateOf(prefs.getBoolean(KEY_PERMISSIONS, false))
        private set

    var themePreset: String? by mutableStateOf(prefs.getString(KEY_THEME_PRESET, null))
        private set

    var debugLogs: Boolean by mutableStateOf(prefs.getBoolean(KEY_DEBUG_LOGS, false))
        private set

    var autoUpdateCheck: Boolean by mutableStateOf(prefs.getBoolean(KEY_AUTO_UPDATE, true))
        private set

    init {
        Logger.enabled = debugLogs
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun updateDynamicColor(enabled: Boolean) {
        dynamicColor = enabled
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    fun updateDebugLogs(enabled: Boolean) {
        debugLogs = enabled
        Logger.enabled = enabled
        prefs.edit().putBoolean(KEY_DEBUG_LOGS, enabled).apply()
    }

    fun updateAmoled(enabled: Boolean) {
        amoled = enabled
        prefs.edit().putBoolean(KEY_AMOLED, enabled).apply()
    }

    fun updateThemePreset(id: String?) {
        themePreset = id
        prefs.edit().putString(KEY_THEME_PRESET, id).apply()
    }

    fun updateAutoUpdateCheck(enabled: Boolean) {
        autoUpdateCheck = enabled
        prefs.edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
    }

    fun markPermissionsRequested() {
        permissionsRequested = true
        prefs.edit().putBoolean(KEY_PERMISSIONS, true).apply()
    }

    fun updateSaveTree(uri: String?, label: String?) {
        saveTreeUri = uri
        saveTreeLabel = label
        prefs.edit()
            .putString(KEY_SAVE_TREE, uri)
            .putString(KEY_SAVE_LABEL, label)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "catter_settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_AMOLED = "amoled"
        const val KEY_SAVE_TREE = "save_tree"
        const val KEY_SAVE_LABEL = "save_label"
        const val KEY_PERMISSIONS = "permissions_requested"
        const val KEY_THEME_PRESET = "theme_preset"
        const val KEY_DEBUG_LOGS = "debug_logs"
        const val KEY_AUTO_UPDATE = "auto_update_check"
    }
}

val LocalAppPreferences = staticCompositionLocalOf<AppPreferences> {
    error("AppPreferences was not provided")
}
