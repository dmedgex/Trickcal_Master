package com.trickcal.crayon.master.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 与 demo/style.css 的 design token 一一对应。 */
@Immutable
data class TcColors(
    val paper: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val brand: Color,
    val brandSoft: Color,
    val gold: Color,
    val ok: Color,
    val warn: Color,
    val isDark: Boolean,
)

/**
 * 皮肤主题：每套都提供浅色 + 深色两份完整配色。
 * gold 始终保留暖金（金蜡笔语义），不随皮肤偏移。
 */
enum class AppSkin(val label: String) {
    CRAYON("暖橙"),
    MINT("薄荷绿"),
    OCEAN("海盐蓝"),
    TWILIGHT("暮光紫");

    fun colors(dark: Boolean): TcColors = when (this) {
        CRAYON -> if (dark) CrayonDark else CrayonLight
        MINT -> if (dark) MintDark else MintLight
        OCEAN -> if (dark) OceanDark else OceanLight
        TWILIGHT -> if (dark) TwilightDark else TwilightLight
    }

    /** 用于设置页色块预览。 */
    val swatch: Color
        get() = when (this) {
            CRAYON -> Color(0xFFE8845F)
            MINT -> Color(0xFF2E9E76)
            OCEAN -> Color(0xFF3B78B8)
            TWILIGHT -> Color(0xFF7A4FC0)
        }

    companion object {
        fun fromKey(key: String?): AppSkin = entries.firstOrNull { it.name == key } ?: CRAYON
    }
}

// ---------------- 暖橙（原始） ----------------

val CrayonLight = TcColors(
    paper = Color(0xFFFBF7F2),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF4EFE8),
    ink = Color(0xFF2A2320),
    ink2 = Color(0xFF6B5F58),
    ink3 = Color(0xFFA0938B),
    line = Color(0xFFEDE4DA),
    brand = Color(0xFFE8845F),
    brandSoft = Color(0xFFFBEAE1),
    gold = Color(0xFFE0B15C),
    ok = Color(0xFF5FA87F),
    warn = Color(0xFFD98251),
    isDark = false,
)

val CrayonDark = TcColors(
    paper = Color(0xFF17130F),
    surface = Color(0xFF221B16),
    surface2 = Color(0xFF2C241D),
    ink = Color(0xFFF3EAE1),
    ink2 = Color(0xFFB8A79B),
    ink3 = Color(0xFF7E6F64),
    line = Color(0xFF342A23),
    brand = Color(0xFFF0976F),
    brandSoft = Color(0xFF3A2A20),
    gold = Color(0xFFE8BE72),
    ok = Color(0xFF6FBE92),
    warn = Color(0xFFE39464),
    isDark = true,
)

// ---------------- 薄荷绿 ----------------

val MintLight = TcColors(
    paper = Color(0xFFEDF4EF),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFE3EDE6),
    ink = Color(0xFF1C2A23),
    ink2 = Color(0xFF566B5F),
    ink3 = Color(0xFF8FA398),
    line = Color(0xFFDCE8E0),
    brand = Color(0xFF2E9E76),
    brandSoft = Color(0xFFDDF0E7),
    gold = Color(0xFFE0B15C),
    ok = Color(0xFF3E9C6E),
    warn = Color(0xFFD07A3E),
    isDark = false,
)

val MintDark = TcColors(
    paper = Color(0xFF111814),
    surface = Color(0xFF18211B),
    surface2 = Color(0xFF202C24),
    ink = Color(0xFFE6F1EA),
    ink2 = Color(0xFFA9BFB1),
    ink3 = Color(0xFF76907F),
    line = Color(0xFF2A362D),
    brand = Color(0xFF55C99A),
    brandSoft = Color(0xFF1E3A2C),
    gold = Color(0xFFE8BE72),
    ok = Color(0xFF5FC78F),
    warn = Color(0xFFD98B54),
    isDark = true,
)

// ---------------- 海盐蓝 ----------------

val OceanLight = TcColors(
    paper = Color(0xFFEDF1F6),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFE3EAF2),
    ink = Color(0xFF1B2430),
    ink2 = Color(0xFF56657A),
    ink3 = Color(0xFF8C9AAB),
    line = Color(0xFFDBE3ED),
    brand = Color(0xFF3B78B8),
    brandSoft = Color(0xFFDEEAF7),
    gold = Color(0xFFE0B15C),
    ok = Color(0xFF3E9C7E),
    warn = Color(0xFFC9704A),
    isDark = false,
)

val OceanDark = TcColors(
    paper = Color(0xFF0F1419),
    surface = Color(0xFF161D25),
    surface2 = Color(0xFF1E2732),
    ink = Color(0xFFE6EDF5),
    ink2 = Color(0xFFA6B4C4),
    ink3 = Color(0xFF738396),
    line = Color(0xFF27313D),
    brand = Color(0xFF61A6E0),
    brandSoft = Color(0xFF1A2C3E),
    gold = Color(0xFFE8BE72),
    ok = Color(0xFF5EBE94),
    warn = Color(0xFFD98B54),
    isDark = true,
)

// ---------------- 暮光紫 ----------------

val TwilightLight = TcColors(
    paper = Color(0xFFF2EEF7),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFE9E3F1),
    ink = Color(0xFF241E2E),
    ink2 = Color(0xFF615674),
    ink3 = Color(0xFF978CA8),
    line = Color(0xFFE3DAEE),
    brand = Color(0xFF7A4FC0),
    brandSoft = Color(0xFFEDE3FA),
    gold = Color(0xFFE0B15C),
    ok = Color(0xFF3E9C7E),
    warn = Color(0xFFC9704A),
    isDark = false,
)

val TwilightDark = TcColors(
    paper = Color(0xFF141019),
    surface = Color(0xFF1C1724),
    surface2 = Color(0xFF241E2E),
    ink = Color(0xFFEDE7F5),
    ink2 = Color(0xFFB4A9C6),
    ink3 = Color(0xFF7F7492),
    line = Color(0xFF2C2537),
    brand = Color(0xFFA583E8),
    brandSoft = Color(0xFF2A1F3D),
    gold = Color(0xFFE8BE72),
    ok = Color(0xFF5EBE94),
    warn = Color(0xFFD98B54),
    isDark = true,
)

/** 兼容旧引用。 */
val LightTcColors = CrayonLight
val DarkTcColors = CrayonDark

val LocalTcColors = staticCompositionLocalOf { CrayonLight }

object Tc {
    val colors: TcColors
        @Composable get() = LocalTcColors.current
}

@Composable
fun TrickcalTheme(
    skin: AppSkin = AppSkin.CRAYON,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = skin.colors(darkTheme)
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.brand,
            onPrimary = Color.White,
            secondary = colors.gold,
            background = colors.paper,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.ink2,
            outline = colors.line,
        )
    } else {
        lightColorScheme(
            primary = colors.brand,
            onPrimary = Color.White,
            secondary = colors.gold,
            background = colors.paper,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.ink2,
            outline = colors.line,
        )
    }
    CompositionLocalProvider(LocalTcColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
