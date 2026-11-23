package cn.neeto.stockpot.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.util.Documents
import cn.neeto.stockpot.ui.screens.EntriesScreen
import cn.neeto.stockpot.ui.screens.EntryEditorSheet
import cn.neeto.stockpot.ui.screens.ImportScreen
import cn.neeto.stockpot.ui.screens.OverviewScreen
import cn.neeto.stockpot.ui.screens.ProjectDetailScreen
import cn.neeto.stockpot.ui.screens.ProjectsScreen
import cn.neeto.stockpot.ui.screens.SettingsScreen
import cn.neeto.stockpot.ui.screens.WalletSheet
import cn.neeto.stockpot.ui.theme.LocalPotColors
import kotlinx.coroutines.delay

private data class PotTab(val id: String, val label: String, val icon: ImageVector, val hint: String)

private val tabs = listOf(
    PotTab("overview", "总览", Icons.Outlined.PieChart, "本地账本 · 数据只在这台手机上"),
    PotTab("entries", "流水", Icons.Outlined.ReceiptLong, "每一笔都可以改，也可以删"),
    PotTab("projects", "项目", Icons.Outlined.FolderOpen, "按一件事看花销，不改账目本身"),
    PotTab("settings", "设置", Icons.Outlined.Settings, "外观、分类、规则、导入与备份"),
)

/** 导入不再是 tab，变成一个可以从设置 / 空状态进入的页面。 */
private const val ROUTE_IMPORT = "import"
private const val PROJECT_PREFIX = "project/"

private fun tabIndex(id: String): Int = tabs.indexOfFirst { it.id == id }.let { if (it < 0) 0 else it }

/** 切 tab 时决定从哪边滑进来：右边的往左推，左边的往右退。 */
private fun routeRank(route: String): Int = when {
    route == ROUTE_IMPORT || route.startsWith(PROJECT_PREFIX) -> tabs.size
    else -> tabIndex(route)
}

@Composable
fun PotRoot(
    vm: PotViewModel,
    incomingFile: Documents.Picked?,
    onIncomingConsumed: () -> Unit,
) {
    val colors = LocalPotColors.current
    val ledger by vm.ledger.collectAsStateWithLifecycle()

    var route by rememberSaveable { mutableStateOf("overview") }
    var lastTab by rememberSaveable { mutableStateOf("overview") }
    var entriesMonth by rememberSaveable { mutableStateOf(Fmt.currentMonth()) }
    var entriesDate by rememberSaveable { mutableStateOf<String?>(null) }

    var editorOpen by remember { mutableStateOf(false) }
    var editorEntry by remember { mutableStateOf<Entry?>(null) }
    var editorDate by rememberSaveable { mutableStateOf(Fmt.today()) }
    var showWallets by remember { mutableStateOf(false) }

    var undoEntry by remember { mutableStateOf<Entry?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    val onTab = route == ROUTE_IMPORT || route.startsWith(PROJECT_PREFIX)
    val activeTab = tabs.firstOrNull { it.id == route } ?: tabs.first { it.id == lastTab }
    val subtitle = when {
        route == ROUTE_IMPORT -> "微信 / 支付宝 / iCost / 通用 CSV"
        route.startsWith(PROJECT_PREFIX) -> "按一件事看花销，不改账目本身"
        else -> activeTab.hint
    }

    LaunchedEffect(undoEntry) {
        if (undoEntry != null) {
            delay(3_600)
            undoEntry = null
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2_200)
            toast = null
        }
    }
    LaunchedEffect(incomingFile) {
        if (incomingFile != null) route = ROUTE_IMPORT
    }

    // 导入页与项目详情是「压在上面」的页面，返回键回到上次的 tab
    BackHandler(enabled = onTab) { route = lastTab }

    fun openEditor(entry: Entry?, date: String) {
        editorEntry = entry
        editorDate = date
        editorOpen = true
    }

    fun selectTab(id: String) {
        lastTab = id
        route = id
    }

    val stateHolder = rememberSaveableStateHolder()

    Scaffold(
        containerColor = colors.canvas,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { PotTopBar(subtitle = subtitle) },
        bottomBar = {
            PotBottomBar(
                selected = if (onTab) lastTab else route,
                onSelect = ::selectTab,
            )
        },
        floatingActionButton = {
            if (!onTab && route != "settings") {
                FloatingActionButton(
                    onClick = { openEditor(null, Fmt.today()) },
                    containerColor = colors.brand,
                    contentColor = colors.onBrand,
                    shape = RoundedCornerShape(18.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "记一笔", modifier = Modifier.size(23.dp))
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AnimatedContent(
                targetState = route,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val forward = routeRank(targetState) >= routeRank(initialState)
                    val enter = slideInHorizontally(
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                    ) { width -> if (forward) width / 8 else -width / 8 } + fadeIn(tween(180))
                    val exit = slideOutHorizontally(
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                    ) { width -> if (forward) -width / 10 else width / 10 } + fadeOut(tween(150))
                    enter togetherWith exit
                },
                label = "tab",
            ) { current ->
                stateHolder.SaveableStateProvider(current) {
                    when {
                        current == "overview" -> OverviewScreen(
                            ledger = ledger,
                            onOpenEntry = { openEditor(it, it.date) },
                            onOpenDay = { date ->
                                entriesDate = date
                                entriesMonth = Fmt.monthOf(date)
                                selectTab("entries")
                            },
                            onOpenMonth = { month ->
                                entriesDate = null
                                entriesMonth = month
                                selectTab("entries")
                            },
                            onOpenImport = { route = ROUTE_IMPORT },
                            onOpenWallets = { showWallets = true },
                            onAddEntry = { openEditor(null, Fmt.today()) },
                            onLoadDemo = {
                                vm.loadDemo()
                                toast = "示例账本已载入"
                            },
                        )

                        current == "entries" -> EntriesScreen(
                            ledger = ledger,
                            initialMonth = entriesMonth,
                            initialDate = entriesDate,
                            onOpenEntry = { openEditor(it, it.date) },
                            onOpenImport = { route = ROUTE_IMPORT },
                        )

                        current == "projects" -> ProjectsScreen(
                            ledger = ledger,
                            onOpenProject = { id -> route = PROJECT_PREFIX + id },
                            onCreate = { name, note, start, end ->
                                vm.createProject(name, note, start, end)
                                toast = "项目已创建"
                            },
                        )

                        current == "settings" -> SettingsScreen(
                            vm = vm,
                            onToast = { toast = it },
                            onOpenImport = { route = ROUTE_IMPORT },
                        )

                        current == ROUTE_IMPORT -> ImportScreen(
                            vm = vm,
                            incoming = incomingFile,
                            onIncomingConsumed = onIncomingConsumed,
                            onBack = { route = lastTab },
                        )

                        current.startsWith(PROJECT_PREFIX) -> ProjectDetailScreen(
                            ledger = ledger,
                            projectId = current.removePrefix(PROJECT_PREFIX),
                            onBack = { route = lastTab },
                            onOpenEntry = { openEditor(it, it.date) },
                            onUpdate = vm::updateProject,
                            onDelete = vm::deleteProject,
                            onAttach = vm::attach,
                            onDetach = vm::detach,
                        )
                    }
                }
            }

            // 撤销条与轻提示都浮在底部栏上方，不挤占列表
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                    .padding(bottom = 4.dp),
            ) {
                val pending = undoEntry
                androidx.compose.animation.AnimatedVisibility(
                    visible = pending != null,
                    enter = slideInVertically(tween(180)) { it } + fadeIn(tween(120)),
                    exit = slideOutVertically(tween(140)) { it } + fadeOut(tween(100)),
                ) {
                    Surface(
                        color = colors.ink,
                        contentColor = colors.panel,
                        shape = RoundedCornerShape(14.dp),
                        shadowElevation = 6.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("账目已删除", fontSize = 12.sp, color = colors.panel)
                            Spacer(Modifier.width(6.dp))
                            TextButton(
                                onClick = {
                                    val entry = undoEntry
                                    if (entry != null) vm.restoreEntry(entry)
                                    undoEntry = null
                                },
                            ) {
                                Text("撤销", fontSize = 12.sp, color = colors.brand, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                val notice = toast
                androidx.compose.animation.AnimatedVisibility(
                    visible = notice != null && pending == null,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(150)),
                ) {
                    Surface(
                        color = colors.ink,
                        contentColor = colors.panel,
                        shape = RoundedCornerShape(14.dp),
                        shadowElevation = 6.dp,
                        onClick = { toast = null },
                    ) {
                        Text(
                            text = notice.orEmpty(),
                            fontSize = 12.sp,
                            color = colors.panel,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }

    if (editorOpen) {
        EntryEditorSheet(
            ledger = ledger,
            existing = editorEntry,
            initialDate = editorDate,
            onDismiss = {
                editorOpen = false
                editorEntry = null
            },
            onSave = {
                vm.addEntry(it)
                toast = "已记下"
            },
            onUpdate = {
                vm.saveEntry(it)
                toast = "已更新"
            },
            onDelete = { entry ->
                undoEntry = entry
                vm.deleteEntry(entry.id)
            },
            onAddCategory = { name -> vm.addCategory(name, "", "") },
        )
    }

    if (showWallets) {
        WalletSheet(
            ledger = ledger,
            onDismiss = { showWallets = false },
            onAdd = vm::addWallet,
            onUpdate = vm::updateWallet,
            onDelete = vm::deleteWallet,
        )
    }
}

@Composable
private fun PotTopBar(subtitle: String) {
    val colors = LocalPotColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.canvas)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(colors.brand),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Stockpot",
                style = MaterialTheme.typography.titleLarge,
                color = colors.ink,
                letterSpacing = (-0.3).sp,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
        )
    }
}

@Composable
private fun PotBottomBar(selected: String, onSelect: (String) -> Unit) {
    val colors = LocalPotColors.current
    Column(Modifier.fillMaxWidth().background(colors.panel)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEach { item ->
                val active = item.id == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(item.id) }
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = if (active) colors.brandInk else colors.ink3,
                        modifier = Modifier.size(21.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.label,
                        fontSize = 10.sp,
                        color = if (active) colors.brandInk else colors.ink3,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
