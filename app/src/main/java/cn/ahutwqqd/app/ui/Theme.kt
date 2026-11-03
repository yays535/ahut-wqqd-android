package cn.ahutwqqd.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 明暗模式，与 AppSettings.themeMode 的字符串一一对应 */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("SYSTEM", "跟随系统"),
    LIGHT("LIGHT", "浅色"),
    DARK("DARK", "深色");

    companion object {
        fun of(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * 配色风格。
 *
 * 只换主色 / 容器色 / 强调色和背景底色，成功与失败的语义色保持固定，
 * 免得换了主题之后「绿点代表签到成功」这件事变得不可信。
 */
enum class ThemeStyle(val key: String, val label: String) {
    BLUE("BLUE", "藏蓝"),
    TEAL("TEAL", "青碧"),
    VIOLET("VIOLET", "暮紫"),
    SUNSET("SUNSET", "暖橙"),
    ROSE("ROSE", "玫瑰"),
    GRAPHITE("GRAPHITE", "石墨");

    companion object {
        fun of(key: String?): ThemeStyle = entries.firstOrNull { it.key == key } ?: BLUE
    }
}

private val LightBase = lightColorScheme(
    primary = Color(0xFF2F5BEA),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E8FF),
    onPrimaryContainer = Color(0xFF11265C),
    secondary = Color(0xFF0E9F6E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9F2E8),
    onSecondaryContainer = Color(0xFF06382A),
    tertiary = Color(0xFF7A5AF8),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F7FA),
    onBackground = Color(0xFF14161A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14161A),
    surfaceVariant = Color(0xFFEFF1F6),
    onSurfaceVariant = Color(0xFF5A6068),
    outline = Color(0xFFD8DCE5),
    outlineVariant = Color(0xFFE7EAF0),
    error = Color(0xFFD93B3B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE3E3),
    onErrorContainer = Color(0xFF5C1010),
    surfaceContainerLow = Color(0xFFFBFBFD),
    surfaceContainer = Color(0xFFFFFFFF)
)

private val DarkBase = darkColorScheme(
    primary = Color(0xFF9DB2FF),
    onPrimary = Color(0xFF0B1B4B),
    primaryContainer = Color(0xFF233C86),
    onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFF6FDAAC),
    onSecondary = Color(0xFF04301F),
    secondaryContainer = Color(0xFF0B4B34),
    onSecondaryContainer = Color(0xFFC8F2DE),
    tertiary = Color(0xFFC6B7FF),
    onTertiary = Color(0xFF2A1D5E),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE9EBF0),
    surface = Color(0xFF171A20),
    onSurface = Color(0xFFE9EBF0),
    surfaceVariant = Color(0xFF23272F),
    onSurfaceVariant = Color(0xFFA8AEB8),
    outline = Color(0xFF3A3F49),
    outlineVariant = Color(0xFF2A2F37),
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF4A0F0F),
    errorContainer = Color(0xFF5C1B1B),
    onErrorContainer = Color(0xFFFFD9D9),
    surfaceContainerLow = Color(0xFF141820),
    surfaceContainer = Color(0xFF171A20)
)

/** 每套主题在浅色下要覆盖的几个位置 */
private fun lightFor(style: ThemeStyle): ColorScheme {
    val s = when (style) {
        ThemeStyle.BLUE -> Style(
            primary = Color(0xFF2F5BEA), onPrimary = Color(0xFFFFFFFF),
            container = Color(0xFFE2E8FF), onContainer = Color(0xFF11265C),
            tertiary = Color(0xFF7A5AF8), background = Color(0xFFF6F7FA),
            surfaceVariant = Color(0xFFEFF1F6)
        )
        ThemeStyle.TEAL -> Style(
            primary = Color(0xFF0E8C7F), onPrimary = Color(0xFFFFFFFF),
            container = Color(0xFFD2F1EA), onContainer = Color(0xFF06352F),
            tertiary = Color(0xFF1E7FA8), background = Color(0xFFF3F8F7),
            surfaceVariant = Color(0xFFE8F2EF)
        )
        ThemeStyle.VIOLET -> Style(
            primary = Color(0xFF6D46E8), onPrimary = Color(0xFFFFFFFF),
            container = Color(0xFFE8E1FF), onContainer = Color(0xFF21104F),
            tertiary = Color(0xFFB54BC8), background = Color(0xFFF7F5FC),
            surfaceVariant = Color(0xFFF0ECFA)
        )
        ThemeStyle.SUNSET -> Style(
            primary = Color(0xFFCE5A18), onPrimary = Color(0xFFFFFFFF),
            container = Color(0xFFFFE6D5), onContainer = Color(0xFF4A1B00),
            tertiary = Color(0xFFC2455E), background = Color(0xFFFBF6F1),
            surfaceVariant = Color(0xFFF6EDE4)
        )
        ThemeStyle.ROSE -> Style(
            primary = Color(0xFFD6336C), onPrimary = Color(0xFFFFFFFF),
            container = Color(0xFFFFE0EA), onContainer = Color(0xFF4C0A22),
            tertiary = Color(0xFFA64BD8), background = Color(0xFFFCF5F7),
            surfaceVariant = Color(0xFFF8EAEF)
        )
        ThemeStyle.GRAPHITE -> Style(
            primary = Color(0xFF3C4250), onPrimary = Color(0xFFFFFFFF),
            container = Color(0xFFE3E5EA), onContainer = Color(0xFF1B1E24),
            tertiary = Color(0xFF5A6270), background = Color(0xFFF5F6F7),
            surfaceVariant = Color(0xFFEDEEF1)
        )
    }
    return LightBase.copy(
        primary = s.primary,
        onPrimary = s.onPrimary,
        primaryContainer = s.container,
        onPrimaryContainer = s.onContainer,
        tertiary = s.tertiary,
        onTertiary = Color(0xFFFFFFFF),
        background = s.background,
        surfaceVariant = s.surfaceVariant,
        surfaceContainerLow = s.background
    )
}

private fun darkFor(style: ThemeStyle): ColorScheme {
    val s = when (style) {
        ThemeStyle.BLUE -> Style(
            primary = Color(0xFF9DB2FF), onPrimary = Color(0xFF0B1B4B),
            container = Color(0xFF233C86), onContainer = Color(0xFFDDE3FF),
            tertiary = Color(0xFFC6B7FF), background = Color(0xFF0F1115),
            surfaceVariant = Color(0xFF23272F)
        )
        ThemeStyle.TEAL -> Style(
            primary = Color(0xFF6FD9C8), onPrimary = Color(0xFF00382F),
            container = Color(0xFF0B4B41), onContainer = Color(0xFFC9F3EA),
            tertiary = Color(0xFF7CCBE8), background = Color(0xFF0C1413),
            surfaceVariant = Color(0xFF1D2A27)
        )
        ThemeStyle.VIOLET -> Style(
            primary = Color(0xFFC3B2FF), onPrimary = Color(0xFF241356),
            container = Color(0xFF35246E), onContainer = Color(0xFFE6DEFF),
            tertiary = Color(0xFFE3A6F0), background = Color(0xFF100F16),
            surfaceVariant = Color(0xFF26232F)
        )
        ThemeStyle.SUNSET -> Style(
            primary = Color(0xFFFFB27A), onPrimary = Color(0xFF4A1B00),
            container = Color(0xFF5C2A0B), onContainer = Color(0xFFFFE0CB),
            tertiary = Color(0xFFFF9AAE), background = Color(0xFF151110),
            surfaceVariant = Color(0xFF2C2420)
        )
        ThemeStyle.ROSE -> Style(
            primary = Color(0xFFFF9CBE), onPrimary = Color(0xFF4C0A22),
            container = Color(0xFF5E1631), onContainer = Color(0xFFFFDCE7),
            tertiary = Color(0xFFD9A6F0), background = Color(0xFF150F12),
            surfaceVariant = Color(0xFF2B2126)
        )
        ThemeStyle.GRAPHITE -> Style(
            primary = Color(0xFFBFC6D4), onPrimary = Color(0xFF1B1E24),
            container = Color(0xFF333A45), onContainer = Color(0xFFE3E5EA),
            tertiary = Color(0xFF9AA3B2), background = Color(0xFF101113),
            surfaceVariant = Color(0xFF24272C)
        )
    }
    return DarkBase.copy(
        primary = s.primary,
        onPrimary = s.onPrimary,
        primaryContainer = s.container,
        onPrimaryContainer = s.onContainer,
        tertiary = s.tertiary,
        onTertiary = Color(0xFF2A1D5E),
        background = s.background,
        surfaceVariant = s.surfaceVariant,
        surfaceContainerLow = s.background
    )
}

private data class Style(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color,
    val tertiary: Color,
    val background: Color,
    val surfaceVariant: Color
)

/** 设置页色卡用的预览色：浅色模式下的主色 */
fun ThemeStyle.previewColor(): Color = lightFor(this).primary

private val AhutTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
)

@Composable
fun AhutSignTheme(
    themeMode: String = ThemeMode.SYSTEM.key,
    themeStyle: String = ThemeStyle.BLUE.key,
    content: @Composable () -> Unit
) {
    val style = ThemeStyle.of(themeStyle)
    val dark = when (ThemeMode.of(themeMode)) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) darkFor(style) else lightFor(style),
        typography = AhutTypography,
        content = content
    )
}
