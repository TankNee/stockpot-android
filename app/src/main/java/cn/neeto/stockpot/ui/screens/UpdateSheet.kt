package cn.neeto.stockpot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.net.Updater
import cn.neeto.stockpot.ui.components.GhostButton
import cn.neeto.stockpot.ui.components.Hairline
import cn.neeto.stockpot.ui.components.MarkdownText
import cn.neeto.stockpot.ui.components.PotSheet
import cn.neeto.stockpot.ui.components.PrimaryButton
import cn.neeto.stockpot.ui.theme.LocalPotColors

/**
 * 更新说明。
 *
 * 以前这段是直接摊在设置页「关于」那张卡里的：一有新版本，卡片凭空长出一大截，
 * 底下的说明和按钮全被顶走，翻设置的人会以为点错了什么。现在收进一个弹层——
 * 说明再长也只在这个壳里滚，设置页永远是一行版本号。
 *
 * 正文按 Markdown 渲染（见 [MarkdownText]）：管理端发版时写的 `##`、`-`、`**`
 * 都能正常显示，而不是把一堆井号和星号原样甩给人看。
 */
@Composable
fun UpdateSheet(
    state: Updater.State,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    val colors = LocalPotColors.current
    val release = state.release ?: return
    val downloading = state.phase == Updater.Phase.DOWNLOADING
    val ready = state.phase == Updater.Phase.DOWNLOADED
    val failed = state.phase == Updater.Phase.FAILED

    PotSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "新版本 ${release.versionName}",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append("现在是 ${state.currentVersion}")
                        append(" · ").append(updateSizeText(release.size))
                        append(" · code ").append(release.versionCode)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                )
            }
        }

        if (release.force) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "这一版是强制更新，装上之前功能可能对不上。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.brandInk,
                lineHeight = 17.sp,
            )
        }

        Spacer(Modifier.height(14.dp))
        Hairline()
        Spacer(Modifier.height(14.dp))

        // 说明可能很长（一整个版本十几条），给它自己滚，别把下面的按钮顶出去
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (release.notes.isBlank()) {
                Text(
                    text = "这一版没写更新说明。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                )
            } else {
                MarkdownText(release.notes)
            }
        }

        Spacer(Modifier.height(16.dp))

        when (state.phase) {
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
                            .fillMaxWidth(state.progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.brand),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "正在下载 ${updateSizeText(release.size)} · ${(state.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                )
            }

            Updater.Phase.FAILED, Updater.Phase.DOWNLOADED, Updater.Phase.READY -> {
                if (failed || state.error != null) {
                    Text(
                        text = state.error ?: "下载失败，再试一次",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.danger,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 装完之前随时能退出这一层：更新不是拦路抢劫。
                    // 已经下好了就把「关掉」换成「重新下载」，省得手滑点了白等一遍。
                    GhostButton(
                        text = if (ready) "重新下载" else "关掉",
                        onClick = if (ready) onDownload else onDismiss,
                    )
                    PrimaryButton(
                        text = if (ready) "立即安装" else "下载并安装",
                        onClick = if (ready) onInstall else onDownload,
                    )
                }
                if (state.needPermission) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "系统里给 Stockpot 打开「安装未知应用」，再回来点一次。",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                        lineHeight = 17.sp,
                    )
                }
            }

            Updater.Phase.CHECKING -> Text(
                text = "正在问服务器…",
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
            )

            Updater.Phase.IDLE -> Text(
                text = "已经是最新的了。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "安装包从你自己的服务器下，不经过应用商店，装了之后账本和登录都还在。",
            fontSize = 11.sp,
            color = colors.ink3,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 字节数说人话。和设置页那一行用的是同一套写法。 */
internal fun updateSizeText(bytes: Long): String = when {
    bytes <= 0L -> "大小未知"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}
