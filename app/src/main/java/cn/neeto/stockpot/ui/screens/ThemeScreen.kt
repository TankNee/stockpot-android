package cn.neeto.stockpot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.neeto.stockpot.ui.PotViewModel
import cn.neeto.stockpot.ui.components.Haptic
import cn.neeto.stockpot.ui.components.IconOption
import cn.neeto.stockpot.ui.components.IconSegmented
import cn.neeto.stockpot.ui.components.PotCard
import cn.neeto.stockpot.ui.components.SectionTitle
import cn.neeto.stockpot.ui.components.overlayBottomPadding
import cn.neeto.stockpot.ui.components.rememberHaptics
import cn.neeto.stockpot.ui.theme.LocalPotColors
import cn.neeto.stockpot.ui.theme.PotPalette
import cn.neeto.stockpot.ui.theme.previewColors

/**
 * 主题页。
 *
 * 五套纸都来自同一批预设：一张流体大理石图 + 底下三块色。这里照原样摆回来——
 * 每套给一张「换过去之后长什么样」的缩略图（用那套纸的颜色现画，不截屏），
 * 底下三块预设色带名字和色值，选之前就能看清它到底是什么味道。
 *
 * 深浅是叠在配色之上的另一档，所以放在最上面单独一排。
 */
@Composable
fun ThemeScreen(
    vm: PotViewModel,
    onBack: () -> Unit,
) {
    val colors = LocalPotColors.current
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val dark = when (prefs.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val current = PotPalette.of(prefs.palette)

    Column(Modifier.fillMaxSize()) {
        EditorBar(title = "主题", onBack = onBack, onConfirm = null)

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = overlayBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PotCard {
                    SectionTitle("深浅")
                    Spacer(Modifier.height(10.dp))
                    IconSegmented(
                        options = listOf(
                            IconOption("system", "跟随系统", Icons.Outlined.BrightnessAuto),
                            IconOption("light", "浅色", Icons.Outlined.LightMode),
                            IconOption("dark", "深色", Icons.Outlined.DarkMode),
                        ),
                        selected = prefs.theme,
                        onSelect = vm::setTheme,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "换配色只改纸面；深浅是另一档，两样可以叠着来。下面每张图都按当前的深浅画。",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                        lineHeight = 17.sp,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "配色",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.ink,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${PotPalette.entries.size} 套",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        current.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.brandInk,
                    )
                }
            }

            items(PotPalette.entries.size) { index ->
                val palette = PotPalette.entries[index]
                PaletteCard(
                    palette = palette,
                    dark = dark,
                    selected = palette == current,
                    onPick = { vm.setPalette(palette.id) },
                )
            }

            item {
                PotCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(colors.brandTint),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("支", fontSize = 11.sp, color = colors.expense)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("支出用红", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                        Spacer(Modifier.width(16.dp))
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(colors.income.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("收", fontSize = 11.sp, color = colors.income)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("收入用绿", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "五套纸都守这个约：红是花的、绿是进的，只把音量调成跟纸面合得来的一档。",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

/**
 * 一套纸的选择卡。
 *
 * 上半截是缩略图：拿那套纸的 canvas / panel / brand 现画一条「余额 + 一笔账」，
 * 换过去之后长什么样，这里就是什么样。
 */
@Composable
private fun PaletteCard(
    palette: PotPalette,
    dark: Boolean,
    selected: Boolean,
    onPick: () -> Unit,
) {
    val colors = LocalPotColors.current
    val haptics = rememberHaptics()
    val preview = previewColors(palette, dark)
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.panel)
            .then(
                if (selected) Modifier.border(1.5.dp, colors.brand.copy(alpha = 0.55f), shape)
                else Modifier,
            )
            .clickable {
                if (!selected) haptics(Haptic.Tap)
                onPick()
            },
    ) {
        // 缩略图：纸面从左上往右下淡一点，像那张图的光
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            preview.canvas,
                            preview.panel2,
                            preview.brandTint,
                        ),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Box(
                    Modifier
                        .width(78.dp)
                        .height(13.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(preview.ink.copy(alpha = 0.82f)),
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .width(46.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(preview.ink3.copy(alpha = 0.55f)),
                )
                Spacer(Modifier.height(11.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(preview.panel)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(preview.brandTint),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(preview.brand),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.62f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(preview.ink.copy(alpha = 0.72f)),
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .fillMaxWidth(0.38f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(preview.ink3.copy(alpha = 0.5f)),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .width(34.dp)
                            .height(9.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(preview.brand),
                    )
                }
            }

            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.brand),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = colors.onBrand,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    palette.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink,
                )
                Spacer(Modifier.width(8.dp))
                if (selected) {
                    Text(
                        "正在用",
                        fontSize = 10.sp,
                        color = colors.brandInk,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.brandTint)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                palette.hint,
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                palette.swatches.forEach { swatch ->
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(swatch.color)
                                .border(1.dp, colors.line, CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                swatch.name,
                                fontSize = 10.sp,
                                color = colors.ink2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                swatch.hex,
                                fontSize = 9.sp,
                                color = colors.ink3,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
