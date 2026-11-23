package cn.neeto.stockpot.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.outlined.AccountBalanceWallet
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
import cn.neeto.stockpot.ui.screens.AssetsScreen
import cn.neeto.stockpot.ui.screens.EntriesScreen
import cn.neeto.stockpot.ui.screens.EntryEditorSheet
import cn.neeto.stockpot.ui.screens.ImportScreen
import cn.neeto.stockpot.ui.screens.OverviewScreen
import cn.neeto.stockpot.ui.screens.ProjectDetailScreen
import cn.neeto.stockpot.ui.screens.ProjectsScreen
import cn.neeto.stockpot.ui.screens.SettingsScreen
import cn.neeto.stockpot.ui.theme.LocalPotColors
import kotlinx.coroutines.delay

private data class PotTab(val id: String, val label: String, val icon: ImageVector, val hint: String)

private val tabs = listOf(
    PotTab("overview", "总览", Icons.Outlined.PieChart, "本地账本 · 数据只在这台手机上"),
    PotTab("entries", "流水", Icons.Outlined.ReceiptLong, "每一笔都可以改，也可以删"),
    PotTab("assets", "资产", Icons.Outlined.AccountBalanceWallet, "账户余额与净资产，余额自己维护"),
    PotTab("settings", "设置", Icons.Outlined.Settings, "外观、分类、规则、导入与备份"),
)

/** 压在 tab 之上的页面：导入、项目列表、项目详情。 */
private const val ROUTE_IMPORT = "import"
private const val ROUTE_PROJECTS = "projects"
private const val PROJECT_PREFIX = "project/"

private fun tabIndex(id: String): Int = tabs.indexOfFirst { it.id == id }.let { if (it < 0) 0 else it }

/**
 * 谁在谁上面。tab 之间比序号，叠加页统一排在所有 tab 后面，
 * 项目详情再压在项目列表上面，这样返回时动画方向不会反。
 */
private fun routeOrder(route: String): Int = when {
    route.startsWith(PROJECT_PREFIX) -> 100
    route == ROUTE_PROJECTS || route == ROUTE_IMPORT -> 90
    else -> tabIndex(route)
}

private fun isOverlay(route: String): Boolean =
    route == ROUTE_IMPORT || route == ROUTE_PROJECTS || route.startsWith(PROJECT_PREFIX)

/** tab 访问轨迹，用来接管返回键：去哪儿的顺序都记着，返回就一步步退。 */
private const val TRAIL_SEP = "|"

private fun decodeTrail(encoded: String): List<String> =
    encoded.split(TRAIL_SEP).filter { it.isNotEmpty() }.ifEmpty { listOf("overview") }

private fun encodeTrail(trail: List<String>): String = trail.joinToString(TRAIL_SEP)

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
    /** 依次点过哪些 tab，返回键按这个轨迹倒退。 */
    var tabTrail by rememberSaveable { mutableStateOf("overview") }
    /** 叠加页是从哪儿来的，返回时回到那一页（可能是另一个叠加页）。 */
    var parentRoute by rememberSaveable { mutableStateOf<String?>(null) }
    // 流水页的月份与日期筛选提到这里：从总览点柱状图进来时带一次参数，
    // 用户关掉之后就是关掉了，切 tab 回来不该再冒出来。
    var entriesMonth by rememberSaveable { mutableStateOf(Fmt.currentMonth()) }
    var entriesDate by rememberSaveable { mutableStateOf<String?>(null) }

    var editorOpen by remember { mutableStateOf(false) }
    var editorEntry by remember { mutableStateOf<Entry?>(null) }
    var editorDate by rememberSaveable { mutableStateOf(Fmt.today()) }

    var undoEntry by remember { mutableStateOf<Entry?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    val overlay = isOverlay(route)
    val activeTab = tabs.firstOrNull { it.id == route } ?: tabs.first { it.id == lastTab }
    val subtitle = when {
        route == ROUTE_IMPORT -> "微信 / 支付宝 / iCost / 通用 CSV"
        route == ROUTE_PROJECTS -> "按一件事看花销，不改账目本身"
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
        if (incomingFile != null && !route.startsWith(ROUTE_IMPORT)) {
            parentRoute = route
            route = ROUTE_IMPORT
        }
    }

    fun openOverlay(target: String) {
        parentRoute = route
        route = target
    }

    fun popOverlay() {
        route = parentRoute ?: lastTab
        parentRoute = null
    }

    fun openProject(id: String) = openOverlay(PROJECT_PREFIX + id)

    val trail = remember(tabTrail) { decodeTrail(tabTrail) }

    // 叠加页之间也有层级：项目详情返回项目列表，项目列表返回原 tab；
    // 退回 tab 之后继续按返回，就按访问轨迹一层层往回走。
    BackHandler(enabled = overlay || trail.size > 1) {
        if (overlay) {
            popOverlay()
        } else {
            val next = trail.dropLast(1)
            val target = next.last()
            tabTrail = encodeTrail(next)
            lastTab = target
            route = target
        }
    }

    fun openEditor(entry: Entry?, date: String) {
        editorEntry = entry
        editorDate = date
        editorOpen = true
    }

    fun selectTab(id: String) {
        if (route == id) return
        parentRoute = null
        lastTab = id
        route = id
        // 回到轨迹里已经出现过的 tab，就把它后面的记录丢掉，跟浏览器的后退一致
        val current = decodeTrail(tabTrail)
        val existing = current.indexOf(id)
        tabTrail = encodeTrail(if (existing >= 0) current.take(existing + 1) else current + id)
    }

    val stateHolder = rememberSaveableStateHolder()

    Scaffold(
        containerColor = colors.canvas,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { PotTopBar(subtitle = subtitle) },
        bottomBar = {
            PotBottomBar(
                selected = if (overlay) lastTab else route,
                onSelect = ::selectTab,
            )
        },
        floatingActionButton = {
            // 资产页的「添加账户」在页面里，记账按钮只出现在账目相关页面
            if (!overlay && route != "settings" && route != "assets") {
                FloatingActionButton(
                    onClick = { openEditor(null, Fmt.today()) },
                    containerColor = colors.brand,
                    contentColor = colors.onBrand,
                    shape = RoundedCornerShape(18.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "记一笔", modifier = Modifier.size(24.dp))
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
                    val forward = routeOrder(targetState) >= routeOrder(initialState)
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
                            onOpenImport = { openOverlay(ROUTE_IMPORT) },
                            onOpenProjects = { openOverlay(ROUTE_PROJECTS) },
                            onOpenProject = { openProject(it) },
                            onAddEntry = { openEditor(null, Fmt.today()) },
                            onLoadDemo = {
                                vm.loadDemo()
                                toast = "示例账本已载入"
                            },
                        )

                        current == "entries" -> EntriesScreen(
                            ledger = ledger,
                            month = entriesMonth,
                            dayFilter = entriesDate,
                            onMonth = { entriesMonth = it },
                            onDayFilter = { entriesDate = it },
                            onOpenEntry = { openEditor(it, it.date) },
                            onOpenImport = { openOverlay(ROUTE_IMPORT) },
                        )

                        current == "assets" -> AssetsScreen(
                            ledger = ledger,
                            onAdd = vm::addWallet,
                            onUpdate = vm::updateWallet,
                            onDelete = vm::deleteWallet,
                        )

                        current == "settings" -> SettingsScreen(
                            vm = vm,
                            onToast = { toast = it },
                            onOpenImport = { openOverlay(ROUTE_IMPORT) },
                        )

                        current == ROUTE_PROJECTS -> ProjectsScreen(
                            ledger = ledger,
                            onBack = { popOverlay() },
                            onOpenProject = { openProject(it) },
                            onCreate = { name, note, start, end ->
                                vm.createProject(name, note, start, end)
                                toast = "项目已创建"
                            },
                        )

                        current == ROUTE_IMPORT -> ImportScreen(
                            vm = vm,
                            incoming = incomingFile,
                            onIncomingConsumed = onIncomingConsumed,
                            onBack = { popOverlay() },
                        )

                        current.startsWith(PROJECT_PREFIX) -> ProjectDetailScreen(
                            ledger = ledger,
                            projectId = current.removePrefix(PROJECT_PREFIX),
                            onBack = { popOverlay() },
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
                AnimatedVisibility(
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
                            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("账目已删除", fontSize = 14.sp, color = colors.panel)
                            Spacer(Modifier.width(6.dp))
                            TextButton(
                                onClick = {
                                    val entry = undoEntry
                                    if (entry != null) vm.restoreEntry(entry)
                                    undoEntry = null
                                },
                            ) {
                                Text("撤销", fontSize = 14.sp, color = colors.brand, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                val notice = toast
                AnimatedVisibility(
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
                            fontSize = 14.sp,
                            color = colors.panel,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
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
                .padding(horizontal = 4.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEach { item ->
                val active = item.id == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(13.dp))
                        .clickable { onSelect(item.id) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = if (active) colors.brandInk else colors.ink3,
                        modifier = Modifier.size(23.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.label,
                        fontSize = 12.sp,
                        color = if (active) colors.brandInk else colors.ink3,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
