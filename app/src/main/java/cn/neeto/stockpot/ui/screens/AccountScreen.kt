package cn.neeto.stockpot.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.neeto.stockpot.core.net.LoginOutcome
import cn.neeto.stockpot.core.net.SyncManager
import cn.neeto.stockpot.core.net.TwoFactorSetup
import cn.neeto.stockpot.core.settings.Account
import cn.neeto.stockpot.ui.PotViewModel
import cn.neeto.stockpot.ui.components.Avatar
import cn.neeto.stockpot.ui.components.GhostButton
import cn.neeto.stockpot.ui.components.Hairline
import cn.neeto.stockpot.ui.components.IconOption
import cn.neeto.stockpot.ui.components.IconSegmented
import cn.neeto.stockpot.ui.components.PillTone
import cn.neeto.stockpot.ui.components.PotCard
import cn.neeto.stockpot.ui.components.PotConfirmSheet
import cn.neeto.stockpot.ui.components.PotPillButton
import cn.neeto.stockpot.ui.components.PotSheet
import cn.neeto.stockpot.ui.components.PrimaryButton
import cn.neeto.stockpot.ui.components.QrCode
import cn.neeto.stockpot.ui.components.SectionTitle
import cn.neeto.stockpot.ui.components.SheetActions
import cn.neeto.stockpot.ui.components.SheetField
import cn.neeto.stockpot.ui.components.overlayBottomPadding
import cn.neeto.stockpot.ui.theme.LocalPotColors
import cn.neeto.stockpot.ui.theme.MoneyStyle
import kotlinx.coroutines.launch

/** 这一页上会弹确认的两个动作。 */
private enum class PendingAccountAction { LOGOUT, CLEAR_REMOTE }

/**
 * 账号：登录是可选项，所以这一页不拦人，从设置里点进来才看得见。
 *
 * 没登录时讲清楚「不登录也能用，登录只是多一份备份」，给登录/注册两个口子；
 * 登录之后这里就是「我自己」的控制台：头像、昵称、密码、两步验证，
 * 再往下才是同步和退出——资料在上面，危险动作在最后。
 */
@Composable
fun AccountScreen(
    vm: PotViewModel,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
) {
    val colors = LocalPotColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cloud by vm.cloud.collectAsStateWithLifecycle()
    val avatar by vm.avatar.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<PendingAccountAction?>(null) }
    var editingName by remember { mutableStateOf(false) }
    var editingPassword by remember { mutableStateOf(false) }
    var twoFactorSheet by remember { mutableStateOf(false) }
    var disablingTwoFactor by remember { mutableStateOf(false) }
    var avatarBusy by remember { mutableStateOf(false) }

    // 换了账号、换了头像版本，都重新拉一次；同一版不会重复下载
    LaunchedEffect(cloud.signedIn, cloud.account.id, cloud.account.avatarAt) { vm.loadAvatar() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        avatarBusy = true
        scope.launch {
            val result = vm.setAvatarFromUri(uri)
            avatarBusy = false
            onToast(
                result.fold(
                    onSuccess = { "头像已更新" },
                    onFailure = { it.message ?: "头像没换成，再试一次" },
                ),
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        EditorBar(title = "账号", onBack = onBack, onConfirm = null)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = overlayBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (cloud.signedIn) {
                item {
                    ProfileCard(
                        account = cloud.account,
                        avatar = avatar,
                        busy = avatarBusy,
                        onChangePhoto = {
                            if (!avatarBusy) {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            }
                        },
                        onRemovePhoto = {
                            if (!avatarBusy) {
                                avatarBusy = true
                                scope.launch {
                                    val result = vm.removeAvatar()
                                    avatarBusy = false
                                    onToast(
                                        result.fold(
                                            onSuccess = { "头像已去掉" },
                                            onFailure = { it.message ?: "没去掉，再试一次" },
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }

                item {
                    PotCard(padding = PaddingValues(0.dp)) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp)) {
                            SectionTitle("资料")
                        }
                        AccountRow(
                            title = "昵称",
                            value = cloud.account.name,
                            icon = Icons.Outlined.Person,
                            onClick = { editingName = true },
                        )
                        Hairline(Modifier.padding(start = 16.dp))
                        AccountRow(
                            title = "邮箱",
                            value = cloud.account.email,
                            onClick = null,
                        )
                    }
                }

                item {
                    PotCard(padding = PaddingValues(0.dp)) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp)) {
                            SectionTitle("安全")
                        }
                        AccountRow(
                            title = "修改密码",
                            value = if (cloud.account.twoFactor) "改完两步验证照旧" else "至少 8 位",
                            icon = Icons.Outlined.Lock,
                            onClick = { editingPassword = true },
                        )
                        Hairline(Modifier.padding(start = 16.dp))
                        TwoFactorRow(
                            enabled = cloud.account.twoFactor,
                            onToggle = { wantsOn ->
                                if (wantsOn) twoFactorSheet = true else disablingTwoFactor = true
                            },
                        )
                    }
                }

                item {
                    PotCard {
                        SectionTitle("这台设备")
                        Spacer(Modifier.height(6.dp))
                        InfoRow("服务器", vm.serverUrl)
                        InfoRow(
                            "上次同步",
                            if (cloud.status == SyncManager.Status.SYNCING) "正在同步…" else syncTimeText(cloud.lastSync),
                        )
                        InfoRow(
                            "本机状态",
                            if (vm.cloudDirty) "有改动还没推上去" else "和云端一致",
                        )
                        if (cloud.status == SyncManager.Status.ERROR) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "同步失败：${cloud.error ?: "网络不通"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.danger,
                                lineHeight = 17.sp,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton(
                            text = if (cloud.status == SyncManager.Status.SYNCING) "同步中…" else "立即同步",
                            onClick = {
                                if (cloud.status == SyncManager.Status.SYNCING) return@PrimaryButton
                                scope.launch {
                                    val ok = vm.cloudSyncNow()
                                    onToast(if (ok) "已对齐" else "这次没同步上，稍后再试")
                                }
                            },
                        )
                    }
                }

                item {
                    PotCard(padding = PaddingValues(0.dp)) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
                            SectionTitle("这台服务器")
                        }
                        AccountActionRow(
                            title = "清空云端备份",
                            subtitle = "本机账本不动，只是把服务器上那份清掉",
                            danger = true,
                            onClick = { pending = PendingAccountAction.CLEAR_REMOTE },
                        )
                        Hairline(Modifier.padding(start = 16.dp))
                        AccountActionRow(
                            title = "退出登录",
                            subtitle = "账本留在本机，之后照常记账",
                            onClick = { pending = PendingAccountAction.LOGOUT },
                        )
                    }
                }
            } else {
                item {
                    PotCard {
                        StatusHeader(ui = cloud)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "不登录也完全能用，账本只在这台手机上。登录之后会整份上传到自己的服务器，" +
                                "换手机、重装应用都能拿回来——数据不经过任何第三方。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.ink3,
                            lineHeight = 20.sp,
                        )
                    }
                }
                item {
                    LoginForm(
                        serverUrl = vm.serverUrl,
                        onSetServerUrl = vm::setServerUrl,
                        onLogin = vm::cloudLogin,
                        onTwoFactor = vm::cloudLoginTwoFactor,
                        onRegister = vm::cloudRegister,
                        onDone = { onToast("已登录，正在同步") },
                    )
                }
            }
        }
    }

    if (editingName) {
        NameSheet(
            initial = cloud.account.name,
            onDismiss = { editingName = false },
            onSave = { name -> vm.updateName(name) },
            onSaved = {
                editingName = false
                onToast("昵称已更新")
            },
        )
    }

    if (editingPassword) {
        PasswordSheet(
            onDismiss = { editingPassword = false },
            onChange = { current, next -> vm.changePassword(current, next) },
            onSaved = {
                editingPassword = false
                onToast("密码已更新")
            },
        )
    }

    if (twoFactorSheet) {
        TwoFactorSetupSheet(
            onDismiss = { twoFactorSheet = false },
            onSetup = { vm.setupTwoFactor() },
            onEnable = { code -> vm.enableTwoFactor(code) },
            onDone = {
                twoFactorSheet = false
                onToast("两步验证已开启")
            },
        )
    }

    if (disablingTwoFactor) {
        TwoFactorDisableSheet(
            onDismiss = { disablingTwoFactor = false },
            onDisable = { password -> vm.disableTwoFactor(password) },
            onDone = {
                disablingTwoFactor = false
                onToast("两步验证已关闭")
            },
        )
    }

    val action = pending
    if (action != null) {
        val title: String
        val body: String
        val confirmText: String
        when (action) {
            PendingAccountAction.LOGOUT -> {
                title = "退出登录？"
                body = "只是这台手机不再连着账号，账本留在本机，之后照常记账。"
                confirmText = "退出登录"
            }
            PendingAccountAction.CLEAR_REMOTE -> {
                title = "清空云端备份？"
                body = "服务器上那份账本会被清掉，本机账本不受影响。这一步不能撤销。"
                confirmText = "清空云端"
            }
        }
        PotConfirmSheet(
            title = title,
            body = body,
            confirmText = confirmText,
            onDismiss = { pending = null },
            onConfirm = {
                pending = null
                scope.launch {
                    when (action) {
                        PendingAccountAction.LOGOUT -> {
                            vm.cloudLogout()
                            onToast("已退出登录")
                        }
                        PendingAccountAction.CLEAR_REMOTE -> {
                            val result = vm.cloudClearRemote()
                            onToast(
                                result.fold(
                                    onSuccess = { "云端已清空" },
                                    onFailure = { "没清成：${it.message ?: "网络不通"}" },
                                ),
                            )
                        }
                    }
                }
            },
        )
    }
}

/* ------------------------------ 头像 + 名字 ------------------------------ */

@Composable
private fun ProfileCard(
    account: Account,
    avatar: android.graphics.Bitmap?,
    busy: Boolean,
    onChangePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
) {
    val colors = LocalPotColors.current
    PotCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(bitmap = avatar, initial = account.initial, size = 64.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    account.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    account.email,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (account.twoFactor) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(colors.brandTint)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Security,
                            contentDescription = null,
                            tint = colors.brandInk,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("两步验证已开", fontSize = 11.sp, color = colors.brandInk)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PotPillButton(
                text = if (busy) "处理中…" else "换头像",
                icon = Icons.Outlined.PhotoCamera,
                enabled = !busy,
                onClick = onChangePhoto,
            )
            if (account.hasAvatar) {
                PotPillButton(
                    text = "去掉头像",
                    icon = Icons.Outlined.DeleteOutline,
                    tone = PillTone.Ghost,
                    enabled = !busy,
                    onClick = onRemovePhoto,
                )
            }
        }
    }
}

/* -------------------------------- 资料行 -------------------------------- */

@Composable
private fun AccountRow(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: (() -> Unit)?,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.brandTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = colors.brandInk, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
        Spacer(Modifier.width(12.dp))
        Text(
            value.ifBlank { "未设置" },
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.ink3,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun TwoFactorRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.brandTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Security,
                contentDescription = null,
                tint = colors.brandInk,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("两步验证", style = MaterialTheme.typography.bodyMedium, color = colors.ink)
            Spacer(Modifier.height(3.dp))
            Text(
                if (enabled) {
                    "登录时要在认证器里再输一次六位数，密码被人知道了也进不来。"
                } else {
                    "用手机上的认证器（Google Authenticator、1Password 等）多守一道门。"
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
                lineHeight = 17.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

/* -------------------------------- 弹层 -------------------------------- */

/** 改昵称。 */
@Composable
private fun NameSheet(
    initial: String,
    onDismiss: () -> Unit,
    onSave: suspend (String) -> Result<Unit>,
    onSaved: () -> Unit,
) {
    val colors = LocalPotColors.current
    val scope = rememberCoroutineScope()
    var name by remember(initial) { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    PotSheet(onDismiss = onDismiss) {
        Text("改昵称", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "昵称只影响显示，登录还是用邮箱。最多 24 个字。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(14.dp))
        SheetField(
            label = "昵称",
            value = name,
            placeholder = "怎么称呼你",
            onValueChange = {
                name = it.take(24)
                error = null
            },
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = colors.danger)
        }
        Spacer(Modifier.height(14.dp))
        SheetActions(
            confirmText = if (busy) "保存中…" else "保存",
            enabled = !busy && name.isNotBlank() && name.trim() != initial,
            onCancel = onDismiss,
            onConfirm = {
                if (busy) return@SheetActions
                busy = true
                scope.launch {
                    val result = onSave(name.trim())
                    busy = false
                    result.fold(onSuccess = { onSaved() }, onFailure = { error = it.message ?: "没保存上" })
                }
            },
        )
    }
}

/** 改密码：三个框，前端先自己对一遍，省一次往返。 */
@Composable
private fun PasswordSheet(
    onDismiss: () -> Unit,
    onChange: suspend (String, String) -> Result<Unit>,
    onSaved: () -> Unit,
) {
    val colors = LocalPotColors.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    PotSheet(onDismiss = onDismiss) {
        Text("修改密码", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "改完这台手机不用重新登录，别的设备下次登录用新密码。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(14.dp))
        SheetField(
            label = "当前密码",
            value = current,
            placeholder = "现在用的密码",
            onValueChange = {
                current = it
                error = null
            },
            keyboardType = KeyboardType.Password,
            password = true,
        )
        Spacer(Modifier.height(10.dp))
        SheetField(
            label = "新密码",
            value = next,
            placeholder = "至少 8 位",
            onValueChange = {
                next = it
                error = null
            },
            keyboardType = KeyboardType.Password,
            password = true,
        )
        Spacer(Modifier.height(10.dp))
        SheetField(
            label = "再输一次",
            value = repeat,
            placeholder = "和新密码一致",
            onValueChange = {
                repeat = it
                error = null
            },
            keyboardType = KeyboardType.Password,
            password = true,
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = colors.danger)
        }
        Spacer(Modifier.height(14.dp))
        SheetActions(
            confirmText = if (busy) "提交中…" else "改密码",
            enabled = !busy && current.isNotEmpty() && next.length >= 8 && repeat.isNotEmpty(),
            onCancel = onDismiss,
            onConfirm = {
                if (busy) return@SheetActions
                if (next != repeat) {
                    error = "两次输入的新密码不一样"
                    return@SheetActions
                }
                busy = true
                scope.launch {
                    val result = onChange(current, next)
                    busy = false
                    result.fold(onSuccess = { onSaved() }, onFailure = { error = it.message ?: "没改成" })
                }
            },
        )
    }
}

/**
 * 开启两步验证：扫码 → 输六位数 → 收好恢复码。
 *
 * 三步都在同一个弹层里，中间可以退出去：没确认之前服务端只是「待启用」，
 * 手机丢了、码扫不上都不会把账号锁在门外。
 */
@Composable
private fun TwoFactorSetupSheet(
    onDismiss: () -> Unit,
    onSetup: suspend () -> Result<TwoFactorSetup>,
    onEnable: suspend (String) -> Result<List<String>>,
    onDone: () -> Unit,
) {
    val colors = LocalPotColors.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var setup by remember { mutableStateOf<TwoFactorSetup?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var recovery by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(Unit) {
        onSetup().fold(
            onSuccess = { setup = it },
            onFailure = { loadError = it.message ?: "拿不到密钥，稍后再试" },
        )
    }

    PotSheet(onDismiss = onDismiss) {
        val saved = recovery
        if (saved != null) {
            // 第三步：恢复码只露这一次
            Text("把恢复码存好", style = MaterialTheme.typography.titleLarge, color = colors.ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "手机丢了、换手机时用得上，每个只能用一次。离开这一页就看不到了。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink3,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.panel2)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                saved.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth()) {
                        pair.forEach { item ->
                            Text(
                                item,
                                style = MoneyStyle,
                                fontSize = 15.sp,
                                color = colors.ink,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PotPillButton(
                    text = "复制全部",
                    icon = Icons.Outlined.ContentCopy,
                    tone = PillTone.Ghost,
                    onClick = { clipboard.setText(AnnotatedString(saved.joinToString("\n"))) },
                )
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton(text = "我存好了", onClick = onDone)
            return@PotSheet
        }

        Text("开启两步验证", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "用认证器扫下面这个码，再把手机上显示的六位数填进来。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
            lineHeight = 19.sp,
        )

        val data = setup
        when {
            data == null && loadError == null -> {
                Spacer(Modifier.height(24.dp))
                Text(
                    "正在生成密钥…",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
            }
            data == null -> {
                Spacer(Modifier.height(12.dp))
                Text(
                    loadError.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.danger,
                )
                Spacer(Modifier.height(14.dp))
                SheetActions(
                    confirmText = "重试",
                    enabled = true,
                    onCancel = onDismiss,
                    onConfirm = {
                        loadError = null
                        scope.launch {
                            onSetup().fold(
                                onSuccess = { setup = it },
                                onFailure = { loadError = it.message ?: "还是拿不到" },
                            )
                        }
                    },
                )
            }
            else -> {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    QrCode(matrix = data.qr, size = 216.dp)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "扫不上就手动输入这串密钥：",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        data.secret,
                        style = MoneyStyle,
                        fontSize = 12.sp,
                        color = colors.ink2,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    PotPillButton(
                        text = "复制",
                        icon = Icons.Outlined.ContentCopy,
                        tone = PillTone.Ghost,
                        onClick = { clipboard.setText(AnnotatedString(data.secret)) },
                    )
                }
                Spacer(Modifier.height(14.dp))
                SheetField(
                    label = "认证器上的六位数",
                    value = code,
                    placeholder = "123456",
                    onValueChange = {
                        code = it.filter(Char::isDigit).take(6)
                        error = null
                    },
                    keyboardType = KeyboardType.Number,
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = colors.danger)
                }
                Spacer(Modifier.height(14.dp))
                SheetActions(
                    confirmText = if (busy) "校验中…" else "确认开启",
                    enabled = !busy && code.length == 6,
                    onCancel = onDismiss,
                    onConfirm = {
                        if (busy) return@SheetActions
                        busy = true
                        scope.launch {
                            val result = onEnable(code)
                            busy = false
                            result.fold(
                                onSuccess = { recovery = it },
                                onFailure = { error = it.message ?: "验证码不对" },
                            )
                        }
                    },
                )
            }
        }
    }
}

/** 关闭两步验证：要密码，不然拿到手机的人一把就能关掉。 */
@Composable
private fun TwoFactorDisableSheet(
    onDismiss: () -> Unit,
    onDisable: suspend (String) -> Result<Unit>,
    onDone: () -> Unit,
) {
    val colors = LocalPotColors.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    PotSheet(onDismiss = onDismiss) {
        Text("关闭两步验证", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "关掉之后，只凭密码就能登录。确认一下是本人操作。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(14.dp))
        SheetField(
            label = "登录密码",
            value = password,
            placeholder = "输入密码确认",
            onValueChange = {
                password = it
                error = null
            },
            keyboardType = KeyboardType.Password,
            password = true,
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = colors.danger)
        }
        Spacer(Modifier.height(14.dp))
        SheetActions(
            confirmText = if (busy) "处理中…" else "关闭",
            enabled = !busy && password.isNotEmpty(),
            danger = true,
            onCancel = onDismiss,
            onConfirm = {
                if (busy) return@SheetActions
                busy = true
                scope.launch {
                    val result = onDisable(password)
                    busy = false
                    result.fold(onSuccess = { onDone() }, onFailure = { error = it.message ?: "没关成" })
                }
            },
        )
    }
}

/* ------------------------------ 未登录 ------------------------------ */

@Composable
private fun StatusHeader(ui: SyncManager.SyncUi) {
    val colors = LocalPotColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (ui.signedIn) colors.brandTint else colors.panel2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (ui.signedIn) Icons.Outlined.CloudQueue else Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = if (ui.signedIn) colors.brandInk else colors.ink3,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (ui.signedIn) "已登录" else "没有登录",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (ui.signedIn) ui.account.email else "账本只在这台手机上",
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (ui.signedIn) {
            val text = when (ui.status) {
                SyncManager.Status.SYNCING -> "同步中"
                SyncManager.Status.ERROR -> "同步失败"
                SyncManager.Status.OK -> "已同步"
                SyncManager.Status.IDLE -> ""
            }
            if (text.isNotEmpty()) {
                val tint = when (ui.status) {
                    SyncManager.Status.ERROR -> colors.danger
                    SyncManager.Status.OK -> colors.success
                    else -> colors.brandInk
                }
                Text(text, style = MaterialTheme.typography.labelSmall, color = tint)
            }
        }
    }
}

/** 登录 / 注册：同一个表单，注册时多一个昵称，按钮换个字。开了两步验证会多一步。 */
@Composable
private fun LoginForm(
    serverUrl: String,
    onSetServerUrl: (String) -> Unit,
    onLogin: suspend (String, String) -> Result<LoginOutcome>,
    onTwoFactor: suspend (String, String) -> Result<Unit>,
    onRegister: suspend (String, String, String) -> Result<Unit>,
    onDone: () -> Unit,
) {
    val colors = LocalPotColors.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(MODE_LOGIN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var server by remember(serverUrl) { mutableStateOf(serverUrl) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var ticket by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }

    val askingCode = ticket != null

    PotCard {
        SectionTitle(
            when {
                askingCode -> "两步验证"
                mode == MODE_LOGIN -> "登录自己的服务器"
                else -> "建一个账号"
            },
        )
        Spacer(Modifier.height(8.dp))

        if (askingCode) {
            Text(
                "这个账号开着两步验证。输入认证器上的六位数，或者一个还没用过的恢复码。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink3,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(12.dp))
            SheetField(
                label = "验证码",
                value = code,
                placeholder = "123456 或 7F2K-9XQ3",
                onValueChange = {
                    code = it.take(9)
                    error = null
                },
                keyboardType = KeyboardType.Text,
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = colors.danger)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(
                    text = "换个账号",
                    icon = Icons.Outlined.Login,
                    onClick = {
                        ticket = null
                        code = ""
                        error = null
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                text = if (busy) "校验中…" else "验证并登录",
                icon = Icons.Outlined.Security,
                onClick = {
                    if (busy || code.isBlank()) return@PrimaryButton
                    val current = ticket ?: return@PrimaryButton
                    busy = true
                    error = null
                    scope.launch {
                        val result = onTwoFactor(current, code.trim())
                        busy = false
                        result.onFailure { error = it.message ?: "验证码不对" }
                        result.onSuccess { onDone() }
                    }
                },
            )
            return@PotCard
        }

        IconSegmented(
            options = listOf(
                IconOption(MODE_LOGIN, "登录", Icons.Outlined.Login),
                IconOption(MODE_REGISTER, "注册", Icons.Outlined.PersonAdd),
            ),
            selected = mode,
            onSelect = {
                mode = it
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        SheetField(
            label = "邮箱",
            value = email,
            placeholder = "you@example.com",
            onValueChange = { email = it },
            keyboardType = KeyboardType.Email,
        )
        Spacer(Modifier.height(10.dp))
        SheetField(
            label = "密码",
            value = password,
            placeholder = if (mode == MODE_REGISTER) "至少 8 位" else "登录密码",
            onValueChange = { password = it },
            keyboardType = KeyboardType.Password,
            password = true,
        )
        if (mode == MODE_REGISTER) {
            Spacer(Modifier.height(10.dp))
            SheetField(
                label = "昵称（可不填）",
                value = name,
                placeholder = "tongke",
                onValueChange = { name = it },
            )
        }
        Spacer(Modifier.height(10.dp))
        SheetField(
            label = "同步服务器",
            value = server,
            placeholder = "https://api.example.com",
            onValueChange = {
                server = it
                onSetServerUrl(it)
            },
            keyboardType = KeyboardType.Uri,
        )
        Text(
            "有自己的服务器就改这里，地址要带 https://。",
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
            modifier = Modifier.padding(top = 5.dp),
        )

        val message = error
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.labelSmall, color = colors.danger)
        }

        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            text = when {
                busy -> "请稍等…"
                mode == MODE_LOGIN -> "登录"
                else -> "注册"
            },
            icon = if (mode == MODE_LOGIN) Icons.Outlined.Login else Icons.Outlined.PersonAdd,
            onClick = {
                if (busy) return@PrimaryButton
                if (password.length < 8) {
                    error = "密码至少 8 位"
                    return@PrimaryButton
                }
                busy = true
                error = null
                scope.launch {
                    if (mode == MODE_LOGIN) {
                        val result = onLogin(email, password)
                        busy = false
                        result.fold(
                            onSuccess = { outcome ->
                                when (outcome) {
                                    is LoginOutcome.Done -> onDone()
                                    is LoginOutcome.NeedCode -> {
                                        ticket = outcome.ticket
                                        code = ""
                                    }
                                }
                            },
                            onFailure = { error = it.message ?: "没成功，再试一次" },
                        )
                    } else {
                        val result = onRegister(email, password, name)
                        busy = false
                        result.onFailure { error = it.message ?: "没成功，再试一次" }
                        result.onSuccess { onDone() }
                    }
                }
            },
        )
    }
}

/* -------------------------------- 小零件 -------------------------------- */

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.ink3)
        Spacer(Modifier.width(12.dp))
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AccountActionRow(
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            // 卡片自己没内边距（为了让分隔线通到边上），所以左右这 16dp 得由行来给：
            // 不然标题会顶到卡片边，跟上面那个 SectionTitle 差一个身位
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (danger) colors.danger else colors.ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.ink3)
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier.size(17.dp),
        )
    }
}

/** ISO 时间戳 → 「11 月 23 日 17:40」，跨年也看得懂。账号页和设置页共用。 */
internal fun syncTimeText(iso: String): String {
    if (iso.isBlank()) return "还没同步过"
    val time = runCatching {
        java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault())
    }.getOrNull() ?: return iso
    return "%d 月 %d 日 %02d:%02d".format(time.monthValue, time.dayOfMonth, time.hour, time.minute)
}

private const val MODE_LOGIN = "login"
private const val MODE_REGISTER = "register"
