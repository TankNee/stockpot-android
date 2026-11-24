package cn.neeto.stockpot.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.importer.ImportOutcome
import cn.neeto.stockpot.core.importer.ImportPreview
import cn.neeto.stockpot.core.importer.ParsedBill
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.util.Documents
import cn.neeto.stockpot.ui.PickResult
import cn.neeto.stockpot.ui.PotViewModel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import cn.neeto.stockpot.ui.components.PotPillButton
import cn.neeto.stockpot.ui.components.PillTone
import cn.neeto.stockpot.ui.components.Chip
import cn.neeto.stockpot.ui.components.Hairline
import cn.neeto.stockpot.ui.components.GhostButton
import cn.neeto.stockpot.ui.components.PrimaryButton
import cn.neeto.stockpot.ui.components.PotCard
import cn.neeto.stockpot.ui.components.SectionTitle
import cn.neeto.stockpot.ui.components.overlayBottomPadding
import cn.neeto.stockpot.ui.theme.LocalPotColors
import cn.neeto.stockpot.ui.theme.MoneyStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun sourceLabel(source: String): String = when (source) {
    "wechat" -> "微信支付"
    "alipay" -> "支付宝"
    "icost" -> "iCost"
    "generic" -> "通用 CSV"
    "imported" -> "账本 JSON"
    "manual" -> "手工记账"
    else -> source
}

@Composable
fun ImportScreen(
    vm: PotViewModel,
    incoming: Documents.Picked?,
    onIncomingConsumed: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalPotColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ledger by vm.ledger.collectAsStateWithLifecycle()

    var busy by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<PickResult?>(null) }
    var outcome by remember { mutableStateOf<ImportOutcome?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun inspect(bytes: ByteArray, name: String) {
        busy = true
        message = null
        outcome = null
        pick = vm.inspect(bytes, name)
        busy = false
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val picked = withContext(Dispatchers.IO) { Documents.read(context, uri) }
            busy = false
            if (picked == null) {
                message = "读不到这个文件，换个文件试试"
                return@launch
            }
            inspect(picked.bytes, picked.name)
        }
    }

    LaunchedEffect(incoming) {
        val file = incoming ?: return@LaunchedEffect
        // 先交给外部置空，再用与组合无关的 scope 去解析：
        // 直接在这里 await 的话，incoming 变 null 会把协程一起取消掉。
        onIncomingConsumed()
        scope.launch { inspect(file.bytes, file.name) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 2.dp,
            bottom = overlayBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = colors.ink2,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text("导入账单", style = MaterialTheme.typography.titleMedium, color = colors.ink)
            }
        }
        item {
            Column {
                Text(
                    "把各家的账单倒进来，重复导入也不会多出一行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                )
                Spacer(Modifier.height(12.dp))
                PotCard {
                    SectionTitle("支持的来源")
                    Spacer(Modifier.height(10.dp))
                    FormatRow("微信支付", "账单明细 .xlsx，按表头识别，不认文件名")
                    FormatRow("支付宝", "交易明细 .csv，GBK 编码也能读")
                    FormatRow("iCost / 通用 CSV", "日期、金额、分类、备注按列名自动对齐")
                    FormatRow("Stockpot 账本", "桌面端导出的 ledger.json，可直接合并")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "文件在手机上解析，不上传任何服务器。",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                    )
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(
                    text = if (busy) "正在解析…" else "选择文件",
                    onClick = {
                        if (!busy) launcher.launch(arrayOf("*/*"))
                    },
                )
                Spacer(Modifier.width(10.dp))
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = colors.brand,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        "也可以在微信/支付宝里直接分享到 Stockpot",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                    )
                }
            }
        }

        val notice = message
        if (notice != null) {
            item {
                PotCard {
                    Text(notice, style = MaterialTheme.typography.bodySmall, color = colors.danger)
                }
            }
        }

        val current = pick
        if (current is PickResult.Failed) {
            item {
                PotCard {
                    Text(current.message, style = MaterialTheme.typography.bodySmall, color = colors.ink2)
                }
            }
        }

        if (current is PickResult.Bill) {
            item {
                BillPreviewCard(
                    bill = current.bill,
                    preview = current.preview,
                    onCancel = { pick = null },
                    onConfirm = {
                        val result = vm.commitImport(current.bill, current.preview.fileName)
                        outcome = result
                        pick = null
                    },
                )
            }
        }

        if (current is PickResult.LedgerFile) {
            item {
                LedgerPreviewCard(
                    fileName = current.fileName,
                    incoming = current.incoming,
                    onCancel = { pick = null },
                    onConfirm = {
                        outcome = vm.commitLedger(current.incoming)
                        pick = null
                    },
                )
            }
        }

        val done = outcome
        if (done != null) {
            item {
                PotCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(colors.income.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✓", color = colors.income, fontSize = 15.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "已导入 ${done.added} 笔",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                buildString {
                                    append(sourceLabel(done.source))
                                    if (done.duplicates > 0) append("  ·  跳过重复 ${done.duplicates}")
                                    if (done.ignored > 0) append("  ·  忽略 ${done.ignored}")
                                    if (done.refundsLinked > 0) append("  ·  关联退款 ${done.refundsLinked}")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.ink3,
                            )
                        }
                        PotPillButton(text = "好", icon = Icons.Outlined.Check, onClick = { outcome = null })
                    }
                }
            }
        }

        if (ledger.imports.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                SectionTitle("导入历史")
            }
            items(ledger.imports.take(20).size) { index ->
                val record = ledger.imports[index]
                PotCard(padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                record.file,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${sourceLabel(record.source)}  ·  新增 ${record.added}  ·  跳过 ${record.skipped}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.ink3,
                            )
                        }
                        Text(Fmt.stampLabel(record.importedAt), fontSize = 12.sp, color = colors.ink3)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatRow(title: String, body: String) {
    val colors = LocalPotColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(colors.brand.copy(alpha = 0.6f)),
        )
        Spacer(Modifier.width(9.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodySmall, color = colors.ink, fontWeight = FontWeight.Medium)
            Text(body, style = MaterialTheme.typography.labelSmall, color = colors.ink3, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun BillPreviewCard(
    bill: ParsedBill,
    preview: ImportPreview,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = LocalPotColors.current
    PotCard {
        SectionTitle("预览", trailing = { Chip(label = sourceLabel(preview.source), active = true, onClick = {}) })
        Spacer(Modifier.height(8.dp))
        Text(
            preview.fileName,
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            PreviewStat("可导入", "${preview.rows - preview.duplicates}", Modifier.weight(1f))
            PreviewStat("重复", "${preview.duplicates}", Modifier.weight(1f))
            PreviewStat("忽略", "${preview.ignored}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Hairline()
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("支出合计", fontSize = 12.sp, color = colors.ink3)
                Spacer(Modifier.height(3.dp))
                Text(Fmt.yuan(preview.expense), style = MoneyStyle.copy(fontSize = 16.sp), color = colors.expense)
            }
            Column(Modifier.weight(1f)) {
                Text("收入合计", fontSize = 12.sp, color = colors.ink3)
                Spacer(Modifier.height(3.dp))
                Text(Fmt.yuan(preview.income), style = MoneyStyle.copy(fontSize = 16.sp), color = colors.income)
            }
            Column(Modifier.weight(1f)) {
                Text("日期范围", fontSize = 12.sp, color = colors.ink3)
                Spacer(Modifier.height(3.dp))
                Text(
                    if (preview.firstDate.isEmpty()) "—" else "${Fmt.shortDate(preview.firstDate)}–${Fmt.shortDate(preview.lastDate)}",
                    style = MoneyStyle.copy(fontSize = 15.sp),
                    color = colors.ink,
                )
            }
        }
        if (preview.sample.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Hairline()
            Spacer(Modifier.height(8.dp))
            Text("先看几笔", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
            Spacer(Modifier.height(4.dp))
            preview.sample.take(4).forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Fmt.shortDate(row.date), fontSize = 12.sp, color = colors.ink3, modifier = Modifier.width(44.dp))
                    Text(
                        row.counterparty.ifEmpty { row.note.ifEmpty { "—" } },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        (if (row.kind == Kind.EXPENSE) "-" else "+") + Fmt.yuan(row.amount),
                        style = MoneyStyle.copy(fontSize = 13.sp),
                        color = if (row.kind == Kind.EXPENSE) colors.expense else colors.income,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PotPillButton(text = "取消", icon = Icons.Outlined.Close, tone = PillTone.Ghost, onClick = onCancel)
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                text = if (preview.rows - preview.duplicates <= 0) "没有新记录" else "导入 ${preview.rows - preview.duplicates} 笔",
                onClick = { if (preview.rows - preview.duplicates > 0) onConfirm() },
            )
        }
    }
}

@Composable
private fun LedgerPreviewCard(
    fileName: String,
    incoming: cn.neeto.stockpot.core.model.Ledger,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = LocalPotColors.current
    val dates = incoming.entries.map { it.date }.filter { it.isNotEmpty() }
    PotCard {
        SectionTitle("账本文件", trailing = { Chip(label = "JSON", active = true, onClick = {}) })
        Spacer(Modifier.height(8.dp))
        Text(fileName, style = MaterialTheme.typography.bodySmall, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            PreviewStat("账目", "${incoming.entries.size}", Modifier.weight(1f))
            PreviewStat("分类", "${incoming.categories.size}", Modifier.weight(1f))
            PreviewStat("项目", "${incoming.projects.size}", Modifier.weight(1f))
        }
        if (dates.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${Fmt.shortDate(dates.min())} – ${Fmt.shortDate(dates.max())}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "按 id 和去重键合并，重复导入同一份文件不会产生重复账目。",
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PotPillButton(text = "取消", icon = Icons.Outlined.Close, tone = PillTone.Ghost, onClick = onCancel)
            Spacer(Modifier.weight(1f))
            PrimaryButton("合并进账本", onConfirm)
        }
    }
}

@Composable
private fun PreviewStat(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalPotColors.current
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = colors.ink3)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MoneyStyle.copy(fontSize = 17.sp), color = colors.ink)
    }
}
