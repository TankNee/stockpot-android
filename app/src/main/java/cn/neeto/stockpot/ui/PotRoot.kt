package cn.neeto.stockpot.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CornerSize
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
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
import cn.neeto.stockpot.AppShortcuts
import cn.neeto.stockpot.LaunchAction
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Wallet
import cn.neeto.stockpot.core.util.Documents
import androidx.compose.material.icons.outlined.Undo
import cn.neeto.stockpot.ui.components.PotPillButton
import cn.neeto.stockpot.ui.components.EditorAnim
import cn.neeto.stockpot.ui.components.EditorReveal
import cn.neeto.stockpot.ui.components.Haptic
import cn.neeto.stockpot.ui.components.LocalRowAnchors
import cn.neeto.stockpot.core.util.Diag
import cn.neeto.stockpot.ui.components.LocalCategoryIcons
import cn.neeto.stockpot.ui.components.effectiveIcon
import cn.neeto.stockpot.ui.components.PotConfirmSheet
import cn.neeto.stockpot.ui.components.RowAnchors
import cn.neeto.stockpot.ui.components.rowAnchor
import cn.neeto.stockpot.ui.components.rememberHaptics
import cn.neeto.stockpot.ui.screens.AccountScreen
import cn.neeto.stockpot.ui.screens.AssetsScreen
import cn.neeto.stockpot.ui.screens.CategoriesScreen
import cn.neeto.stockpot.ui.screens.DataScreen
import cn.neeto.stockpot.ui.screens.RefundPickScreen
import cn.neeto.stockpot.ui.screens.RefundScreen
import cn.neeto.stockpot.ui.screens.EntriesScreen
import cn.neeto.stockpot.ui.screens.EntryEditorScreen
import cn.neeto.stockpot.ui.screens.ImportScreen
import cn.neeto.stockpot.ui.screens.OverviewScreen
import cn.neeto.stockpot.ui.screens.RulesScreen
import cn.neeto.stockpot.ui.screens.ProjectDetailScreen
import cn.neeto.stockpot.ui.screens.ProjectsScreen
import cn.neeto.stockpot.ui.screens.SettingsScreen
import cn.neeto.stockpot.ui.screens.StockAccountScreen
import cn.neeto.stockpot.ui.screens.HoldingEditorScreen
import cn.neeto.stockpot.ui.screens.StockDetailScreen
import cn.neeto.stockpot.ui.screens.StocksScreen
import cn.neeto.stockpot.ui.screens.ThemeScreen
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
    PotTab("overview", "总览", Icons.Outlined.PieChart, Icons.Filled.PieChart, "本地记账 · 登录后可同步到自己的服务器"),
    PotTab("entries", "流水", Icons.Outlined.ReceiptLong, Icons.Filled.ReceiptLong, "每一笔都可以改，也可以删"),
    PotTab("assets", "资产", Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet, "账户余额与净资产，余额自己维护"),
    PotTab("settings", "设置", Icons.Outlined.Settings, Icons.Filled.Settings, "外观、分类、规则、导入与云同步"),
)

/** 压在 tab 之上的页面：导入、项目列表、项目详情、账户添加。 */
private const val ROUTE_IMPORT = "import"
private const val ROUTE_PROJECTS = "projects"
private const val PROJECT_PREFIX = "project/"
private const val ROUTE_WALLET_EDIT = "wallet-edit"
private const val ROUTE_WALLET_KIND = "wallet-kind"
private const val ROUTE_STOCKS = "stocks"
private const val ROUTE_TITLE_SWAP = "title-swap"
/** 设置里的「账号」是独立的一页，不再塞在设置页的弹层里 */
private const val ROUTE_ACCOUNT = "settings/account"
/** 分类管理、关键词规则也各占一页：列表和编辑都在里面，不再挤在弹层里 */
private const val ROUTE_CATEGORIES = "settings/categories"
private const val ROUTE_RULES = "settings/rules"
/** 导入、导出、清空都收进「数据管理」这一页 */
private const val ROUTE_DATA = "settings/data"
/** 退款配对：待配退款、可退支出、已配对三堆东西都在这一页 */
private const val ROUTE_REFUNDS = "settings/refunds"
/** 主题：五套配色各一张缩略图和预设色，深浅也在这一页 */
private const val ROUTE_THEME = "settings/theme"
private const val STOCK_ACC_PREFIX = "stock-acc/"
private const val STOCK_DETAIL_PREFIX = "stock/"
/** 添加 / 编辑持仓：stock-holding/<账户 id>[/<股票 id>]，不带股票 id 就是新建 */
private const val STOCK_HOLDING_PREFIX = "stock-holding/"

private fun tabIndex(id: String): Int = tabs.indexOfFirst { it.id == id }.let { if (it < 0) 0 else it }

/**
 * 谁在谁上面。tab 之间比序号，叠加页统一排在所有 tab 后面，
 * 项目详情再压在项目列表上面，这样返回时动画方向不会反。
 */
private fun routeOrder(route: String): Int = when {
    route.startsWith(PROJECT_PREFIX) -> 100
    route == ROUTE_WALLET_EDIT -> 98
    route == ROUTE_WALLET_KIND -> 97
    route.startsWith(STOCK_HOLDING_PREFIX) -> 99
    route.startsWith(STOCK_DETAIL_PREFIX) -> 96
    route.startsWith(STOCK_ACC_PREFIX) -> 95
    route == ROUTE_STOCKS -> 94
    route == ROUTE_CATEGORIES || route == ROUTE_RULES || route == ROUTE_DATA ||
        route == ROUTE_REFUNDS || route == ROUTE_THEME -> 93
    route == ROUTE_ACCOUNT -> 92
    route == ROUTE_TITLE_SWAP -> 91
    route == ROUTE_PROJECTS || route == ROUTE_IMPORT -> 90
    else -> tabIndex(route)
}

private fun isOverlay(route: String): Boolean =
    route == ROUTE_IMPORT || route == ROUTE_PROJECTS || route.startsWith(PROJECT_PREFIX) ||
        route == ROUTE_WALLET_EDIT || route == ROUTE_WALLET_KIND ||
        route == ROUTE_STOCKS || route.startsWith(STOCK_ACC_PREFIX) ||
        route.startsWith(STOCK_DETAIL_PREFIX) || route.startsWith(STOCK_HOLDING_PREFIX) ||
        route == ROUTE_TITLE_SWAP ||
        route == ROUTE_ACCOUNT || route == ROUTE_CATEGORIES || route == ROUTE_RULES ||
        route == ROUTE_DATA || route == ROUTE_REFUNDS || route == ROUTE_THEME

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
    /** 长按桌面图标点进来的那一下；执行完由外面清掉。 */
    launchAction: LaunchAction? = null,
    onLaunchConsumed: () -> Unit = {},
) {
    val colors = LocalPotColors.current
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    // 还在读库 / 正在等第一轮云端对账：这时候的「空」不作数
    val phase by vm.phase.collectAsStateWithLifecycle()
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
    // 从一条账目点进它关联的那条（退款 ⇄ 原消费），退回时按这个栈一层层回去
    var editorTrail by remember { mutableStateOf<List<Entry>>(emptyList()) }

    // 编辑页从哪儿长出来：行自己在布局时把位置登记进 rowAnchors，点开时按条目 id 取一次
    val rowAnchors = remember { RowAnchors() }
    var editorOrigin by remember { mutableStateOf<Rect?>(null) }
    // 「挑一笔」是整页，压在编辑页之上：从编辑页、退款页都能进，关掉回到来的地方。
    // 退出动画放完之前内容还得留着，所以额外记一份「最后打开过的那条」。
    var pickForId by rememberSaveable { mutableStateOf("") }
    var pickHold by remember { mutableStateOf<Entry?>(null) }

    /** 展开进度：0 = 还缩在那一行里，1 = 整页铺开。 */
    val editorProgress = remember { Animatable(0f) }
    /** 退出动画放完之前编辑页还得留着，所以「该不该显示」和「想不想显示」分开记。 */
    var editorShowing by remember { mutableStateOf(false) }
    // 弹层里显示的那条要跟着账本走：刚关联完 refundLinkId 就变了，
    // 还拿打开时的快照就会一直显示「未关联」。
    val liveEditorEntry = editorEntry?.let { current ->
        ledger.entries.firstOrNull { it.id == current.id } ?: current
    }

    // 删除的撤销用整份账本快照：删退款行会连带把原消费的已退金额改掉，
    // 只把那一行放回去账就歪了。
    var undoSnapshot by remember { mutableStateOf<Ledger?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    // 长按某一行流水之后等确认的那一条
    var pendingDelete by remember { mutableStateOf<Entry?>(null) }
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

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

    LaunchedEffect(undoSnapshot) {
        if (undoSnapshot != null) {
            delay(3_600)
            undoSnapshot = null
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2_200)
            toast = null
        }
    }

    // 开 / 关编辑页。开的时候用弹簧收尾干净（不该有回弹，整页弹出去会露边），
    // 关的时候用短一点的 tween：iOS 里也是收得比开得利落。
    LaunchedEffect(editorOpen) {
        if (editorOpen) {
            editorShowing = true
            editorProgress.animateTo(1f, spring(dampingRatio = 1f, stiffness = 420f))
        } else if (editorShowing) {
            editorProgress.animateTo(0f, tween(260, easing = FastOutLinearInEasing))
            editorShowing = false
            // 等动画落定再清：清早了，退出途中这一页会跳成「记一笔」
            editorEntry = null
            editorTrail = emptyList()
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

    // 「记一笔」是从胶囊上的加号进来的，没有那一行可落，就从加号那颗圆长出来
    val screen = LocalConfiguration.current
    val rootDensity = LocalDensity.current
    fun addOrigin(): Rect {
        rowAnchors.rectOf(ADD_ANCHOR)?.let { return it }
        // 底栏这会儿不在（比如叠加页里）：退回屏幕右下角那一带，至少方向是对的
        val widthPx = with(rootDensity) { screen.screenWidthDp.dp.toPx() }
        val heightPx = with(rootDensity) { screen.screenHeightDp.dp.toPx() }
        val half = with(rootDensity) { 34.dp.toPx() }
        return Rect(widthPx / 2f - half, heightPx - half * 3f, widthPx / 2f + half, heightPx - half)
    }

    fun openEditor(entry: Entry?, date: String) {
        editorEntry = entry
        editorDate = date
        editorTrail = emptyList()
        // 行还没布局过（理论上不会）就退回加号那一带，总比从屏幕正中间凭空长出来好
        editorOrigin = entry?.let { rowAnchors.rectOf(it.id) } ?: addOrigin()
        editorOpen = true
    }

    /** 打开「挑一笔」整页：挑的是 [entry] 的对面。 */
    fun openRefundPick(entry: Entry) {
        pickForId = entry.id
    }

    /** 跳到对面那条，当前这条压进栈，返回箭头能原路退回来。 */
    fun openLinkedEntry(target: Entry) {
        val current = liveEditorEntry
        if (current != null && current.id != target.id) editorTrail = editorTrail + current
        editorEntry = target
        editorDate = target.date
        editorOpen = true
    }

    fun popLinkedEntry() {
        val previous = editorTrail.lastOrNull() ?: return
        editorTrail = editorTrail.dropLast(1)
        editorEntry = previous
        editorDate = previous.date
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

    // 长按图标点进来的：记一笔直接开编辑页，其余落到对应那一页。
    // 带 id 的 key 是为了「同一个快捷方式连点两次」也能再执行一遍。
    LaunchedEffect(launchAction) {
        val request = launchAction ?: return@LaunchedEffect
        when (request.name) {
            AppShortcuts.NEW_ENTRY -> openEditor(null, Fmt.today())
            AppShortcuts.ENTRIES -> selectTab("entries")
            AppShortcuts.REFUNDS -> {
                selectTab("settings")
                openOverlay(ROUTE_REFUNDS)
            }
            AppShortcuts.IMPORT -> {
                selectTab("settings")
                openOverlay(ROUTE_IMPORT)
            }
        }
        onLaunchConsumed()
    }

    val stateHolder = rememberSaveableStateHolder()

    // 编辑页展开时后面这一页要缩一点：跟 iOS 推一张卡片上来时后面的页面后退一格是同一个意思，
    // 有这一下，前后两个场景才有层次，而不是两张全屏图硬叠在一起。圆角跟着一起收。
    val backCornerPx = with(LocalDensity.current) { 30.dp.toPx() }

    // 不用 Scaffold：顶栏高度会跟着滚动一小段一小段地变，Scaffold 的 innerPadding 一变
    // 就会把整页重新组合一遍；换成 Column 之后只有布局重排，滚动不会掉帧。
    // 分类路径 → 图标：列表行（CategoryAvatar）拿不到账本，从这里查
    val categoryIcons = remember(ledger.categories) {
        ledger.categories.associate { it.path to it.effectiveIcon() }
    }
    CompositionLocalProvider(
        LocalRowAnchors provides rowAnchors,
        LocalCategoryIcons provides categoryIcons,
    ) {
        Box(Modifier.fillMaxSize().background(colors.canvas)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val t = editorProgress.value
                        if (t > 0f) {
                            val shrink = 1f - 0.06f * t
                            scaleX = shrink
                            scaleY = shrink
                            clip = true
                            shape = RoundedCornerShape(CornerSize(backCornerPx * t))
                        }
                    },
            ) {
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
                            // 页面切换记一笔：闪退后能看出是从哪一页崩的
                            LaunchedEffect(current) { Diag.step("route", current) }
                            stateHolder.SaveableStateProvider(current) {
                                // 叠加页自己带返回头（EditorBar 那种），顶栏让位。
                                // 但顶栏只塌到状态栏那一条线为止，正好替叠加页占住状态栏的高度，
                                // 所以这里不用再补 statusBarsPadding——转场里内容也就不会先窜上去再落回来。
                                Box(Modifier.fillMaxSize()) {
                                    when {
                                    current == "overview" -> OverviewScreen(
                                        ledger = ledger,
                                        phase = phase,
                                        onOpenEntry = { openEditor(it, it.date) },
                                        onLongPressEntry = { pendingDelete = it },
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
                                        refreshing = refreshing,
                                        onRefresh = { vm.pullRefresh { toast = it } },
                                        stockValue = stockTotals.marketValue,
                                        stockPnl = stockTotals.pnl,
                                        stockDayPnl = stockTotals.dayPnl,
                                        stockAccountCount = ledger.stockAccounts.size,
                                        stockTrend = stockTrend.map { it.value },
                                        onOpenStocks = { openOverlay(ROUTE_STOCKS) },
                                    )

                                    current == "entries" -> EntriesScreen(
                                        ledger = ledger,
                                        phase = phase,
                                        month = entriesMonth,
                                        dayFilter = entriesDate,
                                        onMonth = { entriesMonth = it },
                                        onDayFilter = { entriesDate = it },
                                        onOpenEntry = { openEditor(it, it.date) },
                                        onLongPressEntry = { pendingDelete = it },
                                        onOpenImport = { openOverlay(ROUTE_IMPORT) },
                                        refreshing = refreshing,
                                        onRefresh = { vm.pullRefresh { toast = it } },
                                    )

                                    current == "assets" -> AssetsScreen(
                                        ledger = ledger,
                                        phase = phase,
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
                                        onOpenTitleSwap = { openOverlay(ROUTE_TITLE_SWAP) },
                                        onOpenAccount = { openOverlay(ROUTE_ACCOUNT) },
                                        onOpenCategories = { openOverlay(ROUTE_CATEGORIES) },
                                        onOpenRules = { openOverlay(ROUTE_RULES) },
                                        onOpenRefunds = { openOverlay(ROUTE_REFUNDS) },
                                        onOpenData = { openOverlay(ROUTE_DATA) },
                                        onOpenTheme = { openOverlay(ROUTE_THEME) },
                                    )

                                    current == ROUTE_THEME -> ThemeScreen(
                                        vm = vm,
                                        onBack = { popOverlay() },
                                    )

                                    current == ROUTE_DATA -> DataScreen(
                                        vm = vm,
                                        onBack = { popOverlay() },
                                        onOpenImport = { openOverlay(ROUTE_IMPORT) },
                                        onToast = { toast = it },
                                    )

                                    current == ROUTE_REFUNDS -> RefundScreen(
                                        ledger = ledger,
                                        onBack = { popOverlay() },
                                        onOpenEntry = { openEditor(it, it.date) },
                                        onUnlink = { vm.unlinkRefund(it) },
                                        onAutoPair = { vm.autoPairRefunds() },
                                        onPickFor = { openRefundPick(it) },
                                        onToast = { toast = it },
                                    )

                                    current == ROUTE_CATEGORIES -> CategoriesScreen(
                                        ledger = ledger,
                                        onBack = { popOverlay() },
                                        onAdd = { name, parent, kind, icon ->
                                            vm.addCategory(name, parent, kind, icon)
                                        },
                                        onUpdate = { def, name, parent, kind, icon ->
                                            vm.updateCategory(def, name, parent, kind, icon)
                                            toast = "分类已更新"
                                        },
                                        onDelete = { def -> vm.deleteCategory(def) },
                                    )

                                    current == ROUTE_RULES -> RulesScreen(
                                        ledger = ledger,
                                        onBack = { popOverlay() },
                                        onAdd = { pattern, category ->
                                            vm.addRule(pattern, category)
                                            toast = "规则已添加"
                                        },
                                        onUpdate = { rule ->
                                            vm.updateRule(rule)
                                            toast = "规则已更新"
                                        },
                                        onDelete = { id -> vm.deleteRule(id) },
                                    )

                                    current == ROUTE_ACCOUNT -> AccountScreen(
                                        vm = vm,
                                        onBack = { popOverlay() },
                                        onToast = { toast = it },
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
                                        onOpenHolding = { stockId ->
                                            val accountId = current.removePrefix(STOCK_ACC_PREFIX)
                                            openOverlay(
                                                STOCK_HOLDING_PREFIX + accountId +
                                                    if (stockId == null) "" else "/$stockId",
                                            )
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
                                            onEditHolding = {
                                                openOverlay("$STOCK_HOLDING_PREFIX$accountId/$stockId")
                                            },
                                            onRemoveHolding = { id -> vm.removeHolding(accountId, id) },
                                        )
                                    }

                                    current.startsWith(STOCK_HOLDING_PREFIX) -> {
                                        val parts = current.removePrefix(STOCK_HOLDING_PREFIX).split("/")
                                        val accountId = parts.firstOrNull().orEmpty()
                                        val stockId = parts.getOrNull(1).orEmpty()
                                        val account = ledger.stockAccounts.firstOrNull { it.id == accountId }
                                        val stock = ledger.stocks.firstOrNull { it.id == stockId }
                                        val holding = account?.entries?.get(stockId)
                                        HoldingEditorScreen(
                                            accountName = account?.name.orEmpty(),
                                            existing = if (stock != null && holding != null) stock to holding else null,
                                            quotes = quotes,
                                            onBack = { popOverlay() },
                                            onSave = { saved, costE4, shares ->
                                                vm.saveHolding(accountId, saved, costE4, shares)
                                                vm.refreshQuotes(silent = true)
                                                // 新加的票还没有日 K，补一下才能画走势
                                                vm.ensureDaily(listOf(saved), force = true)
                                                toast = if (stock == null) "已加入持仓" else "持仓已更新"
                                                popOverlay()
                                            },
                                            onDelete = { id ->
                                                vm.removeHolding(accountId, id)
                                                vm.refreshQuotes(silent = true)
                                            },
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

                // 撤销条与轻提示都浮在底部胶囊上方，不挤占列表。
                // 进到二级页面时胶囊滑走，它们也跟着落下去，免得悬在半空。
                val noticeLift by animateDpAsState(
                    targetValue = if (overlay) 0.dp else PotBarHeight + 20.dp,
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    label = "noticeLift",
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                        .padding(bottom = noticeLift),
                ) {
                    val pending = undoSnapshot
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
                                PotPillButton(
                                    text = "撤销",
                                    icon = Icons.Outlined.Undo,
                                    onClick = {
                                        val snapshot = undoSnapshot
                                        if (snapshot != null) vm.restoreLedger(snapshot)
                                        undoSnapshot = null
                                    },
                                )
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

                // tab 胶囊只在四个主页面出现：进到二级页面它整条滑下去，
                // 免得一页「账号 / 项目详情」底下还压着导航，看着像还没进去。
                // 选中态沿用 lastTab，退场那 200ms 里高亮不会先跳一下。
                AnimatedVisibility(
                    visible = !overlay,
                    enter = slideInVertically(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                    ) { height -> height } + fadeIn(tween(160)),
                    exit = slideOutVertically(
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                    ) { height -> height } + fadeOut(tween(120)),
                    modifier = Modifier.align(Alignment.BottomCenter),
                    label = "bottomBar",
                ) {
                    PotBottomBar(
                        selected = if (overlay) lastTab else route,
                        onSelect = ::selectTab,
                        // 设置页没有「加一笔」这回事，最后一个 tab 就只留导航，胶囊顺势撑满整行；
                        // 退场时按主页面那个 tab 算，形状不会在滑走的半路上突然变宽
                        showAdd = (if (overlay) lastTab else route) != "settings",
                        addHint = if (route == "assets") "添加账户" else "记一笔",
                        onAdd = {
                            // 资产页的加号是建账户，账目页的加号是记一笔
                            if (route == "assets") openWallet(null) else openEditor(null, Fmt.today())
                        },
                    )
                }
            }

            // 压暗后面那一页。只读进度、只画一层，进度变化不会引起重组。
            // 深色主题下 ink 是浅色的，压暗得用黑，否则会越压越亮。
            val dim = if (colors.canvas.luminance() > 0.5f) colors.ink else Color.Black
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val t = editorProgress.value
                        if (t > 0f) drawRect(dim, alpha = 0.34f * t)
                    },
            )

            // 编辑页本身：从点中的那一行摊开 / 放大 / 推进来 / 从底下推上来，退出时原样收回去。
            // 四种过场在设置里选，默认是折叠。
            // 内容在退出动画放完之前一直留着，所以拿 editorShowing 判断而不是 editorOpen。
            if (editorShowing) {
                EditorReveal(
                    anim = EditorAnim.of(prefs.editorAnim),
                    progress = { editorProgress.value },
                    origin = editorOrigin,
                ) {
                    EntryEditorScreen(
                        ledger = ledger,
                        existing = liveEditorEntry,
                        initialDate = editorDate,
                        onDismiss = { editorOpen = false },
                        onSave = {
                            vm.addEntry(it)
                            toast = "已记下"
                        },
                        onUpdate = {
                            vm.saveEntry(it)
                            toast = "已更新"
                        },
                        onDelete = { entry ->
                            undoSnapshot = ledger
                            vm.deleteEntry(entry.id)
                        },
                        onAddCategory = { name -> vm.addCategory(name, "", "") },
                        onOpenEntry = { openLinkedEntry(it) },
                        onLinkRefund = { expenseId, refundId -> vm.linkRefund(expenseId, refundId) },
                        onUnlinkRefund = { vm.unlinkRefund(it) },
                        onPickRefund = { openRefundPick(it) },
                        onBack = if (editorTrail.isEmpty()) null else ({ popLinkedEntry() }),
                    )
                }
            }

            // 「挑一笔」压在编辑页之上：两边都能进来，关掉就回到来的那一层。
            // 用一张整页而不是弹层——候选几十条、还要搜索和看细节，半屏根本不够用。
            val pickTarget = pickForId.let { id -> ledger.entries.firstOrNull { it.id == id } }
            SideEffect { if (pickTarget != null) pickHold = pickTarget }
            val picking = pickTarget ?: pickHold
            AnimatedVisibility(
                visible = pickTarget != null,
                enter = slideInHorizontally(
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                ) { width -> width } + fadeIn(tween(180)),
                exit = slideOutHorizontally(
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                ) { width -> width } + fadeOut(tween(160)),
            ) {
                if (picking != null) {
                    RefundPickScreen(
                        ledger = ledger,
                        entry = picking,
                        onBack = { pickForId = "" },
                        onPick = { other ->
                            val expenseId = if (picking.kind == Kind.EXPENSE) picking.id else other.id
                            val refundId = if (picking.kind == Kind.EXPENSE) other.id else picking.id
                            vm.linkRefund(expenseId, refundId)
                            pickForId = ""
                            toast = "已关联 · 这笔账里退的钱只算一次"
                        },
                    )
                }
            }
        }
    }

    // 长按流水行：先问一句再删。确认那一刻存一份账本快照，底部那条「撤销」照旧能用
    val doomed = pendingDelete
    if (doomed != null) {
        PotConfirmSheet(
            title = "删除这笔？",
            body = deletePrompt(doomed),
            confirmText = "删除",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                undoSnapshot = ledger
                vm.deleteEntry(doomed.id)
            },
        )
    }
}

/** 删除确认里那句话：标题 + 钱，别让人对着「确定删除吗」猜是哪一笔。 */
private fun deletePrompt(entry: Entry): String {
    val title = entry.counterparty.ifEmpty {
        entry.note.ifEmpty { entry.category.ifEmpty { "这笔账" } }
    }
    val amount = if (entry.effectiveAmount < 0) -entry.effectiveAmount else entry.effectiveAmount
    return "「$title」${Fmt.yuan(amount)} 会被删掉，本机账本和云端都会跟着少这一笔。" +
        "删错了底部还有一次撤销。"
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

/** 底栏加号在位置登记表里的键：「记一笔」没有来源行，就从这颗加号长出来。 */
private const val ADD_ANCHOR = "ui:add"
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
    val addAnchors = LocalRowAnchors.current
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
                        // 记一笔的展开动画从这颗圆开始，先报个位置
                        .then(addAnchors?.let { Modifier.rowAnchor(ADD_ANCHOR, it) } ?: Modifier)
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
    // 选中态已经有一块跟着滑的胶囊，别再叠一层 ripple：
    // 两个阴影叠在一起，点一下就糊成一团
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(PotBarShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) {
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
