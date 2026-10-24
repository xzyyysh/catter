package f3l1x.catter.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

const val DEFAULT_THEME_ID = "default"

enum class ThemeFamily(val label: String) {
    Catter("Catter"),
    Catppuccin("Catppuccin"),
    Gruvbox("Gruvbox"),
    TokyoNight("Tokyo Night"),
    RosePine("Rosé Pine"),
    Nord("Nord"),
    Everforest("Everforest"),
}

data class ThemeVariant(
    val id: String,
    val family: ThemeFamily,
    val name: String,
    val isDark: Boolean,
    val scheme: ColorScheme,
)

object Themes {

    val default = ThemeVariant(
        id = DEFAULT_THEME_ID,
        family = ThemeFamily.Catter,
        name = "Default",
        isDark = true,
        scheme = ChocolateDarkColorScheme,
    )

    val all: List<ThemeVariant> = buildList {
        add(default)

        add(variant("catppuccin-latte", ThemeFamily.Catppuccin, "Latte", false, 0xFFEFF1F5, 0xFFE6E9EF, 0xFFDCE0E8, 0xFFCCD0DA, 0xFF4C4F69, 0xFF6C6F85, 0xFF9CA0B0, 0xFF8839EF))
        add(variant("catppuccin-frappe", ThemeFamily.Catppuccin, "Frappé", true, 0xFF303446, 0xFF292C3C, 0xFF414559, 0xFF51576D, 0xFFC6D0F5, 0xFFA5ADCE, 0xFF838BA7, 0xFFCA9EE6))
        add(variant("catppuccin-macchiato", ThemeFamily.Catppuccin, "Macchiato", true, 0xFF24273A, 0xFF1E2030, 0xFF363A4F, 0xFF494D64, 0xFFCAD3F5, 0xFFA5ADCB, 0xFF8087A2, 0xFFC6A0F6))
        add(variant("catppuccin-mocha", ThemeFamily.Catppuccin, "Mocha", true, 0xFF1E1E2E, 0xFF181825, 0xFF313244, 0xFF45475A, 0xFFCDD6F4, 0xFFA6ADC8, 0xFF7F849C, 0xFFCBA6F7))

        add(variant("gruvbox-light", ThemeFamily.Gruvbox, "Light", false, 0xFFFBF1C7, 0xFFF2E5BC, 0xFFEBDBB2, 0xFFD5C4A1, 0xFF3C3836, 0xFF665C54, 0xFFA89984, 0xFFAF3A03))
        add(variant("gruvbox-dark", ThemeFamily.Gruvbox, "Dark", true, 0xFF282828, 0xFF1D2021, 0xFF3C3836, 0xFF504945, 0xFFEBDBB2, 0xFFBDAE93, 0xFF928374, 0xFFFE8019))
        add(variant("gruvbox-material", ThemeFamily.Gruvbox, "Material", true, 0xFF282828, 0xFF1D2021, 0xFF32302F, 0xFF3C3836, 0xFFD4BE98, 0xFFA89984, 0xFF7C6F64, 0xFFE78A4E))
        add(variant("gruvbox-retro", ThemeFamily.Gruvbox, "Retro", false, 0xFFF2E5BC, 0xFFEBDBB2, 0xFFD5C4A1, 0xFFBDAE93, 0xFF3C3836, 0xFF665C54, 0xFF928374, 0xFFD65D0E))

        add(variant("tokyo-night", ThemeFamily.TokyoNight, "Tokyo Night", true, 0xFF1A1B26, 0xFF16161E, 0xFF1F2335, 0xFF292E42, 0xFFC0CAF5, 0xFFA9B1D6, 0xFF565F89, 0xFF7AA2F7))
        add(variant("tokyo-storm", ThemeFamily.TokyoNight, "Storm", true, 0xFF24283B, 0xFF1F2335, 0xFF292E42, 0xFF3B4261, 0xFFC0CAF5, 0xFFA9B1D6, 0xFF565F89, 0xFF7AA2F7))
        add(variant("tokyo-moon", ThemeFamily.TokyoNight, "Moon", true, 0xFF222436, 0xFF1E2030, 0xFF2F334D, 0xFF3B4261, 0xFFC8D3F5, 0xFF828BB8, 0xFF545C7E, 0xFF82AAFF))
        add(variant("tokyo-day", ThemeFamily.TokyoNight, "Day", false, 0xFFE1E2E7, 0xFFD0D5E3, 0xFFC4C8DA, 0xFFB4B8CA, 0xFF3760BF, 0xFF6172B0, 0xFF8990B3, 0xFF2E7DE9))

        add(variant("rose-pine", ThemeFamily.RosePine, "Rosé Pine", true, 0xFF191724, 0xFF1F1D2E, 0xFF26233A, 0xFF403D52, 0xFFE0DEF4, 0xFF908CAA, 0xFF6E6A86, 0xFFC4A7E7))
        add(variant("rose-pine-moon", ThemeFamily.RosePine, "Moon", true, 0xFF232136, 0xFF2A273F, 0xFF393552, 0xFF44415A, 0xFFE0DEF4, 0xFF908CAA, 0xFF6E6A86, 0xFFC4A7E7))
        add(variant("rose-pine-dawn", ThemeFamily.RosePine, "Dawn", false, 0xFFFAF4ED, 0xFFF2E9E1, 0xFFE9E0D9, 0xFFDFD6CF, 0xFF575279, 0xFF797593, 0xFF9893A5, 0xFF907AA9))

        add(variant("nord", ThemeFamily.Nord, "Nord", true, 0xFF2E3440, 0xFF3B4252, 0xFF434C5E, 0xFF4C566A, 0xFFECEFF4, 0xFFD8DEE9, 0xFF616E88, 0xFF88C0D0))
        add(variant("nord-light", ThemeFamily.Nord, "Nord Light", false, 0xFFECEFF4, 0xFFE5E9F0, 0xFFD8DEE9, 0xFFC9D0DD, 0xFF2E3440, 0xFF4C566A, 0xFF7B88A1, 0xFF5E81AC))
        add(variant("nord-dark", ThemeFamily.Nord, "Nord Dark", true, 0xFF242933, 0xFF2E3440, 0xFF3B4252, 0xFF434C5E, 0xFFECEFF4, 0xFFD8DEE9, 0xFF616E88, 0xFF88C0D0))

        add(variant("everforest-hard-dark", ThemeFamily.Everforest, "Hard Dark", true, 0xFF272E33, 0xFF2E383C, 0xFF374145, 0xFF414B50, 0xFFD3C6AA, 0xFF9DA9A0, 0xFF7A8478, 0xFFA7C080))
        add(variant("everforest-medium-dark", ThemeFamily.Everforest, "Medium Dark", true, 0xFF2D353B, 0xFF343F44, 0xFF3D484D, 0xFF475258, 0xFFD3C6AA, 0xFF9DA9A0, 0xFF859289, 0xFFA7C080))
        add(variant("everforest-soft-dark", ThemeFamily.Everforest, "Soft Dark", true, 0xFF333C43, 0xFF3A464C, 0xFF434F55, 0xFF4D5960, 0xFFD3C6AA, 0xFF9DA9A0, 0xFF859289, 0xFFA7C080))
        add(variant("everforest-hard-light", ThemeFamily.Everforest, "Hard Light", false, 0xFFFFFBEF, 0xFFF2EFDF, 0xFFEBE7D5, 0xFFE0DCC7, 0xFF5C6A72, 0xFF829181, 0xFFA6B0A0, 0xFF8DA101))
        add(variant("everforest-medium-light", ThemeFamily.Everforest, "Medium Light", false, 0xFFFDF6E3, 0xFFF4F0D9, 0xFFEDE8CF, 0xFFE2DDC5, 0xFF5C6A72, 0xFF829181, 0xFFA6B0A0, 0xFF8DA101))
        add(variant("everforest-soft-light", ThemeFamily.Everforest, "Soft Light", false, 0xFFF8F3E1, 0xFFF0EBD8, 0xFFE8E3D0, 0xFFDDD8C5, 0xFF5C6A72, 0xFF829181, 0xFFA6B0A0, 0xFF8DA101))
    }

    val families: List<ThemeFamily> = ThemeFamily.entries

    fun byId(id: String?): ThemeVariant? = all.firstOrNull { it.id == id }

    fun of(family: ThemeFamily): List<ThemeVariant> = all.filter { it.family == family }

    fun current(id: String?): ThemeVariant = byId(id) ?: default
}

private fun variant(
    id: String,
    family: ThemeFamily,
    name: String,
    dark: Boolean,
    background: Long,
    surface: Long,
    high: Long,
    highest: Long,
    text: Long,
    sub: Long,
    outline: Long,
    accent: Long,
): ThemeVariant = ThemeVariant(
    id = id,
    family = family,
    name = name,
    isDark = dark,
    scheme = buildScheme(dark, background, surface, high, highest, text, sub, outline, accent),
)

private fun buildScheme(
    dark: Boolean,
    background: Long,
    surface: Long,
    high: Long,
    highest: Long,
    text: Long,
    sub: Long,
    outline: Long,
    accent: Long,
): ColorScheme {
    val bg = Color(background)
    val surfaceColor = Color(surface)
    val highColor = Color(high)
    val highestColor = Color(highest)
    val textColor = Color(text)
    val subColor = Color(sub)
    val outlineColor = Color(outline)
    val accentColor = Color(accent)

    val onAccent = if (accentColor.luminance() > 0.5f) Color(0xFF101010) else Color(0xFFFFFFFF)
    val primaryContainer = lerp(highColor, accentColor, 0.34f)
    val secondaryContainer = lerp(highColor, accentColor, 0.24f)
    val outlineVariant = lerp(outlineColor, bg, 0.55f)

    val errorColor = if (dark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A)
    val onError = if (dark) Color(0xFF690005) else Color(0xFFFFFFFF)
    val errorContainer = if (dark) Color(0xFF93000A) else Color(0xFFFFDAD6)
    val onErrorContainer = if (dark) Color(0xFFFFDAD6) else Color(0xFF410002)

    return if (dark) {
        darkColorScheme(
            primary = accentColor,
            onPrimary = onAccent,
            primaryContainer = primaryContainer,
            onPrimaryContainer = textColor,
            inversePrimary = accentColor,
            secondary = accentColor,
            onSecondary = onAccent,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = textColor,
            tertiary = accentColor,
            onTertiary = onAccent,
            tertiaryContainer = secondaryContainer,
            onTertiaryContainer = textColor,
            error = errorColor,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            background = bg,
            onBackground = textColor,
            surface = surfaceColor,
            onSurface = textColor,
            surfaceVariant = highestColor,
            onSurfaceVariant = subColor,
            outline = outlineColor,
            outlineVariant = outlineVariant,
            scrim = Color.Black,
            inverseSurface = textColor,
            inverseOnSurface = bg,
            surfaceDim = bg,
            surfaceBright = highColor,
            surfaceContainerLowest = bg,
            surfaceContainerLow = surfaceColor,
            surfaceContainer = surfaceColor,
            surfaceContainerHigh = highColor,
            surfaceContainerHighest = highestColor,
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            onPrimary = onAccent,
            primaryContainer = primaryContainer,
            onPrimaryContainer = textColor,
            inversePrimary = accentColor,
            secondary = accentColor,
            onSecondary = onAccent,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = textColor,
            tertiary = accentColor,
            onTertiary = onAccent,
            tertiaryContainer = secondaryContainer,
            onTertiaryContainer = textColor,
            error = errorColor,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            background = bg,
            onBackground = textColor,
            surface = surfaceColor,
            onSurface = textColor,
            surfaceVariant = highestColor,
            onSurfaceVariant = subColor,
            outline = outlineColor,
            outlineVariant = outlineVariant,
            scrim = Color.Black,
            inverseSurface = textColor,
            inverseOnSurface = bg,
            surfaceDim = bg,
            surfaceBright = highColor,
            surfaceContainerLowest = bg,
            surfaceContainerLow = surfaceColor,
            surfaceContainer = surfaceColor,
            surfaceContainerHigh = highColor,
            surfaceContainerHighest = highestColor,
        )
    }
}
