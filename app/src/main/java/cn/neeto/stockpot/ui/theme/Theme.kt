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
import androidx.compose.ui.graphics.toArgb
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
import java.util.Locale

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

/** 预设里的一块色：名字 + 色值，主题页照着原图摆一排。 */
data class PaletteSwatch(val name: String, val color: Color) {
    val hex: String get() = String.format(Locale.ROOT, "#%06X", color.toArgb() and 0xFFFFFF)
}

/**
 * 主题纸样。五套都是从「一张流体大理石图 + 三块预设色」搬过来的：
 * 预设色给得很淡，只能当纸面；品牌色另外从同一色相里往深压一档，
 * 否则按钮上写什么都读不出来。
 *
 * id 存在设置里，改 id 等于让所有老用户的偏好失效，别动。
 */
enum class PotPalette(
    val id: String,
    val label: String,
    val hint: String,
    /** 原图底下那三块预设色，顺序也照原图。 */
    val swatches: List<PaletteSwatch>,
) {
    Pistachio(
        id = "pistachio",
        label = "开心果绿",
        hint = "燕麦奶打底，抹一层薄薄的抹茶",
        swatches = listOf(
            PaletteSwatch("开心果绿", Color(0xFFCFE4C6)),
            PaletteSwatch("燕麦奶", Color(0xFFF2EBDD)),
            PaletteSwatch("柔雾灰", Color(0xFFDDD9D2)),
        ),
    ),
    Butter(
        id = "butter",
        label = "柠檬奶黄",
        hint = "奶油白里掺一勺柠檬酱，暖而不刺眼",
        swatches = listOf(
            PaletteSwatch("柠檬奶黄", Color(0xFFF6E8A8)),
            PaletteSwatch("奶油白", Color(0xFFFAF6ED)),
            PaletteSwatch("杏仁米", Color(0xFFEEDFCB)),
        ),
    ),
    Peach(
        id = "peach",
        label = "蜜桃粉",
        hint = "蜜桃配海盐，粉里透一点冷",
        swatches = listOf(
            PaletteSwatch("蜜桃粉", Color(0xFFF5D1C9)),
            PaletteSwatch("海盐蓝", Color(0xFFD8E7F2)),
            PaletteSwatch("绵云白", Color(0xFFF7F5F2)),
        ),
    ),
    LemonAsh(
        id = "lemon-ash",
        label = "奶油柠檬",
        hint = "浅雾灰压住柠檬，比上一套更沉静",
        swatches = listOf(
            PaletteSwatch("奶油柠檬", Color(0xFFF1E39A)),
            PaletteSwatch("浅雾灰", Color(0xFFD6D0C8)),
            PaletteSwatch("杏仁白", Color(0xFFF6EFE6)),
        ),
    ),
    Mint(
        id = "mint",
        label = "薄荷青",
        hint = "薄荷青与鼠尾草灰，最凉的一套",
        swatches = listOf(
            PaletteSwatch("薄荷青", Color(0xFFC7E6D3)),
            PaletteSwatch("冷灰白", Color(0xFFE6E4E1)),
            PaletteSwatch("鼠尾草灰", Color(0xFFA8B7A8)),
        ),
    ),
    ;

    companion object {
        /**
         * 老版本存的是 macaron / paper，现在没有这两套了：
         * 按气味就近接过去，别让升级的人一睁眼回到默认。
         */
        fun of(id: String?): PotPalette = when (id) {
            "macaron" -> Pistachio
            "paper" -> LemonAsh
            else -> entries.firstOrNull { it.id == id } ?: Pistachio
        }
    }
}

/** 开心果绿：燕麦奶的底，绿只出现在该出现的地方。 */
private val pistachioLight = PotColors(
    canvas = Color(0xFFF4F2E8),
    panel = Color(0xFFFDFCF7),
    panel2 = Color(0xFFECE9DC),
    line = Color(0xFFE2DFD1),
    lineStrong = Color(0xFFCFCBB9),
    ink = Color(0xFF2E362E),
    ink2 = Color(0xFF67705F),
    ink3 = Color(0xFF969D8D),
    brand = Color(0xFF6B9A70),
    brandInk = Color(0xFF4A7050),
    brandTint = Color(0xFFE3EFDC),
    danger = Color(0xFFBE5A4A),
    success = Color(0xFF4C8A63),
    expense = Color(0xFFBE5A4A),
    income = Color(0xFF4C8A63),
    chartPalette = listOf(
        Color(0xFFA6C79C), // 开心果
        Color(0xFFD9CB8E), // 燕麦黄
        Color(0xFF8FA9B8), // 雾蓝
        Color(0xFFC7A594), // 陶土
        Color(0xFFA9B79B), // 鼠尾草
        Color(0xFFD3B48A), // 麦芽
        Color(0xFF96B5A6), // 薄荷灰
        Color(0xFFB8A9C6), // 淡紫
    ),
)

private val pistachioDark = PotColors(
    canvas = Color(0xFF131713),
    panel = Color(0xFF1A1F1A),
    panel2 = Color(0xFF222823),
    line = Color(0xFF2C332D),
    lineStrong = Color(0xFF3A423B),
    ink = Color(0xFFEEF2EA),
    ink2 = Color(0xFFB3BCAF),
    ink3 = Color(0xFF8B9488),
    brand = Color(0xFF9BC7A0),
    brandInk = Color(0xFFC4E3C8),
    brandTint = Color(0xFF22301F),
    danger = Color(0xFFE58C7B),
    success = Color(0xFF89C39C),
    expense = Color(0xFFE58C7B),
    income = Color(0xFF89C39C),
    chartPalette = listOf(
        Color(0xFF8FB68A),
        Color(0xFFC7B873),
        Color(0xFF7E99A8),
        Color(0xFFB08F7F),
        Color(0xFF93A183),
        Color(0xFFBF9E77),
        Color(0xFF81A093),
        Color(0xFFA293AF),
    ),
    onBrand = Color(0xFF14210F),
)

/** 柠檬奶黄：黄底上写白字读不出来，所以这套品牌按钮是深褐色的字。 */
private val butterLight = PotColors(
    canvas = Color(0xFFF9F5E9),
    panel = Color(0xFFFFFDF8),
    panel2 = Color(0xFFF1ECDB),
    line = Color(0xFFE8E2CE),
    lineStrong = Color(0xFFD6CEB4),
    ink = Color(0xFF383227),
    ink2 = Color(0xFF766E5B),
    ink3 = Color(0xFFA2987F),
    brand = Color(0xFFD2B04A),
    brandInk = Color(0xFF8F7318),
    brandTint = Color(0xFFFAF0C9),
    danger = Color(0xFFC05F49),
    success = Color(0xFF558956),
    expense = Color(0xFFC05F49),
    income = Color(0xFF558956),
    chartPalette = listOf(
        Color(0xFFE0C468), // 柠檬奶黄
        Color(0xFFC9A63C), // 芥黄
        Color(0xFFA3C0A0), // 青苗
        Color(0xFF8FA9B8), // 雾蓝
        Color(0xFFCFA8B0), // 藕粉
        Color(0xFFD6BC8F), // 杏仁
        Color(0xFF94AE8C), // 橄榄
        Color(0xFFB9A6C6), // 淡紫
    ),
    onBrand = Color(0xFF33290F),
)

private val butterDark = PotColors(
    canvas = Color(0xFF14130E),
    panel = Color(0xFF1D1C15),
    panel2 = Color(0xFF26241A),
    line = Color(0xFF312F22),
    lineStrong = Color(0xFF403D2C),
    ink = Color(0xFFF5F1E4),
    ink2 = Color(0xFFC2BAA4),
    ink3 = Color(0xFF978F7A),
    brand = Color(0xFFDCC06A),
    brandInk = Color(0xFFF2E0A4),
    brandTint = Color(0xFF2C2611),
    danger = Color(0xFFE89380),
    success = Color(0xFF94C297),
    expense = Color(0xFFE89380),
    income = Color(0xFF94C297),
    chartPalette = listOf(
        Color(0xFFC7AC58),
        Color(0xFFE0C468),
        Color(0xFF8DA98A),
        Color(0xFF7D95A4),
        Color(0xFFB08F97),
        Color(0xFFBFA477),
        Color(0xFF7F9877),
        Color(0xFFA291AF),
    ),
    onBrand = Color(0xFF241D08),
)

/** 蜜桃粉：粉当主角，海盐蓝留给第二根柱子，不然整页会甜得发腻。 */
private val peachLight = PotColors(
    canvas = Color(0xFFFAF5F3),
    panel = Color(0xFFFFFCFB),
    panel2 = Color(0xFFF4ECE9),
    line = Color(0xFFECE2DE),
    lineStrong = Color(0xFFDCCFC9),
    ink = Color(0xFF3A3235),
    ink2 = Color(0xFF7A6D71),
    ink3 = Color(0xFFA69A9E),
    brand = Color(0xFFC5796C),
    brandInk = Color(0xFF9E574B),
    brandTint = Color(0xFFFBE9E5),
    danger = Color(0xFFBE5D57),
    success = Color(0xFF4F8A6B),
    expense = Color(0xFFBE5D57),
    income = Color(0xFF4F8A6B),
    chartPalette = listOf(
        Color(0xFFE3A79B), // 蜜桃
        Color(0xFF9FC0DA), // 海盐蓝
        Color(0xFFA8C9A2), // 青提
        Color(0xFFE4C77F), // 奶黄
        Color(0xFFBCA8CE), // 香芋
        Color(0xFF8FBFB4), // 薄荷
        Color(0xFFDDB48F), // 杏子
        Color(0xFFC79AB2), // 玫瑰
    ),
)

private val peachDark = PotColors(
    canvas = Color(0xFF171314),
    panel = Color(0xFF1F1A1C),
    panel2 = Color(0xFF282123),
    line = Color(0xFF332B2E),
    lineStrong = Color(0xFF42383B),
    ink = Color(0xFFF6EFEF),
    ink2 = Color(0xFFC3B6B8),
    ink3 = Color(0xFF998C8F),
    brand = Color(0xFFE8A197),
    brandInk = Color(0xFFF6C9C2),
    brandTint = Color(0xFF33221F),
    danger = Color(0xFFEF9C90),
    success = Color(0xFF93C4A8),
    expense = Color(0xFFEF9C90),
    income = Color(0xFF93C4A8),
    chartPalette = listOf(
        Color(0xFFC98E84),
        Color(0xFF8AA8C0),
        Color(0xFF91AF8B),
        Color(0xFFC7AE6E),
        Color(0xFFA592B5),
        Color(0xFF7FA79E),
        Color(0xFFC29B7B),
        Color(0xFFB0839A),
    ),
    onBrand = Color(0xFF2A1614),
)

/** 奶油柠檬：和柠檬奶黄同色相，但整体往灰里退半步，看久了不刺。 */
private val lemonAshLight = PotColors(
    canvas = Color(0xFFF5F2EA),
    panel = Color(0xFFFDFCF8),
    panel2 = Color(0xFFEFEBE0),
    line = Color(0xFFE5E0D3),
    lineStrong = Color(0xFFD3CDBE),
    ink = Color(0xFF33322C),
    ink2 = Color(0xFF6F6C61),
    ink3 = Color(0xFF9C988B),
    brand = Color(0xFFC0A34C),
    brandInk = Color(0xFF8A7222),
    brandTint = Color(0xFFF7F0D3),
    danger = Color(0xFFBB5F4B),
    success = Color(0xFF5A8A5E),
    expense = Color(0xFFBB5F4B),
    income = Color(0xFF5A8A5E),
    chartPalette = listOf(
        Color(0xFFDCC672), // 奶油柠檬
        Color(0xFFC0A34C), // 暗金
        Color(0xFFA5BC9C), // 灰绿
        Color(0xFF8FA3B0), // 冷蓝
        Color(0xFFC0A8A0), // 灰粉
        Color(0xFFCFB98A), // 麦色
        Color(0xFF97A88F), // 鼠尾草
        Color(0xFFB3A6BF), // 灰紫
    ),
    onBrand = Color(0xFF2B2513),
)

private val lemonAshDark = PotColors(
    canvas = Color(0xFF14130F),
    panel = Color(0xFF1D1C17),
    panel2 = Color(0xFF26241D),
    line = Color(0xFF302E24),
    lineStrong = Color(0xFF3F3C2F),
    ink = Color(0xFFF4F1E7),
    ink2 = Color(0xFFBFB9AA),
    ink3 = Color(0xFF948E7D),
    brand = Color(0xFFD6BE6D),
    brandInk = Color(0xFFEFDDA2),
    brandTint = Color(0xFF2A2513),
    danger = Color(0xFFE6917E),
    success = Color(0xFF97C29B),
    expense = Color(0xFFE6917E),
    income = Color(0xFF97C29B),
    chartPalette = listOf(
        Color(0xFFC4AC61),
        Color(0xFFD6BE6D),
        Color(0xFF90A487),
        Color(0xFF7D8F9B),
        Color(0xFFA8928B),
        Color(0xFFB8A376),
        Color(0xFF83967C),
        Color(0xFF9D91A9),
    ),
    onBrand = Color(0xFF211C0B),
)

/** 薄荷青：绿里带蓝，配鼠尾草灰，整套最凉。 */
private val mintLight = PotColors(
    canvas = Color(0xFFF1F4F0),
    panel = Color(0xFFFBFDFB),
    panel2 = Color(0xFFE9EEE8),
    line = Color(0xFFDFE4DE),
    lineStrong = Color(0xFFCBD3CB),
    ink = Color(0xFF2B3531),
    ink2 = Color(0xFF64706A),
    ink3 = Color(0xFF929D97),
    brand = Color(0xFF5FA98D),
    brandInk = Color(0xFF3F7C67),
    brandTint = Color(0xFFE1F0E9),
    danger = Color(0xFFBC5F55),
    success = Color(0xFF4A8A6E),
    expense = Color(0xFFBC5F55),
    income = Color(0xFF4A8A6E),
    chartPalette = listOf(
        Color(0xFF9BCFBA), // 薄荷青
        Color(0xFFA8B7A8), // 鼠尾草
        Color(0xFF8FA9B8), // 冷蓝
        Color(0xFFD8C98E), // 沙黄
        Color(0xFFC9A8B8), // 藕粉
        Color(0xFFB3C49B), // 青苗
        Color(0xFF8FC7BE), // 湖水
        Color(0xFFBDB098), // 亚麻
    ),
)

private val mintDark = PotColors(
    canvas = Color(0xFF121614),
    panel = Color(0xFF1A1F1D),
    panel2 = Color(0xFF222825),
    line = Color(0xFF2C322F),
    lineStrong = Color(0xFF39423E),
    ink = Color(0xFFEDF2EF),
    ink2 = Color(0xFFB1BBB6),
    ink3 = Color(0xFF88938E),
    brand = Color(0xFF7FC0A6),
    brandInk = Color(0xFFA9DCC6),
    brandTint = Color(0xFF1D2C26),
    danger = Color(0xFFE68A7E),
    success = Color(0xFF86C4A8),
    expense = Color(0xFFE68A7E),
    income = Color(0xFF86C4A8),
    chartPalette = listOf(
        Color(0xFF7FB09C),
        Color(0xFF94A394),
        Color(0xFF7C94A3),
        Color(0xFFC2B37C),
        Color(0xFFB295A3),
        Color(0xFF9CAE84),
        Color(0xFF79B2A9),
        Color(0xFFA89C86),
    ),
    onBrand = Color(0xFF10221A),
)

private fun paletteColors(palette: PotPalette, dark: Boolean): PotColors = when (palette) {
    PotPalette.Pistachio -> if (dark) pistachioDark else pistachioLight
    PotPalette.Butter -> if (dark) butterDark else butterLight
    PotPalette.Peach -> if (dark) peachDark else peachLight
    PotPalette.LemonAsh -> if (dark) lemonAshDark else lemonAshLight
    PotPalette.Mint -> if (dark) mintDark else mintLight
}

/** 别的页面想偷看一眼某套纸（主题页里的预览卡用），不去动当前主题。 */
fun previewColors(palette: PotPalette, dark: Boolean): PotColors = paletteColors(palette, dark)

val LocalPotColors = staticCompositionLocalOf { pistachioLight }

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
    // 黄底那两套的品牌按钮写的是深色字，深浅一切换就会跳，跟着淡过去
    val onBrand by animateColorAsState(target.onBrand, tween(240), label = "onBrand")
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
        onBrand = onBrand,
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
