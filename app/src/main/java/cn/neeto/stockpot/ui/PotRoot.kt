package cn.neeto.stockpot.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.data.StockMath
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Wallet
import cn.neeto.stockpot.core.util.Documents
import cn.neeto.stockpot.ui.components.Haptic
import cn.neeto.stockpot.ui.components.rememberHaptics
import cn.neeto.stockpot.ui.screens.AssetsScreen
import cn.neeto.stockpot.ui.screens.EntriesScreen
import cn.neeto.stockpot.ui.screens.EntryEditorSheet
import cn.neeto.stockpot.ui.screens.ImportScreen
import cn.neeto.stockpot.ui.screens.OverviewScreen
import cn.neeto.stockpot.ui.screens.ProjectDetailScreen
import cn.neeto.stockpot.ui.screens.ProjectsScreen
import cn.neeto.stockpot.ui.screens.SettingsScreen
import cn.neeto.stockpot.ui.screens.StockAccountScreen
import cn.neeto.stockpot.ui.screens.StockDetailScreen
import cn.neeto.stockpot.ui.screens.StocksScreen
import cn.neeto.stockpot.ui.screens.TitleSwapScreen
import cn.neeto.stockpot.ui.screens.WalletEditorScreen
import cn.neeto.stockpot.ui.screens.WalletKindScreen
import cn.neeto.stockpot.ui.theme.LocalPotColors
import kotlinx.coroutines.delay

private data class PotTab(
    val id: String,
    val label: String,
    val icon: ImageVector,
    /** 选中时换成实心图标，跟 BiliPai 一样 */
    val iconActive: ImageVector,
    val hint: String,
)

private val tabs = listOf(
    PotTab("overview", "总览", Icons.Outlined.PieChart, Icons.Filled.PieChart, "本地账本 · 数据只在这台手机上"),
    PotTab("entries", "流水", Icons.Outlined.ReceiptLong, Icons.Filled.ReceiptLong, "每一笔都可以改，也可以删"),
    PotTab("assets", "资产", Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet, "账户余额与净资产，余额自己维护"),
    PotTab("settings", "设置", Icons.Outlined.Settings, Icons.Filled.Settings, "外观、分类、规则、导入与备份"),
)

/** 压在 tab 之上的页面：导入、项目列表、项目详情、账户添加。 */
private const val ROUTE_IMPORT = "import"
private const val ROUTE_PROJECTS = "projects"
private const val PROJECT_PREFIX = "project/"
private const val ROUTE_WALLET_EDIT = "wallet-edit"
private const val ROUTE_WALLET_KIND = "wallet-kind"
private const val ROUTE_STOCKS = "stocks"
private const val ROUTE_TITLE_SWAP = "title-swap"
private const val STOCK_ACC_PREFIX = "stock-acc/"
private const val STOCK_DETAIL_PREFIX = "stock/"

private fun tabIndex(id: String): Int = tabs.indexOfFirst { it.id == id }.let { if (it < 0) 0 else it }

/**
 * 谁在谁上面。tab 之间比序号，叠加页统一排在所有 tab 后面，
 * 项目详情再压在项目列表上面，这样返回时动画方向不会反。
 */
private fun routeOrder(route: String): Int = when {
    route.startsWith(PROJECT_PREFIX) -> 100
    route == ROUTE_WALLET_EDIT -> 98
    route == ROUTE_WALLET_KIND -> 97
    route.startsWith(STOCK_DETAIL_PREFIX) -> 96
    route.startsWith(STOCK_ACC_PREFIX) -> 95
    route == ROUTE_STOCKS -> 94
    route == ROUTE_TITLE_SWAP -> 91
    route == ROUTE_PROJECTS || route == ROUTE_IMPORT -> 90
    else -> tabIndex(route)
}

private fun isOverlay(route: String): Boolean =
    route == ROUTE_IMPORT || route == ROUTE_PROJECTS || route.startsWith(PROJECT_PREFIX) ||
        route == ROUTE_WALLET_EDIT || route == ROUTE_WALLET_KIND ||
        route == ROUTE_STOCKS || route.startsWith(STOCK_ACC_PREFIX) ||
        route.startsWith(STOCK_DETAIL_PREFIX) || route == ROUTE_TITLE_SWAP

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
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val quotes by vm.quotes.collectAsStateWithLifecycle()
    val quoteBusy by vm.quoteBusy.collectAsStateWithLifecycle()
    val quoteNote by vm.quoteNote.collectAsStateWithLifecycle()
    val rates by vm.rates.collectAsStateWithLifecycle()
    val candles by vm.candles.collectAsStateWithLifecycle()
    val minutes by vm.minutes.collectAsStateWithLifecycle()

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

    // 账户编辑的草稿：放在这里，去「选择类型」转一圈回来还能接着填
    var walletDraft by remember { mutableStateOf<Wallet?>(null) }
    var walletIsNew by remember { mutableStateOf(true) }

    var editorOpen by remember { mutableStateOf(false) }
    var editorEntry by remember { mutableStateOf<Entry?>(null) }
    var editorDate by rememberSaveable { mutableStateOf(Fmt.today()) }

    var undoEntry by remember { mutableStateOf<Entry?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    val overlay = isOverlay(route)
    val activeTab = tabs.firstOrNull { it.id == route } ?: tabs.first { it.id == lastTab }
    val stockTotals = remember(ledger.stockAccounts, ledger.stocks, quotes, rates) {
        StockMath.totals(ledger.stockAccounts, ledger.stocks, quotes, rates)
    }
    // 首页那张股票卡要画最近一个月的市值曲线，日 K 拉一次缓存在 VM 里
    val stockTrend = remember(ledger.stockAccounts, ledger.stocks, candles, rates) {
        StockMath.trend(
            accounts = ledger.stockAccounts,
            stocks = ledger.stocks.associateBy { it.id },
            candles = candles,
            rates = rates,
            limit = 30,
        )
    }
    // 顶栏只讲当前这个 tab：进到叠加页时它一边收一边退场，
    // 叠加页自己的返回头上写着页面名，这里就不用再换一套字。
    val subtitle = activeTab.hint

    // 大标题的收起进度（0 = 大标题 + 副标题，1 = 只剩一行小标题），每个页面各记一份：
    // 从别的页面回来还能接着上次的位置，不会突然又撑开。
    val collapses = remember { mutableStateMapOf<String, Float>() }
    val routeNow by rememberUpdatedState(route)
    val collapsePx = with(LocalDensity.current) { HeaderCollapseDistance.toPx() }
    // 用「列表实际滚了多少」来推标题，而不是手指位移：
    // 往下滚就收，往上滚就放回来（两个方向都跟手），内容滚不动时也不会乱缩——
    // 之前只在"到顶后的剩余位移"里展开，可系统的高亮/拉伸效果先把那截吃掉了，
    // 结果收起之后再也长不回去。
    val headerScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (consumed.y == 0f) return Offset.Zero
                // 只记 tab 页的进度。叠加页的顶栏反正已经藏起来了，
                // 记下来会在下次进来时被当成"当前状态"，又冒出一次先撑开再收。
                if (isOverlay(routeNow)) return Offset.Zero
                val key = routeNow
                val now = collapses[key] ?: 0f
                val next = (now - consumed.y / collapsePx).coerceIn(0f, 1f)
                if (next != now) collapses[key] = next
                return Offset.Zero
            }
        }
    }

    // 进到有股票的页面时顺手拉一次行情；失败就安静着，用最后的价格
    LaunchedEffect(ledger.stocks.size) {
        if (ledger.stocks.isNotEmpty()) vm.refreshQuotes(silent = true)
    }

    // 首页、股票相关的页面都需要日 K（画曲线、算走势），只拉一次，之后读缓存
    LaunchedEffect(route, ledger.stocks.size) {
        val stockRoute = route == "overview" || route == ROUTE_STOCKS ||
            route.startsWith(STOCK_ACC_PREFIX) || route.startsWith(STOCK_DETAIL_PREFIX)
        if (stockRoute && ledger.stocks.isNotEmpty()) vm.ensureDaily(ledger.stocks)
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

    /** 传 null 是新增账户（先挑类型），传账户是编辑。 */
    fun openWallet(wallet: Wallet?) {
        walletIsNew = wallet == null
        if (wallet == null) {
            walletDraft = Wallet(id = "", name = "", kind = "debit")
            openOverlay(ROUTE_WALLET_KIND)
        } else {
            walletDraft = wallet
            openOverlay(ROUTE_WALLET_EDIT)
        }
    }

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

    // 不用 Scaffold：顶栏高度会跟着滚动一小段一小段地变，Scaffold 的 innerPadding 一变
    // 就会把整页重新组合一遍；换成 Column 之后只有布局重排，滚动不会掉帧。
    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        Column(Modifier.fillMaxSize()) {
            PotHeader(
                title = activeTab.label,
                subtitle = subtitle,
                // 顶栏用未选中的线条版：旁边就是 24sp 的半粗标题，图标得轻一点才不抢字
                icon = activeTab.icon,
                // 进度用 lambda 交进去，只让顶栏自己重组合，不牵动整页。
                // 叠加页自己没有收起进度，要沿用进去之前那个 tab 的——
                // 否则从已经滚上去的资产页点进股票时，顶栏会先"弹开"到展开态再收，那一下最扎眼。
                collapse = { collapses[routeNow] ?: collapses[lastTab] ?: 0f },
                hidden = overlay,
            )
            // 底栏不占布局，浮在内容上面：列表可以滚到胶囊背后，
            // 左右两侧露出页面内容（BiliPai 那种悬浮效果），而不是一条实心横栏把内容切掉。
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(headerScroll),
            ) {
                AnimatedContent(
                    targetState = route,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val forward = routeOrder(targetState) >= routeOrder(initialState)
                        val enter = slideInHorizontally(
                            animationSpec = tween(240, easing = FastOutSlowInEasing),
                        ) { width -> if (forward) width / 8 else -width / 8 } + fadeIn(tween(240))
                        val exit = slideOutHorizontally(
                            animationSpec = tween(240, easing = FastOutSlowInEasing),
                        ) { width -> if (forward) -width / 10 else width / 10 } + fadeOut(tween(200))
                        enter togetherWith exit
                    },
                    label = "tab",
                ) { current ->
                    stateHolder.SaveableStateProvider(current) {
                        // 叠加页自己带返回头（EditorBar 那种），顶栏让位。
                        // 但顶栏只塌到状态栏那一条线为止，正好替叠加页占住状态栏的高度，
                        // 所以这里不用再补 statusBarsPadding——转场里内容也就不会先窜上去再落回来。
                        Box(Modifier.fillMaxSize()) {
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
                                stockValue = stockTotals.marketValue,
                                stockPnl = stockTotals.pnl,
                                stockDayPnl = stockTotals.dayPnl,
                                stockAccountCount = ledger.stockAccounts.size,
                                stockTrend = stockTrend.map { it.value },
                                onOpenStocks = { openOverlay(ROUTE_STOCKS) },
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
                                stockValue = stockTotals.marketValue,
                                stockPnl = stockTotals.pnl,
                                stockAccountCount = ledger.stockAccounts.size,
                                onOpenStocks = { openOverlay(ROUTE_STOCKS) },
                                onAdd = { openWallet(null) },
                                onOpenWallet = { openWallet(it) },
                            )

                            current == "settings" -> SettingsScreen(
                                vm = vm,
                                onToast = { toast = it },
                                onOpenImport = { openOverlay(ROUTE_IMPORT) },
                                onOpenTitleSwap = { openOverlay(ROUTE_TITLE_SWAP) },
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

                            current == ROUTE_TITLE_SWAP -> TitleSwapScreen(
                                ledger = ledger,
                                words = prefs.swapWords,
                                // 规则开关、加词、删词都走同一个口子，页面自己算好新集合
                                onSetWords = vm::setSwapWords,
                                onBack = { popOverlay() },
                                onSwap = { ids ->
                                    vm.swapTitles(ids)
                                    toast = "已交换 ${ids.size} 条"
                                },
                            )

                            current == ROUTE_IMPORT -> ImportScreen(
                                vm = vm,
                                incoming = incomingFile,
                                onIncomingConsumed = onIncomingConsumed,
                                onBack = { popOverlay() },
                            )

                            current == ROUTE_WALLET_KIND -> WalletKindScreen(
                                ledger = ledger,
                                selected = walletDraft?.kind ?: "debit",
                                onPick = { kind, presetName ->
                                    val draft = walletDraft
                                    if (draft != null) {
                                        walletDraft = draft.copy(
                                            kind = kind,
                                            name = if (draft.name.isBlank() && presetName.isNotEmpty()) presetName else draft.name,
                                        )
                                    }
                                    if (parentRoute == ROUTE_WALLET_EDIT) {
                                        // 编辑途中回来改类型，退回原来那张表
                                        popOverlay()
                                    } else {
                                        // 新增的第一步：进表单，返回时还会先回到这一页
                                        openOverlay(ROUTE_WALLET_EDIT)
                                    }
                                },
                                onBack = { popOverlay() },
                            )

                            current == ROUTE_WALLET_EDIT -> WalletEditorScreen(
                                wallet = walletDraft ?: Wallet(id = "", name = "", kind = "debit"),
                                isNew = walletIsNew,
                                onBack = { popOverlay() },
                                onPickKind = { openOverlay(ROUTE_WALLET_KIND) },
                                onSave = { updated ->
                                    if (walletIsNew) vm.addWallet(updated) else vm.updateWallet(updated)
                                    toast = if (walletIsNew) "账户已添加" else "账户已更新"
                                    popOverlay()
                                },
                                onDelete = { wallet ->
                                    vm.deleteWallet(wallet.id)
                                    toast = "账户已删除"
                                    popOverlay()
                                },
                            )

                            current == ROUTE_STOCKS -> StocksScreen(
                                ledger = ledger,
                                quotes = quotes,
                                rates = rates,
                                candles = candles,
                                busy = quoteBusy,
                                note = quoteNote,
                                onBack = { popOverlay() },
                                onRefresh = {
                                    vm.refreshQuotes()
                                    vm.ensureDaily(ledger.stocks, force = true)
                                },
                                onOpenAccount = { openOverlay(STOCK_ACC_PREFIX + it) },
                                onAddAccount = vm::addStockAccount,
                            )

                            current.startsWith(STOCK_ACC_PREFIX) -> StockAccountScreen(
                                ledger = ledger,
                                accountId = current.removePrefix(STOCK_ACC_PREFIX),
                                quotes = quotes,
                                rates = rates,
                                candles = candles,
                                busy = quoteBusy,
                                onBack = { popOverlay() },
                                onRefresh = {
                                    vm.refreshQuotes()
                                    vm.ensureDaily(ledger.stocks, force = true)
                                },
                                onSaveHolding = { stock, costE4, shares ->
                                    vm.saveHolding(
                                        current.removePrefix(STOCK_ACC_PREFIX),
                                        stock,
                                        costE4,
                                        shares,
                                    )
                                    vm.refreshQuotes(silent = true)
                                    // 新加的票还没有日 K，补一下才能画走势
                                    vm.ensureDaily(listOf(stock), force = true)
                                },
                                onRemoveHolding = { stockId ->
                                    vm.removeHolding(current.removePrefix(STOCK_ACC_PREFIX), stockId)
                                },
                                onRename = { name ->
                                    val target = ledger.stockAccounts.firstOrNull {
                                        it.id == current.removePrefix(STOCK_ACC_PREFIX)
                                    }
                                    if (target != null) vm.renameStockAccount(target.copy(name = name))
                                },
                                onDelete = {
                                    vm.deleteStockAccount(current.removePrefix(STOCK_ACC_PREFIX))
                                    toast = "股票账户已删除"
                                    popOverlay()
                                },
                                onOpenStock = { stockId ->
                                    openOverlay(
                                        STOCK_DETAIL_PREFIX + current.removePrefix(STOCK_ACC_PREFIX) + "/" + stockId,
                                    )
                                },
                                onLoadDaily = {
                                    val account = ledger.stockAccounts.firstOrNull {
                                        it.id == current.removePrefix(STOCK_ACC_PREFIX)
                                    }
                                    val ids = account?.entries?.keys.orEmpty()
                                    vm.ensureDaily(ledger.stocks.filter { it.id in ids })
                                },
                            )

                            current.startsWith(STOCK_DETAIL_PREFIX) -> {
                                val parts = current.removePrefix(STOCK_DETAIL_PREFIX).split("/")
                                val accountId = parts.firstOrNull().orEmpty()
                                val stockId = parts.getOrNull(1).orEmpty()
                                StockDetailScreen(
                                    ledger = ledger,
                                    accountId = accountId,
                                    stockId = stockId,
                                    quotes = quotes,
                                    rates = rates,
                                    candles = candles,
                                    minutes = minutes,
                                    busy = quoteBusy,
                                    onBack = { popOverlay() },
                                    onRefresh = {
                                        vm.refreshQuotes()
                                        vm.ensureDaily(ledger.stocks, force = true)
                                        ledger.stocks.firstOrNull { it.id == stockId }?.let {
                                            vm.ensureMinute(it, force = true)
                                        }
                                    },
                                    onLoadMinute = {
                                        ledger.stocks.firstOrNull { it.id == stockId }?.let { vm.ensureMinute(it) }
                                    },
                                    onSaveHolding = { stock, costE4, shares ->
                                        vm.saveHolding(accountId, stock, costE4, shares)
                                        vm.refreshQuotes(silent = true)
                                        vm.ensureDaily(listOf(stock), force = true)
                                    },
                                    onRemoveHolding = { id -> vm.removeHolding(accountId, id) },
                                )
                            }

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
                }

            }
        }

        // 撤销条与轻提示都浮在底部胶囊上方，不挤占列表
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .padding(bottom = PotBarHeight + 20.dp),
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

        PotBottomBar(
            selected = if (overlay) lastTab else route,
            onSelect = ::selectTab,
            // 设置页没有「加一笔」这回事，最后一个 tab 就只留导航，胶囊顺势撑满整行
            showAdd = !overlay && route != "settings",
            addHint = if (route == "assets") "添加账户" else "记一笔",
            onAdd = {
                // 资产页的加号是建账户，账目页的加号是记一笔
                if (route == "assets") openWallet(null) else openEditor(null, Fmt.today())
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
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

/* ------------------------------ 顶栏：会缩的页面标题 ------------------------------ */

/** 大标题状态：标题 + 一行说明，给页面本身留的位置也就这么多。 */
private val HeaderExpandedHeight = 58.dp

/** 收起后只剩一行小标题，跟系统顶栏一个高度感。 */
private val HeaderCollapsedHeight = 34.dp

/** 手指在列表上滑这么多，标题从大收到小。拉长一点，中间那些中间态才看得见。 */
private val HeaderCollapseDistance = 118.dp

/**
 * 顶栏 = 当前 tab 的图标 + 页面名字，不再是固定的品牌字。
 *
 * 往下滚的时候标题和图标一起缩小、说明淡掉，腾出来的高度直接还给列表；
 * 往上滚回去又一点点长回来。收起进度由外面的滚动回调给，这里只管画。
 * [collapse] 用 lambda 传：滚动时每秒要读几十次，只让这个组合自己重算。
 */
@Composable
private fun PotHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    collapse: () -> Float,
    hidden: Boolean,
) {
    val colors = LocalPotColors.current
    // 进叠加页时顶栏让位到底，但 statusBarsPadding 还在，所以最后仍留状态栏那一截，
    // 叠加页的内容正好从这条线开始（它自己不用再补一条，否则就是双份留白）。
    val barTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 和页面转场同一个 240ms、同一条缓动曲线：顶栏往下收 vs 新页面横向滑进来，
    // 两者同频才不会互相打架（之前一个是 180 一个是 240，一快一慢看着就别扭）。
    val hide by animateFloatAsState(
        targetValue = if (hidden) 1f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "headerHide",
    )
    // 滚动给的是逐帧的原始进度，弹簧太硬会像"啪"一下贴上去，看着突兀；
    // 这里两层磨：软一点的临界阻尼弹簧（不带过冲）+ 把 0..1 重映射成 smoothstep，
    // 起手和收尾都缓，尺寸只会一点点挪，不会在两端硬拐。
    val raw by animateFloatAsState(
        targetValue = collapse().coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 1f, stiffness = 240f),
        label = "headerCollapse",
    )
    val c = raw * raw * (3f - 2f * raw)
    val idle = HeaderExpandedHeight * (1f - c) + HeaderCollapsedHeight * c
    // 这里只管标题那一段；状态栏的那一截由外面的 statusBarsPadding 撑着，
    // 所以 hide 到 1 时总高正好等于 barTop，和下面那个 Spacer 分支接得上，不会跳。
    val height = idle * (1f - hide)
    // 文字比高度退得早（1.2 倍速度归零）：高度还没压到裁字的程度它就已经淡没了，
    // 不会出现"半截字被裁掉一半"的突兀感。
    val textFade = (1f - hide * 1.2f).coerceIn(0f, 1f)
    // 24sp 到 16sp：收起来之后跟其他页面的小标题一个量级，不喧宾夺主
    val titleSize = 24f + (16f - 24f) * c
    val markSize = (24f + (18f - 24f) * c).dp
    // 说明那行比标题退得稍快一点（1.6 倍进度归零），免得小字糊在中间半透明
    val noteAlpha = ((1f - c * 1.6f) * textFade).coerceIn(0f, 1f)
    // 提前返回要放在所有 remember / 动画状态之后：放在前面会跳过后面那几个状态槽，
    // 下次再恢复动画时槽位对不上，收起进度会突然归零。
    if (hide > 0.995f) {
        // 收完之后只留下状态栏那一截，替叠加页撑着，免得内容钻进状态栏
        Spacer(Modifier.fillMaxWidth().height(barTop))
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.canvas)
            .statusBarsPadding()
            .height(height)
            .clipToBounds()
            .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
    ) {
        Spacer(Modifier.weight(1f))
        // 换 tab 时图标和标题是淡着换的，不是"啪"地翻一下
        Crossfade(
            targetState = title to icon,
            animationSpec = tween(170),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    // 让位时是往上淡走，不是原地被削掉半个字
                    alpha = textFade
                    translationY = -10.dp.toPx() * hide
                },
            label = "headerTitle",
        ) { (text, mark) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = mark,
                    contentDescription = null,
                    tint = colors.brand,
                    modifier = Modifier.size(markSize),
                )
                Spacer(Modifier.width((9f + (6.5f - 9f) * c).dp))
                Text(
                    text = text,
                    fontSize = titleSize.sp,
                    lineHeight = (titleSize * 1.28f).sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        // 说明这一行跟着收起一起被压扁（同时已经淡到看不见），
        // 标题才会稳稳地往上走，而不是先跳一下再缩。
        val noteHeight = 16.dp * (1f - c)
        if (noteHeight > 0.5.dp && noteAlpha > 0.01f) {
            Box(Modifier.height(noteHeight).clipToBounds()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3.copy(alpha = noteAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 底部导航：照 BiliPai 的悬浮胶囊做 —— 壳体 56dp 高、内缩 4dp，滑块跟壳体是同一个胶囊形状
 * （所以圆角是同心嵌套的），选中项的位置靠槽位插值滑过去，不是原地换色。
 * 右侧的加号跟胶囊同高、同一条水平线，设置页没有可加的东西时不出现。
 *
 * 分层只靠两样：底色比页面亮一点（panel vs canvas），外加一圈 1dp 的描边。
 * 不叠投影——浅色纸上多画几层反而会糊出一圈脏边。
 */
private val PotBarHeight = 56.dp
private val PotBarInset = 4.dp
private val PotBarShape = RoundedCornerShape(percent = 50)

@Composable
private fun PotBottomBar(
    selected: String,
    onSelect: (String) -> Unit,
    showAdd: Boolean,
    addHint: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPotColors.current
    val index = tabs.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    // 滑块位置：切 tab 时从旧槽位滑到新槽位，带一点回弹，跟看盘软件那种手感一致
    val slotPosition by animateFloatAsState(
        targetValue = index.toFloat(),
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 420f),
        label = "barSlot",
    )
    // 去设置页没有加号，胶囊要撑满整行：给加号那一格一个会伸缩的宽度，
    // 胶囊跟着一点点变宽/变窄，而不是"啪"地跳一下。
    val addSlot by animateDpAsState(
        targetValue = if (showAdd) PotBarHeight + 10.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 320f),
        label = "addSlot",
    )
    val addScale by animateFloatAsState(
        targetValue = if (showAdd) 1f else 0.55f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 460f),
        label = "addScale",
    )
    val addAlpha by animateFloatAsState(
        targetValue = if (showAdd) 1f else 0f,
        animationSpec = tween(durationMillis = if (showAdd) 160 else 90),
        label = "addAlpha",
    )
    // 只靠底色差 + 一圈描边分层：panel 比 canvas 亮一点点，够了。
    // 之前叠投影反而在纸色背景上糊出一圈脏边，看着很不自然。
    val light = colors.canvas.luminance() > 0.5f
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(PotBarHeight)
                .background(if (light) colors.panel else colors.panel2, PotBarShape)
                .border(
                    width = 1.dp,
                    color = if (light) colors.line else colors.lineStrong,
                    shape = PotBarShape,
                )
                .padding(PotBarInset),
        ) {
            val slot = maxWidth / tabs.size
            Box(
                modifier = Modifier
                    .offset { IntOffset((slot * slotPosition).roundToPx(), 0) }
                    .width(slot)
                    .fillMaxHeight()
                    .clip(PotBarShape)
                    .background(colors.brandTint),
            )
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { item ->
                    TabCell(item = item, active = item.id == selected) { onSelect(item.id) }
                }
            }
        }
        if (addSlot > 0.5.dp) {
            Box(
                modifier = Modifier
                    .width(addSlot)
                    .height(PotBarHeight),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .size(PotBarHeight)
                        .graphicsLayer {
                            alpha = addAlpha
                            scaleX = addScale
                            scaleY = addScale
                        }
                        .background(colors.brand, CircleShape)
                        .clickable {
                            haptics(Haptic.Tap)
                            onAdd()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = addHint,
                        tint = colors.onBrand,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabCell(item: PotTab, active: Boolean, onClick: () -> Unit) {
    val colors = LocalPotColors.current
    // 滑块在两层之间滑，文字跟着变深，不用再做一块自己的底色
    val tone by animateColorAsState(
        targetValue = if (active) colors.brandInk else colors.ink3,
        animationSpec = tween(200),
        label = "tabTone",
    )
    val haptics = rememberHaptics()
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(PotBarShape)
            .clickable {
                // 点当前 tab 不震，只有真的换了页才给一下
                if (!active) haptics(Haptic.Tap)
                onClick()
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (active) item.iconActive else item.icon,
            contentDescription = item.label,
            tint = tone,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            ),
            color = tone,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
