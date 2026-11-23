package cn.neeto.stockpot.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import cn.neeto.stockpot.core.settings.AppSettings

/**
 * 暖纸、墨色、一个红橙品牌色 —— 和桌面端同一套语气。
 * 支出用红、收入用绿，符合国内记账习惯。
 */
data class PotColors(
    val canvas: Color,
    val panel: Color,
    val panel2: Color,
    val line: Color,
    val lineStrong: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val brand: Color,
    val brandInk: Color,
    val brandTint: Color,
    val danger: Color,
    val success: Color,
    val expense: Color,
    val income: Color,
    val onBrand: Color = Color.White,
) {
    /** 图表配色：低饱和，和暖纸能待在一起。 */
    val chartPalette: List<Color>
        get() = listOf(
            Color(0xFFC25B3F),
            Color(0xFFD2A03F),
            Color(0xFF6E8B5B),
            Color(0xFF4E7A8A),
            Color(0xFF8A6B9E),
            Color(0xFFB5795E),
            Color(0xFF8A8C63),
            Color(0xFF9C7A4E),
        )
}

private val paperLight = PotColors(
    canvas = Color(0xFFF4F1EC),
    panel = Color(0xFFFFFDF9),
    panel2 = Color(0xFFF7F4EE),
    line = Color(0xFFE7E1D7),
    lineStrong = Color(0xFFD8D1C4),
    ink = Color(0xFF1C1A17),
    ink2 = Color(0xFF5F5A52),
    ink3 = Color(0xFF8B8478),
    brand = Color(0xFFE1543A),
    brandInk = Color(0xFFB83B25),
    brandTint = Color(0xFFFBEAE5),
    danger = Color(0xFFC0392B),
    success = Color(0xFF3F7A54),
    expense = Color(0xFFC0392B),
    income = Color(0xFF3F7A54),
)

private val paperDark = PotColors(
    canvas = Color(0xFF131211),
    panel = Color(0xFF1B1A17),
    panel2 = Color(0xFF232120),
    line = Color(0xFF2C2926),
    lineStrong = Color(0xFF3B3733),
    ink = Color(0xFFF3EFE8),
    ink2 = Color(0xFFB5AEA3),
    ink3 = Color(0xFF8E877C),
    brand = Color(0xFFF2694C),
    brandInk = Color(0xFFFF9078),
    brandTint = Color(0xFF2E1C17),
    danger = Color(0xFFEF7A68),
    success = Color(0xFF8FC2A4),
    expense = Color(0xFFEF7A68),
    income = Color(0xFF8FC2A4),
)

val LocalPotColors = staticCompositionLocalOf { paperLight }

val PotShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(11.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

/** 金额一律用等宽数字，位数变化不会让整行跳动。 */
val MoneyStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 20.sp,
    letterSpacing = (-0.2).sp,
    fontFeatureSettings = "tnum",
)

val PotTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.9).sp,
        fontFeatureSettings = "tnum",
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp,
    ),
)

@Composable
fun PotTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val target = if (dark) paperDark else paperLight
    val canvas by animateColorAsState(target.canvas, tween(240), label = "canvas")
    val panel by animateColorAsState(target.panel, tween(240), label = "panel")
    val panel2 by animateColorAsState(target.panel2, tween(240), label = "panel2")
    val line by animateColorAsState(target.line, tween(240), label = "line")
    val lineStrong by animateColorAsState(target.lineStrong, tween(240), label = "lineStrong")
    val ink by animateColorAsState(target.ink, tween(240), label = "ink")
    val ink2 by animateColorAsState(target.ink2, tween(240), label = "ink2")
    val ink3 by animateColorAsState(target.ink3, tween(240), label = "ink3")
    val brand by animateColorAsState(target.brand, tween(240), label = "brand")
    val brandInk by animateColorAsState(target.brandInk, tween(240), label = "brandInk")
    val brandTint by animateColorAsState(target.brandTint, tween(240), label = "brandTint")
    val colors = target.copy(
        canvas = canvas,
        panel = panel,
        panel2 = panel2,
        line = line,
        lineStrong = lineStrong,
        ink = ink,
        ink2 = ink2,
        ink3 = ink3,
        brand = brand,
        brandInk = brandInk,
        brandTint = brandTint,
    )

    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.brand,
            onPrimary = colors.onBrand,
            primaryContainer = colors.brandTint,
            onPrimaryContainer = colors.brandInk,
            secondary = colors.brandInk,
            onSecondary = colors.panel,
            secondaryContainer = colors.brandTint,
            onSecondaryContainer = colors.brandInk,
            background = colors.canvas,
            onBackground = colors.ink,
            surface = colors.panel,
            onSurface = colors.ink,
            surfaceVariant = colors.panel2,
            onSurfaceVariant = colors.ink2,
            surfaceTint = colors.brand,
            surfaceContainerLowest = colors.panel,
            surfaceContainerLow = colors.panel,
            surfaceContainer = colors.panel2,
            surfaceContainerHigh = colors.panel2,
            surfaceContainerHighest = colors.panel2,
            inverseSurface = colors.ink,
            inverseOnSurface = colors.panel,
            inversePrimary = colors.brandTint,
            outline = colors.lineStrong,
            outlineVariant = colors.line,
            error = colors.danger,
            onError = Color.White,
            scrim = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = colors.brand,
            onPrimary = colors.onBrand,
            primaryContainer = colors.brandTint,
            onPrimaryContainer = colors.brandInk,
            secondary = colors.brandInk,
            onSecondary = colors.panel,
            secondaryContainer = colors.brandTint,
            onSecondaryContainer = colors.brandInk,
            background = colors.canvas,
            onBackground = colors.ink,
            surface = colors.panel,
            onSurface = colors.ink,
            surfaceVariant = colors.panel2,
            onSurfaceVariant = colors.ink2,
            surfaceTint = colors.brand,
            surfaceContainerLowest = colors.panel,
            surfaceContainerLow = colors.panel,
            surfaceContainer = colors.panel2,
            surfaceContainerHigh = colors.panel2,
            surfaceContainerHighest = colors.panel2,
            inverseSurface = colors.ink,
            inverseOnSurface = colors.panel,
            inversePrimary = colors.brandTint,
            outline = colors.lineStrong,
            outlineVariant = colors.line,
            error = colors.danger,
            onError = Color.White,
            scrim = Color.Black,
        )
    }

    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    SideEffect {
        val window = activity?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalPotColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = PotTypography,
            shapes = PotShapes,
            content = content,
        )
    }
}

val potColors: PotColors
    @Composable
    get() = LocalPotColors.current

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
