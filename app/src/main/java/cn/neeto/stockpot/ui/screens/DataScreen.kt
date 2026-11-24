package cn.neeto.stockpot.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.neeto.stockpot.core.util.Documents
import cn.neeto.stockpot.ui.PotViewModel
import cn.neeto.stockpot.ui.components.Hairline
import cn.neeto.stockpot.ui.components.PotCard
import cn.neeto.stockpot.ui.components.PotConfirmSheet
import cn.neeto.stockpot.ui.components.SectionTitle
import cn.neeto.stockpot.ui.components.overlayBottomPadding
import cn.neeto.stockpot.ui.theme.LocalPotColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 数据管理：导入、导出、清理都收到这一页。
 *
 * 这六项原来横在设置页第一屏，一屏里一半是「删数据」的红色行，读起来像在催人清空。
 * 搬成二级页以后分三段：从外面拿进来、存一份出去、动手清掉——
 * 危险的两项沉在最后，中间隔一张「备份」，想清空之前先看见导出。
 */
@Composable
fun DataScreen(
    vm: PotViewModel,
    onBack: () -> Unit,
    onOpenImport: () -> Unit,
    onToast: (String) -> Unit,
) {
    val colors = LocalPotColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ledger by vm.ledger.collectAsStateWithLifecycle()

    var confirmClear by remember { mutableStateOf(false) }
    var confirmDemo by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    // 文件大小只在这一页里看个大概，进页面算一次就够
    val fileSize = remember(vm.dataPath) {
        runCatching { File(vm.dataPath).length() }.getOrDefault(0L)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = Documents.writeText(context, uri, vm.exportJson())
        onToast(if (ok) "账本已导出" else "导出失败")
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = withContext(Dispatchers.IO) { Documents.read(context, uri) }
            if (picked == null) {
                onToast("读不到这个文件")
                return@launch
            }
            val outcome = vm.importLedgerJson(String(picked.bytes, Charsets.UTF_8))
            onToast(
                when {
                    outcome == null -> "这不是一份账本文件"
                    outcome.added == 0 -> "没有新账目，全部已存在"
                    else -> "已合并 ${outcome.added} 笔账目"
                },
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        EditorBar(title = "数据管理", onBack = onBack, onConfirm = null)

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
                    Text(
                        "${ledger.entries.size} 笔账目 · ${ledger.categories.size} 个分类 · ${ledger.rules.size} 条规则",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildString {
                            append("本机账本文件 ")
                            append(if (fileSize > 0) humanSize(fileSize) else "（还没写过）")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        vm.dataPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink2,
                        lineHeight = 16.sp,
                    )
                }
            }

            item {
                PotCard(padding = PaddingValues(0.dp)) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
                        SectionTitle("拿进来")
                    }
                    SettingsRow(
                        title = "导入账单",
                        subtitle = "微信 .xlsx / 支付宝 .csv / iCost / 通用 CSV / 账本 JSON",
                        icon = Icons.Outlined.UploadFile,
                        onClick = onOpenImport,
                    )
                    Hairline(Modifier.padding(start = 16.dp))
                    SettingsRow(
                        title = "导入账本文件",
                        subtitle = "合并另一份 ledger.json，重复账目自动跳过",
                        icon = Icons.Outlined.FileOpen,
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    )
                }
            }

            item {
                PotCard(padding = PaddingValues(0.dp)) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
                        SectionTitle("存出去")
                    }
                    SettingsRow(
                        title = "导出账本",
                        subtitle = "一份可读的 ledger.json，桌面端也能打开",
                        icon = Icons.Outlined.SaveAlt,
                        onClick = { exportLauncher.launch("stockpot-ledger.json") },
                    )
                    Hairline(Modifier.padding(start = 16.dp))
                    SettingsRow(
                        title = "载入示例数据",
                        subtitle = "替换当前账本，用来看看图表长什么样",
                        icon = Icons.Outlined.AutoAwesome,
                        onClick = { confirmDemo = true },
                    )
                }
            }

            item {
                PotCard(padding = PaddingValues(0.dp)) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
                        SectionTitle("清掉")
                    }
                    SettingsRow(
                        title = "清空流水",
                        subtitle = "删除所有账目和导入记录，保留分类与规则",
                        icon = Icons.Outlined.DeleteSweep,
                        danger = true,
                        onClick = { confirmClear = true },
                    )
                    Hairline(Modifier.padding(start = 16.dp))
                    SettingsRow(
                        title = "重置分类与规则",
                        subtitle = "恢复出厂的内置分类树",
                        icon = Icons.Outlined.RestartAlt,
                        danger = true,
                        onClick = { confirmReset = true },
                    )
                }
            }
        }
    }

    if (confirmClear) {
        PotConfirmSheet(
            title = "清空流水？",
            body = "所有账目和导入历史会被删除，分类、规则和钱包保留。这一步不能撤销。",
            confirmText = "清空",
            onDismiss = { confirmClear = false },
            onConfirm = {
                vm.clearEntries()
                confirmClear = false
                onToast("流水已清空")
            },
        )
    }

    if (confirmDemo) {
        PotConfirmSheet(
            title = "载入示例数据？",
            body = "当前账本会被示例账本替换。建议先导出备份。",
            confirmText = "载入",
            onDismiss = { confirmDemo = false },
            onConfirm = {
                vm.loadDemo()
                confirmDemo = false
                onToast("示例账本已载入")
            },
        )
    }

    if (confirmReset) {
        PotConfirmSheet(
            title = "重置分类与规则？",
            body = "账目会保留，但它们的分类会变成内置分类树里的名字；不认识的分类会原样留着。",
            confirmText = "重置",
            onDismiss = { confirmReset = false },
            onConfirm = {
                vm.resetAll()
                confirmReset = false
                onToast("已恢复内置分类")
            },
        )
    }
}

private fun humanSize(bytes: Long): String = when {
    bytes <= 0L -> "空"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}
