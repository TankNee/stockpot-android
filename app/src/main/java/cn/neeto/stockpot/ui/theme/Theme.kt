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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import cn.neeto.stockpot.R
import cn.neeto.stockpot.core.settings.AppSettings

/**
 * 一套配色 = 纸面（canvas / panel / line）+ 一个品牌色 + 一组图表色。
 * 深浅是叠在这之上的一层，所以每套纸都要准备 light / dark 两份。
 *
 * 支出用红、收入用绿，符合国内记账习惯；两套纸都没有改这个约定，
 * 只是把红的音量调低了一档。
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
    /** 图表 / 分类色带用的那一组：同一套纸里挑出来的，放在一起不会打架。 */
    val chartPalette: List<Color>,
    val onBrand: Color = Color.White,
)

/**
 * 主题纸样。id 存在设置里，改 id 等于让所有老用户的偏好失效，别动。
 */
enum class PotPalette(val id: String, val label: String, val hint: String) {
    Macaron(
        id = "macaron",
        label = "马卡龙",
        hint = "奶油白打底，六个糖色负责点缀",
    ),
    Paper(
        id = "paper",
        label = "暖纸",
        hint = "米白配朱砂，和桌面端同一套",
    ),
    ;

    companion object {
        fun of(id: String?): PotPalette = entries.firstOrNull { it.id == id } ?: Macaron
    }
}

/**
 * 马卡龙：高亮度、低饱和 —— 在正色里兑一勺牛奶。
 * 底色用燕麦奶油白留出呼吸，六个糖色负责图表和标签，品牌色是蜜桃玫瑰。
 */
private val macaronLight = PotColors(
    canvas = Color(0xFFFAF4EC),
    panel = Color(0xFFFFFDFB),
    panel2 = Color(0xFFF3EAE0),
    line = Color(0xFFEDE3D8),
    lineStrong = Color(0xFFDED2C4),
    ink = Color(0xFF3F3038),
    ink2 = Color(0xFF7B6A72),
    ink3 = Color(0xFFA29198),
    brand = Color(0xFFD4637C),
    brandInk = Color(0xFFA74860),
    brandTint = Color(0xFFFBE7EB),
    danger = Color(0xFFC94F63),
    success = Color(0xFF4F8F72),
    expense = Color(0xFFC94F63),
    income = Color(0xFF4F8F72),
    chartPalette = listOf(
        Color(0xFFE9A6B4), // 蜜桃粉
        Color(0xFF9FC4E3), // 云朵蓝
        Color(0xFFA9CB9B), // 开心果绿
        Color(0xFFEFD08C), // 奶黄
        Color(0xFFBEAFDD), // 香芋紫
        Color(0xFF8FCFC1), // 薄荷
        Color(0xFFF0B694), // 杏子
        Color(0xFFD9A8C8), // 玫瑰
    ),
)

private val macaronDark = PotColors(
    canvas = Color(0xFF17141A),
    panel = Color(0xFF1F1B23),
    panel2 = Color(0xFF272230),
    line = Color(0xFF332C3B),
    lineStrong = Color(0xFF433A4D),
    ink = Color(0xFFF5EEF1),
    ink2 = Color(0xFFC1B5BC),
    ink3 = Color(0xFF928690),
    brand = Color(0xFFF095AC),
    brandInk = Color(0xFFF8C4D2),
    brandTint = Color(0xFF33212A),
    danger = Color(0xFFEF8FA0),
    success = Color(0xFF9ACFAF),
    expense = Color(0xFFEF8FA0),
    income = Color(0xFF9ACFAF),
    chartPalette = listOf(
        Color(0xFFD98FA1),
        Color(0xFF8FB4D2),
        Color(0xFF9DC08F),
        Color(0xFFD9BE7C),
        Color(0xFFB3A4D2),
        Color(0xFF7FC0B3),
        Color(0xFFDCA684),
        Color(0xFFC99AB8),
    ),
    // 粉底上写白字读不出来，深色主题里反着来：糖色底、梅子色的字
    onBrand = Color(0xFF2C1721),
)

/** 暖纸：米白 + 墨色 + 朱砂，和桌面端同一套语气。 */
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
    chartPalette = listOf(
        Color(0xFFC25B3F),
        Color(0xFFD2A03F),
        Color(0xFF6E8B5B),
        Color(0xFF4E7A8A),
        Color(0xFF8A6B9E),
        Color(0xFFB5795E),
        Color(0xFF8A8C63),
        Color(0xFF9C7A4E),
    ),
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
    chartPalette = listOf(
        Color(0xFFD97A5E),
        Color(0xFFDDB35E),
        Color(0xFF8FA87B),
        Color(0xFF6F97A6),
        Color(0xFFA78DB8),
        Color(0xFFCE9379),
        Color(0xFFA3A57D),
        Color(0xFFB8946A),
    ),
)

/** 设置页里那排小色块：拿品牌色 + 三个图表色，一眼看出这套纸是什么味道。 */
fun paletteSwatches(palette: PotPalette): List<Color> = when (palette) {
    PotPalette.Macaron -> macaronLight.chartPalette.take(3) + macaronLight.brand
    PotPalette.Paper -> paperLight.chartPalette.take(3) + paperLight.brand
}

private fun paletteColors(palette: PotPalette, dark: Boolean): PotColors = when (palette) {
    PotPalette.Macaron -> if (dark) macaronDark else macaronLight
    PotPalette.Paper -> if (dark) paperDark else paperLight
}

val LocalPotColors = staticCompositionLocalOf { macaronLight }

val PotShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(11.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

/**
 * 金额字体：Barlow（DIN 那一挂的窄体几何无衬线，银行、票据上的数字长这样）。
 * 数字是 tabular 的，位数变化不会让整行左右跳；行高给得紧一点，大金额不至于把卡片撑开。
 * 只有拉丁数字和符号走这套，中文还是系统字 —— Barlow 里没有汉字。
 */
private val MoneyFont = FontFamily(
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

/** 金额一律用等宽数字，位数变化不会让整行跳动。 */
val MoneyStyle = TextStyle(
    fontFamily = MoneyFont,
    fontWeight = FontWeight.Medium,
    fontSize = 17.sp,
    lineHeight = 22.sp,
    letterSpacing = (0.1).sp,
    fontFeatureSettings = "tnum",
)

val PotTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.9).sp,
        fontFeatureSettings = "tnum",
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
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
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
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
    val target = paletteColors(PotPalette.of(settings.palette), dark)
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
    // 换纸的时候涨跌色也要跟着走，不然红的先跳、底下的米白慢慢淡，看着像掉帧
    val danger by animateColorAsState(target.danger, tween(240), label = "danger")
    val success by animateColorAsState(target.success, tween(240), label = "success")
    val expense by animateColorAsState(target.expense, tween(240), label = "expense")
    val income by animateColorAsState(target.income, tween(240), label = "income")
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
        danger = danger,
        success = success,
        expense = expense,
        income = income,
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
