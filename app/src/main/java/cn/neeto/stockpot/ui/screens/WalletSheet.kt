package cn.neeto.stockpot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.data.LedgerMath
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Wallet
import cn.neeto.stockpot.ui.components.Chip
import cn.neeto.stockpot.ui.components.Hairline
import cn.neeto.stockpot.ui.components.SheetGrabber
import cn.neeto.stockpot.ui.components.SheetHeader
import cn.neeto.stockpot.ui.theme.LocalPotColors
import cn.neeto.stockpot.ui.theme.MoneyStyle

private val walletKinds = listOf(
    "debit" to "储蓄卡",
    "cash" to "现金 / 零钱",
    "credit" to "信用卡",
    "investment" to "投资",
    "pension" to "养老",
    "provident" to "公积金",
    "medical" to "医保",
    "other" to "其他",
)

fun walletKindLabel(kind: String): String =
    walletKinds.firstOrNull { it.first == kind }?.second ?: "其他"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletSheet(
    ledger: Ledger,
    onDismiss: () -> Unit,
    onAdd: (name: String, kind: String, institution: String, balance: Long) -> Unit,
    onUpdate: (Wallet) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = LocalPotColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editing by remember { mutableStateOf<Wallet?>(null) }
    var adding by remember { mutableStateOf(false) }
    val total = LedgerMath.walletsTotal(ledger)
    val net = LedgerMath.allTimeNet(ledger.entries)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.panel,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = { SheetGrabber() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            SheetHeader(
                title = "资产",
                subtitle = "余额自己维护，账本只记录钱的流动",
                onClose = onDismiss,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("账户余额合计", fontSize = 10.sp, color = colors.ink3)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        Fmt.yuan(total),
                        style = MoneyStyle.copy(fontSize = 27.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.ink,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "账本累计净 ${Fmt.yuan(net, signed = true)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                    )
                }
                PrimaryButton("添加账户", onClick = { adding = true })
            }
            Spacer(Modifier.height(14.dp))
            Hairline()
            Spacer(Modifier.height(6.dp))

            if (ledger.wallets.isEmpty()) {
                Text(
                    "还没有账户。添加银行卡、微信零钱或信用卡，就能看到净资产。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(380.dp)) {
                    items(ledger.wallets, key = { it.id }) { wallet ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { editing = wallet }
                                .padding(vertical = 10.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(colors.panel2),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    wallet.name.take(1),
                                    fontSize = 14.sp,
                                    color = colors.ink2,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    wallet.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOf(walletKindLabel(wallet.kind), wallet.institution)
                                        .filter { it.isNotEmpty() }
                                        .joinToString("  ·  "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.ink3,
                                )
                            }
                            Text(
                                Fmt.yuan(wallet.balance),
                                style = MoneyStyle.copy(fontSize = 14.sp),
                                color = if (wallet.balance < 0) colors.expense else colors.ink,
                            )
                            IconButton(onClick = { onDelete(wallet.id) }, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = "删除账户",
                                    tint = colors.ink3,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        WalletEditorDialog(
            wallet = editing,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { name, kind, institution, balance ->
                val target = editing
                if (target == null) {
                    onAdd(name, kind, institution, balance)
                } else {
                    onUpdate(target.copy(name = name, kind = kind, institution = institution, balance = balance))
                }
                adding = false
                editing = null
            },
        )
    }
}

@Composable
private fun WalletEditorDialog(
    wallet: Wallet?,
    onDismiss: () -> Unit,
    onSave: (name: String, kind: String, institution: String, balance: Long) -> Unit,
) {
    val colors = LocalPotColors.current
    var name by remember { mutableStateOf(wallet?.name ?: "") }
    var institution by remember { mutableStateOf(wallet?.institution ?: "") }
    var kind by remember { mutableStateOf(wallet?.kind ?: "debit") }
    var balanceText by remember { mutableStateOf(wallet?.let { Fmt.editYuan(it.balance) } ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.panel,
        title = {
            Text(
                if (wallet == null) "添加账户" else "编辑账户",
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("账户名，如 招商银行", color = colors.ink3, fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = plainFieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = institution,
                    onValueChange = { institution = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("机构，可留空", color = colors.ink3, fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = plainFieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = balanceText,
                    onValueChange = { input ->
                        balanceText = input.filter { it.isDigit() || it == '.' || it == '-' }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("余额，信用卡填负数", color = colors.ink3, fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = plainFieldColors(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    walletKinds.take(3).forEach { (value, label) ->
                        Chip(label = label, active = kind == value, onClick = { kind = value })
                    }
                }
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    walletKinds.drop(3).forEach { (value, label) ->
                        Chip(label = label, active = kind == value, onClick = { kind = value })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val cents = Fmt.parseYuan(balanceText) ?: 0L
                    onSave(name.trim(), kind, institution.trim(), cents)
                },
            ) {
                Text("保存", color = if (name.isNotBlank()) colors.brand else colors.ink3)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = colors.ink2) } },
    )
}

@Composable
private fun plainFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = LocalPotColors.current.panel2,
    unfocusedContainerColor = LocalPotColors.current.panel2,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = LocalPotColors.current.brand,
)
