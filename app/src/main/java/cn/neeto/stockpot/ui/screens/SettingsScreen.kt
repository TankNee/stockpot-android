package cn.neeto.stockpot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.FormatShapes
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.neeto.stockpot.BuildConfig
import cn.neeto.stockpot.core.model.CategoryDef
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Rule
import cn.neeto.stockpot.core.net.SyncManager
import cn.neeto.stockpot.core.net.Updater
import cn.neeto.stockpot.core.settings.AppIcon
import cn.neeto.stockpot.ui.PotViewModel
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.SwapHoriz
import cn.neeto.stockpot.ui.components.PotPillButton
import cn.neeto.stockpot.ui.components.PillTone
import cn.neeto.stockpot.ui.components.Avatar
import cn.neeto.stockpot.ui.components.IconSegmented
import cn.neeto.stockpot.ui.components.IconOption
import cn.neeto.stockpot.ui.components.Chip
import cn.neeto.stockpot.ui.components.EditorAnim
import cn.neeto.stockpot.ui.components.Hairline
import cn.neeto.stockpot.ui.components.Haptic
import cn.neeto.stockpot.ui.components.GhostButton
import cn.neeto.stockpot.ui.components.PrimaryButton
import cn.neeto.stockpot.ui.components.PotCard
import cn.neeto.stockpot.ui.components.SectionTitle
import cn.neeto.stockpot.ui.components.Segmented
import cn.neeto.stockpot.ui.components.SheetField
import cn.neeto.stockpot.ui.components.SheetGrabber
import cn.neeto.stockpot.ui.components.PotSheet
import cn.neeto.stockpot.ui.components.SheetActions
import cn.neeto.stockpot.ui.components.rememberHaptics
import cn.neeto.stockpot.ui.theme.LocalPotColors
import cn.neeto.stockpot.ui.theme.PotPalette
import cn.neeto.stockpot.ui.theme.paletteSwatches

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: PotViewModel,
    onToast: (String) -> Unit,
    onOpenTitleSwap: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenData: () -> Unit,
) {
    val colors = LocalPotColors.current
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val cloud by vm.cloud.collectAsStateWithLifecycle()
    val avatar by vm.avatar.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()

    // 设置页顶上就是「我」：头像和昵称在这里也该看得见，不用点进去
    LaunchedEffect(cloud.signedIn, cloud.account.id, cloud.account.avatarAt) { vm.loadAvatar() }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 账号放最上面：这是「我」，其余都是这台手机怎么显示
        item {
            AccountEntry(
                cloud = cloud,
                avatar = avatar,
                onOpen = onOpenAccount,
            )
        }

        // 有新版本就紧随其后：藏在「关于」里没人会翻到
        if (update.release != null) {
            item {
                UpdateBanner(
                    update = update,
                    onDownload = { vm.downloadUpdate() },
                    onInstall = {
                        if (!vm.installUpdate()) onToast(update.error ?: "打不开安装器")
                    },
                )
            }
        }

        item {
            PotCard {
                SectionTitle("外观")
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
                Spacer(Modifier.height(14.dp))
                Hairline()
                Spacer(Modifier.height(12.dp))
                Text(
                    "配色",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PotPalette.entries.forEach { palette ->
                        PaletteChoice(
                            palette = palette,
                            selected = PotPalette.of(prefs.palette) == palette,
                            modifier = Modifier.weight(1f),
                            onClick = { vm.setPalette(palette.id) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
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
                    Spacer(Modifier.width(14.dp))
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
            }
        }

        item {
            PotCard {
                SectionTitle("图标")
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppIcon.styles.forEach { style ->
                        IconChoice(
                            style = style,
                            masked = prefs.iconMask,
                            selected = prefs.iconStyle == style.id,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val ok = vm.setIconStyle(style.id)
                                if (!ok) onToast("这个系统的桌面不让换图标")
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Hairline()
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "圆形遮罩",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.ink,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "图形收进圆里，桌面强制裁成圆形时边角也不会被切掉。" +
                                "换完没反应的话，重启一下桌面。",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.ink3,
                            lineHeight = 17.sp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = prefs.iconMask,
                        onCheckedChange = { on ->
                            if (!vm.setIconMask(on)) onToast("这个系统的桌面不让换图标")
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        item {
            PotCard(padding = PaddingValues(0.dp)) {
                Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 4.dp)) {
                    SectionTitle("账本")
                }
                SettingsRow(
                    title = "分类管理",
                    subtitle = "${ledger.categories.count { it.parent.isEmpty() }} 个分组 · ${ledger.categories.size} 个分类",
                    icon = Icons.Outlined.Category,
                    onClick = onOpenCategories,
                )
                Hairline(Modifier.padding(start = 16.dp))
                SettingsRow(
                    title = "关键词规则",
                    subtitle = "${ledger.rules.size} 条 · 导入时最长命中的规则胜出",
                    icon = Icons.Outlined.Label,
                    onClick = onOpenRules,
                )
            }
        }

        item {
            PotCard(padding = PaddingValues(0.dp)) {
                Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 4.dp)) {
                    SectionTitle("显示")
                }
                SettingsRow(
                    title = "条目标题整理",
                    subtitle = titleSwapHint(prefs.swapWords.size),
                    icon = Icons.Outlined.FormatShapes,
                    onClick = onOpenTitleSwap,
                )
                Hairline(Modifier.padding(start = 16.dp))
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        "打开条目的动画",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        EditorAnim.of(prefs.editorAnim).hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(9.dp))
                    IconSegmented(
                        options = EditorAnim.entries.map { IconOption(it.id, it.label, it.icon) },
                        selected = EditorAnim.of(prefs.editorAnim).id,
                        onSelect = vm::setEditorAnim,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item {
            PotCard(padding = PaddingValues(0.dp)) {
                Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 4.dp)) {
                    SectionTitle("数据")
                }
                // 导入、导出、清空都收进这一页，设置页留一行入口就够
                SettingsRow(
                    title = "数据管理",
                    subtitle = "${ledger.entries.size} 笔账目 · 导入账单、导出备份、清空流水",
                    icon = Icons.Outlined.SaveAlt,
                    onClick = onOpenData,
                )
            }
        }

        item {
            PotCard {
                SectionTitle("关于")
                Spacer(Modifier.height(8.dp))
                UpdateSection(
                    update = update,
                    onCheck = { vm.checkUpdate(manual = true) },
                    onDownload = { vm.downloadUpdate() },
                    onInstall = {
                        if (!vm.installUpdate()) onToast(update.error ?: "打不开安装器")
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "本地优先：账本以一份可读 JSON 存在应用的私有目录里，写入时先写临时文件再原子替换，并保留一份滚动备份。登录是可选项——不登录，账本就一直只在这台手机上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "界面参考桌面端 Stockpot，技术栈参考 Guodelist：Kotlin + Compose，数据全部落在本机。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                )
            }
        }
    }
}

/** 设置页那一行的小字：把「现在开了几条规则」说清楚，不用点进去也知道有没有开。 */
private fun titleSwapHint(wordCount: Int): String =
    if (wordCount == 0) "已全部关闭 · 美团、淘宝闪购这类条目的标题可以换回来" else "扫描并交换标题与备注 · ${wordCount} 个关键词已启用"

/**
 * 设置页最上面的「我」：头像、昵称、邮箱，右边一个箭头进账号页。
 *
 * 这原来是个灰色小人 + 一行「已登录 · 某某」，灰得像条说明；
 * 现在头像就是账号本身——没设过头像也有一个写着名字的头像圆。
 */
@Composable
private fun AccountEntry(
    cloud: SyncManager.SyncUi,
    avatar: android.graphics.Bitmap?,
    onOpen: () -> Unit,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.panel)
            .clickable { onOpen() }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cloud.signedIn) {
            Avatar(bitmap = avatar, initial = cloud.account.initial, size = 46.dp)
        } else {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(colors.panel2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = colors.ink3,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (cloud.signedIn) cloud.account.label else "登录与同步",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (cloud.signedIn && cloud.account.twoFactor) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Outlined.Security,
                        contentDescription = "两步验证已开",
                        tint = colors.brandInk,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                if (cloud.signedIn) cloud.account.email else cloudHint(cloud),
                style = MaterialTheme.typography.labelSmall,
                color = if (cloud.status == SyncManager.Status.ERROR) colors.danger else colors.ink3,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = "账号",
            tint = colors.ink3,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    danger: Boolean = false,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 每行前面给一个小色块图标：一列纯文字的行读起来像文章，不像设置项
        if (icon != null) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (danger) colors.danger.copy(alpha = 0.12f) else colors.brandTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (danger) colors.danger else colors.brandInk,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (danger) colors.danger else colors.ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(17.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
/* ------------------------------ 云端同步 ------------------------------ */

/** 设置页里那一小截说明文字：状态是同步功能唯一需要露在外面的东西。 */
private fun cloudHint(ui: SyncManager.SyncUi): String = when {
    !ui.signedIn -> "账本只在这台手机上。登录后同步到自己的服务器，不登录也照常用。"
    ui.status == SyncManager.Status.SYNCING -> "正在同步…"
    ui.status == SyncManager.Status.ERROR -> "同步失败：${ui.error ?: "网络不通"}"
    ui.lastSync.isBlank() -> "已登录，等着第一次同步"
    else -> "上次同步 ${syncTimeText(ui.lastSync)} · 改完自动同步"
}

/* -------------------------------- 更新 -------------------------------- */

/**
 * 有新版本时压在设置页最上面的那条。
 *
 * 一个按钮走完全程：没下就下、下完就装。进度直接写在按钮上，
 * 不铺进度条——这条只是提醒，真要细看可以去下面的「关于」。
 */
@Composable
private fun UpdateBanner(
    update: Updater.State,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    val colors = LocalPotColors.current
    val release = update.release ?: return
    val downloading = update.phase == Updater.Phase.DOWNLOADING
    val ready = update.phase == Updater.Phase.DOWNLOADED
    PotCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "有新版本 ${release.versionName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        downloading -> "正在下载 ${(update.progress * 100).toInt()}%"
                        ready -> "${sizeText(release.size)} 已下载，点右边安装"
                        update.phase == Updater.Phase.FAILED -> update.error ?: "下载没成功"
                        else -> "${sizeText(release.size)} · 不用去应用商店"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (update.phase == Updater.Phase.FAILED) colors.danger else colors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            PrimaryButton(
                text = when {
                    downloading -> "${(update.progress * 100).toInt()}%"
                    ready -> "安装"
                    update.phase == Updater.Phase.FAILED -> "重试"
                    else -> "更新"
                },
                onClick = {
                    if (downloading) return@PrimaryButton
                    if (ready) onInstall() else onDownload()
                },
            )
        }
    }
}

/**
 * 关于卡片的上半截：版本号 + 检查更新。
 *
 * 有新版才把说明和按钮摊开——平时这里就一行「已是最新」，
 * 不占地方也不催人升级。强制更新的版本不拦人，只是在说明后面标一句。
 */
@Composable
private fun UpdateSection(
    update: Updater.State,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    val colors = LocalPotColors.current
    val release = update.release
    val failed = update.phase == Updater.Phase.FAILED

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "Stockpot ${update.currentVersion}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    update.phase == Updater.Phase.CHECKING -> "正在问服务器…"
                    update.phase == Updater.Phase.DOWNLOADING ->
                        "下载中 ${(update.progress * 100).toInt()}%"
                    update.phase == Updater.Phase.DOWNLOADED -> "安装包已下载，点一下就能装"
                    failed -> update.error ?: "检查更新失败"
                    release != null -> "有新版本可以装"
                    update.error != null -> update.error.orEmpty()
                    update.checkedAt.isNotBlank() -> "已是最新 · ${syncTimeText(update.checkedAt)}查过"
                    else -> "还没有检查过"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (failed || (update.error != null && release == null)) colors.danger else colors.ink3,
                lineHeight = 17.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        PotPillButton(
            text = if (update.phase == Updater.Phase.CHECKING) "检查中" else "检查更新",
            icon = Icons.Outlined.Refresh,
            tone = PillTone.Ghost,
            enabled = update.phase != Updater.Phase.CHECKING && update.phase != Updater.Phase.DOWNLOADING,
            onClick = onCheck,
        )
    }

    if (release == null) return

    Spacer(Modifier.height(8.dp))
    Hairline()
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "新版本 ${release.versionName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(sizeText(release.size))
                    append(" · code ").append(release.versionCode)
                    if (release.force) append(" · 强制更新")
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (release.force) colors.brandInk else colors.ink3,
            )
        }
    }
    if (release.notes.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            release.notes,
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink2,
            lineHeight = 19.sp,
        )
    }
    Spacer(Modifier.height(12.dp))

    when (update.phase) {
        Updater.Phase.DOWNLOADING -> {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.panel2),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(update.progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.brand),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "正在下载 ${sizeText(release.size)}…",
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
            )
        }
        Updater.Phase.DOWNLOADED -> PrimaryButton(text = "立即安装", onClick = onInstall)
        Updater.Phase.FAILED -> PrimaryButton(text = "重新下载", onClick = onDownload)
        Updater.Phase.READY -> PrimaryButton(text = "下载并安装", onClick = onDownload)
        else -> Unit
    }

    if (update.needPermission) {
        Spacer(Modifier.height(6.dp))
        Text(
            "系统里给 Stockpot 打开「安装未知应用」，再回来点一次。",
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
            lineHeight = 17.sp,
        )
    }
}

/** 字节数说人话。安装包就那么几个量级，不用做成通用的。 */
private fun sizeText(bytes: Long): String = when {
    bytes <= 0L -> "大小未知"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}

/* -------------------------------- 图标 -------------------------------- */

/**
 * 图标预览。自适应图标在桌面上只露中间 72dp，所以这里把 108dp 的图形放大 1.5 倍
 * 再让外层裁掉多余的边——预览才和桌面看到的一致。
 */
@Composable
private fun AppIconPreview(style: AppIcon.Style, masked: Boolean, size: Dp) {
    val background = Color(style.background)
    val shape = if (masked) CircleShape else RoundedCornerShape(size * 0.26f)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(background)
            .border(
                width = 0.5.dp,
                color = if (background.luminance() > 0.7f) Color(0x1F000000) else Color.Transparent,
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(
                if (masked) style.foregroundMasked else style.foreground,
            ),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.requiredSize(size * 1.5f),
        )
    }
}

/** 设置页里那个可点的样式胶囊：图标 + 名字，选中时套一层品牌色底。 */
@Composable
private fun IconChoice(
    style: AppIcon.Style,
    masked: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalPotColors.current
    val haptics = rememberHaptics()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                haptics(Haptic.Tap)
                onClick()
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(if (selected) colors.brandTint else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            AppIconPreview(style = style, masked = masked, size = 42.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            style.label,
            fontSize = 11.sp,
            color = if (selected) colors.brandInk else colors.ink3,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/**
 * 一张纸样的选择卡：四个小圆点预报这套配色，下面是名字和一句说明。
 * 换配色的时候整页颜色会自己淡过去（PotTheme 里做的插值），这里只负责选。
 */
@Composable
private fun PaletteChoice(
    palette: PotPalette,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalPotColors.current
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.brandTint else colors.panel2)
            .then(
                if (selected) {
                    Modifier.border(1.dp, colors.brand.copy(alpha = 0.45f), shape)
                } else {
                    Modifier
                },
            )
            .clickable {
                if (!selected) haptics(Haptic.Tap)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            paletteSwatches(palette).forEach { swatch ->
                Box(
                    Modifier
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(swatch),
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            palette.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) colors.brandInk else colors.ink,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            palette.hint,
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
            lineHeight = 15.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
