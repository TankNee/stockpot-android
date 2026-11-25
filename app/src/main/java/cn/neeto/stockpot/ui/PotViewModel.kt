package cn.neeto.stockpot.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.neeto.stockpot.PotApp
import cn.neeto.stockpot.core.data.EntryInput
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.data.LedgerOps
import cn.neeto.stockpot.core.data.LedgerPhase
import cn.neeto.stockpot.core.data.ledgerPhase
import cn.neeto.stockpot.core.importer.BillParser
import cn.neeto.stockpot.core.importer.ImportEngine
import cn.neeto.stockpot.core.importer.ImportOutcome
import cn.neeto.stockpot.core.importer.ImportPreview
import cn.neeto.stockpot.core.importer.ParsedBill
import cn.neeto.stockpot.core.model.Candle
import cn.neeto.stockpot.core.model.CategoryDef
import cn.neeto.stockpot.core.model.CurvePoint
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Holding
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Project
import cn.neeto.stockpot.core.model.Quote
import cn.neeto.stockpot.core.model.Rates
import cn.neeto.stockpot.core.model.Rule
import cn.neeto.stockpot.core.model.Stock
import cn.neeto.stockpot.core.model.StockAccount
import cn.neeto.stockpot.core.model.Wallet
import cn.neeto.stockpot.core.net.LoginOutcome
import cn.neeto.stockpot.core.net.SyncManager
import cn.neeto.stockpot.core.net.TwoFactorSetup
import cn.neeto.stockpot.core.net.Updater
import cn.neeto.stockpot.core.stock.QuoteClient
import cn.neeto.stockpot.core.util.Avatars
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 一次「选了文件」之后的结果：账单要确认，账本 JSON 也要确认，失败要说人话。 */
sealed interface PickResult {
    data class Bill(val bill: ParsedBill, val preview: ImportPreview) : PickResult
    data class LedgerFile(val incoming: Ledger, val fileName: String) : PickResult
    data class Failed(val message: String) : PickResult
}

class PotViewModel(app: Application) : AndroidViewModel(app) {

    private val pot = app as PotApp
    private val store = pot.store
    private val settings = pot.settings

    val ledger: StateFlow<Ledger> = store.data
    val prefs = settings.state

    val dataPath: String get() = store.path

    /* ------------------------------ 云端 ------------------------------ */

    private val sync = pot.sync

    /** 登录状态与同步进度；没登录时 signedIn 为 false，UI 就当它不存在 */
    val cloud: StateFlow<SyncManager.SyncUi> = sync.ui

    /**
     * 页面现在该显示什么：还在读库 / 真的空 / 有数据。
     *
     * 判据都在 [ledgerPhase] 里，这里只负责把三个来源拼起来。初始值给 LOADING：
     * 第一帧永远还没读完库，先按「不知道」处理，省得闪一下空状态。
     */
    val phase: StateFlow<LedgerPhase> = combine(store.ready, store.data, cloud) { ready, ledger, ui ->
        ledgerPhase(
            ready = ready,
            entries = ledger.entries.size,
            signedIn = ui.signedIn,
            syncing = ui.status == SyncManager.Status.SYNCING,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LedgerPhase.LOADING)

    val serverUrl: String get() = settings.state.value.serverUrl

    suspend fun cloudLogin(email: String, password: String): Result<LoginOutcome> = sync.login(email, password)

    suspend fun cloudLoginTwoFactor(ticket: String, code: String): Result<Unit> =
        sync.loginTwoFactor(ticket, code)

    suspend fun cloudRegister(email: String, password: String, name: String): Result<Unit> =
        sync.register(email, password, name)

    suspend fun cloudSyncNow(): Boolean = sync.syncNow()

    suspend fun cloudClearRemote(): Result<Unit> = sync.clearRemote()

    fun cloudLogout() = sync.logout()

    fun setServerUrl(url: String) = settings.setServerUrl(url)

    /** 本机有没有还没推上去的改动，账号页拿来显示「是否一致」。 */
    val cloudDirty: Boolean get() = pot.auth.dirty

    /* ------------------------------ 账号资料 ------------------------------ */

    private val _avatar = MutableStateFlow<Bitmap?>(null)

    /** 头像位图。本地私有的东西，不落共享存储，退出登录就清掉。 */
    val avatar: StateFlow<Bitmap?> = _avatar

    /** 上次拉的是哪一版：同一版不重复打网络，换了头像才重新拉。 */
    private var avatarStamp = ""

    /**
     * 按需拉头像。头像单独一个请求，`avatarAt` 变了才重拉——
     * 每次进账号页都下载一遍几十 KB 没必要。
     */
    fun loadAvatar() {
        val ui = cloud.value
        if (!ui.signedIn || !ui.account.hasAvatar) {
            avatarStamp = ""
            if (_avatar.value != null) _avatar.value = null
            return
        }
        val stamp = ui.account.id + "@" + ui.account.avatarAt
        if (stamp == avatarStamp) return
        avatarStamp = stamp
        viewModelScope.launch {
            val bytes = sync.avatarBytes()
            _avatar.value = bytes?.let { Avatars.decode(it) }
            if (_avatar.value == null) avatarStamp = ""
        }
    }

    suspend fun updateName(name: String): Result<Unit> = sync.updateName(name)

    suspend fun changePassword(current: String, next: String): Result<Unit> =
        sync.changePassword(current, next)

    /** 相册选图 → 裁剪压缩 → 上传。读图和压缩都在 IO 线程，别卡着转圈动画。 */
    suspend fun setAvatarFromUri(uri: Uri): Result<Unit> {
        val dataUrl = withContext(Dispatchers.IO) { Avatars.dataUrlFrom(getApplication(), uri) }
            ?: return Result.failure(IllegalStateException("这张图读不出来，换一张试试"))
        return sync.uploadAvatar(dataUrl).onSuccess { loadAvatar() }
    }

    suspend fun removeAvatar(): Result<Unit> = sync.removeAvatar().onSuccess { loadAvatar() }

    suspend fun setupTwoFactor(): Result<TwoFactorSetup> = sync.setupTwoFactor()

    suspend fun enableTwoFactor(code: String): Result<List<String>> = sync.enableTwoFactor(code)

    suspend fun disableTwoFactor(password: String): Result<Unit> = sync.disableTwoFactor(password)

    /* ------------------------------ 下拉刷新 ------------------------------ */

    private val _refreshing = MutableStateFlow(false)

    /** 首页和流水页共用的转圈状态：一次只刷一份，两页不会各转各的。 */
    val refreshing: StateFlow<Boolean> = _refreshing

    /**
     * 下拉刷新。
     *
     * 账本本身是本地的，没什么可拉的，所以「刷」的是外面那两样：云端那一次对账、
     * 股票行情和日 K。两样都没有也照样转一下，回一句「已经是最新的」——
     * 否则下拉之后屏幕毫无反应，会让人以为手势没生效。
     *
     * 有两处以前会让人以为「刷新坏了」：一是同步撞上后台那一轮就直接报失败，
     * 二是中途抛异常时 `_refreshing` 没复位，之后每次下拉都被自己挡住。
     * 现在同步那边排队等结果，这里用 finally 保证转圈一定收回来。
     */
    fun pullRefresh(onDone: (String) -> Unit) {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            var synced: Boolean? = null
            var syncError: String? = null
            val stocks = store.data.value.stocks
            try {
                if (sync.signedIn) {
                    synced = sync.syncNow()
                    if (synced == false) {
                        // 401 会把登录态顺手清掉，这时候说「网络不通」是骗人
                        syncError = if (sync.signedIn) {
                            sync.ui.value.error
                        } else {
                            "登录过期了，重新登录一次"
                        }
                    }
                }
                if (stocks.isNotEmpty()) {
                    refreshQuotes(silent = true)
                    ensureDaily(stocks, force = true)
                }
                // 转圈至少停 450ms：一闪而过比不刷新还让人心里没底
                val gap = 450 - (System.currentTimeMillis() - started)
                if (gap > 0) delay(gap)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (sync.signedIn) {
                    synced = false
                    syncError = error.message ?: error::class.java.simpleName
                }
            } finally {
                // 一定要复位：漏了这一次，后面每次下拉都会被这行挡住
                _refreshing.value = false
            }
            onDone(RefreshNote.of(synced, syncError, stocks.size))
        }
    }

    /* ------------------------------ 应用更新 ------------------------------ */

    private val updater = pot.updater

    val update: StateFlow<Updater.State> = updater.state

    /** 设置页点「检查更新」是 manual：失败要说出来；开机那次是静默的。 */
    fun checkUpdate(manual: Boolean = false) = updater.check(silent = !manual)

    fun downloadUpdate() = updater.download()

    /** 返回 false 表示没拉起安装器（系统不让装未知来源，或者包不在了）。 */
    fun installUpdate(): Boolean = updater.install()

    private var lastForegroundSync = 0L

    /** 回到前台对一次账。半分钟内刚同步过就不重复打网络。 */
    fun syncOnForeground() {
        if (!sync.signedIn) return
        val now = System.currentTimeMillis()
        if (now - lastForegroundSync < 30_000) return
        lastForegroundSync = now
        viewModelScope.launch { sync.syncNow() }
    }

    /* ------------------------------ 账目 ------------------------------ */

    fun addEntry(input: EntryInput) = store.update { LedgerOps.withEntry(it, LedgerOps.entry(input)) }

    fun saveEntry(entry: Entry) = store.update { LedgerOps.updateEntry(it, entry) }

    fun deleteEntry(id: String) = store.update { LedgerOps.deleteEntry(it, id) }

    /** 删除的撤销：整份账本回滚（删退款行会连带改原消费的已退金额）。 */
    fun restoreLedger(ledger: Ledger) = store.replaceAll(ledger)

    /** 支出 ↔ 退款互相绑定，顺带把退款金额记到支出上。 */
    fun linkRefund(expenseId: String, refundId: String) =
        store.update { LedgerOps.linkRefund(it, expenseId, refundId) }

    fun unlinkRefund(id: String) = store.update { LedgerOps.unlinkRefund(it, id) }

    /** 退款页上的一键配对，返回真正配上的对数。 */
    fun autoPairRefunds(): Int {
        var linked = 0
        store.update { ledger ->
            val (next, count) = LedgerOps.autoPairRefunds(ledger)
            linked = count
            next
        }
        return linked
    }

    /* ------------------------------ 分类 ------------------------------ */

    fun addCategory(name: String, parent: String, kind: String) = store.update {
        it.copy(categories = LedgerOps.addCategory(it.categories, name, parent, kind))
    }

    fun updateCategory(old: CategoryDef, name: String, parent: String, kind: String) = store.update {
        LedgerOps.renameCategory(it, old, name, parent, kind)
    }

    fun deleteCategory(def: CategoryDef) = store.update { LedgerOps.deleteCategory(it, def) }

    /* ------------------------------ 规则 ------------------------------ */

    fun addRule(pattern: String, category: String) = store.update {
        it.copy(rules = LedgerOps.addRule(it.rules, pattern, category))
    }

    fun updateRule(rule: Rule) = store.update { it.copy(rules = LedgerOps.updateRule(it.rules, rule)) }

    fun deleteRule(id: String) = store.update { it.copy(rules = LedgerOps.deleteRule(it.rules, id)) }

    /* ------------------------------ 项目 ------------------------------ */

    fun createProject(name: String, note: String, start: String, end: String) = store.update { current ->
        val (projects, _) = LedgerOps.addProject(current.projects, name, note, start, end)
        current.copy(projects = projects)
    }

    fun updateProject(project: Project) = store.update {
        it.copy(projects = LedgerOps.updateProject(it.projects, project))
    }

    fun deleteProject(id: String) = store.update { LedgerOps.deleteProject(it, id) }

    fun attach(projectId: String, ids: Set<String>) = store.update { LedgerOps.attach(it, projectId, ids) }

    fun detach(ids: Set<String>) = store.update { LedgerOps.detach(it, ids) }

    /* ------------------------------ 钱包 ------------------------------ */

    fun addWallet(draft: Wallet) = store.update {
        it.copy(wallets = LedgerOps.addWallet(it.wallets, draft))
    }

    fun updateWallet(wallet: Wallet) = store.update {
        it.copy(wallets = LedgerOps.updateWallet(it.wallets, wallet))
    }

    fun deleteWallet(id: String) = store.update {
        it.copy(wallets = LedgerOps.deleteWallet(it.wallets, id))
    }

    /* ------------------------------ 股票 ------------------------------ */

    private val _quotes = MutableStateFlow<Map<String, Quote>>(emptyMap())
    val quotes: StateFlow<Map<String, Quote>> = _quotes

    private val _quoteBusy = MutableStateFlow(false)
    val quoteBusy: StateFlow<Boolean> = _quoteBusy

    private val _quoteNote = MutableStateFlow<String?>(null)
    val quoteNote: StateFlow<String?> = _quoteNote

    private val _rates = MutableStateFlow(Rates())
    val rates: StateFlow<Rates> = _rates

    /** 拉一次行情。`silent` 的时候失败不吭声，页面自己决定要不要提示。 */
    fun refreshQuotes(silent: Boolean = false) {
        if (_quoteBusy.value) return
        val stocks = store.data.value.stocks
        if (stocks.isEmpty()) {
            _quoteNote.value = null
            return
        }
        viewModelScope.launch {
            _quoteBusy.value = true
            val fetched = runCatching { QuoteClient.fetch(stocks) }.getOrDefault(emptyMap())
            runCatching { QuoteClient.fetchRates() }.getOrNull()?.let { _rates.value = it }
            if (fetched.isNotEmpty()) {
                _quotes.value = _quotes.value + fetched
                val names = fetched.mapValues { it.value.name }
                store.update { ledger ->
                    val renamed = LedgerOps.fillStockNames(ledger.stocks, names)
                    if (renamed == ledger.stocks) ledger else ledger.copy(stocks = renamed)
                }
                _quoteNote.value = if (fetched.size < stocks.size) "部分标的没拿到行情" else null
            } else if (!silent) {
                _quoteNote.value = "行情没拉到，检查一下网络"
            }
            _quoteBusy.value = false
        }
    }

    fun addStockAccount(name: String) = store.update {
        it.copy(stockAccounts = LedgerOps.addStockAccount(it.stockAccounts, name))
    }

    fun renameStockAccount(account: StockAccount) = store.update {
        it.copy(stockAccounts = LedgerOps.updateStockAccount(it.stockAccounts, account))
    }

    fun deleteStockAccount(id: String) = store.update { ledger ->
        val rest = LedgerOps.deleteStockAccount(ledger.stockAccounts, id)
        ledger.copy(stockAccounts = rest, stocks = LedgerOps.pruneStocks(ledger.stocks, rest))
    }

    /** 一个账户里加/改一只票。 */
    fun saveHolding(accountId: String, stock: Stock, costE4: Long, shares: Long) = store.update { ledger ->
        val accounts = ledger.stockAccounts.map { account ->
            if (account.id == accountId) {
                LedgerOps.putHolding(account, stock.id, Holding(costE4 = costE4, shares = shares))
            } else {
                account
            }
        }
        ledger.copy(stocks = LedgerOps.upsertStock(ledger.stocks, stock), stockAccounts = accounts)
    }

    fun removeHolding(accountId: String, stockId: String) = store.update { ledger ->
        val accounts = ledger.stockAccounts.map { account ->
            if (account.id == accountId) LedgerOps.removeHolding(account, stockId) else account
        }
        ledger.copy(stocks = LedgerOps.pruneStocks(ledger.stocks, accounts), stockAccounts = accounts)
    }

    /* ------------------------------ 分时 / 日 K ------------------------------ */

    private val _candles = MutableStateFlow<Map<String, List<Candle>>>(emptyMap())
    val candles: StateFlow<Map<String, List<Candle>>> = _candles

    private val _minutes = MutableStateFlow<Map<String, List<CurvePoint>>>(emptyMap())
    val minutes: StateFlow<Map<String, List<CurvePoint>>> = _minutes

    /** 已经开过请求的 key，失败也不自动重试，免得滚动列表时一直打网络。手点刷新才重来。 */
    private val chartAsked = mutableSetOf<String>()

    /**
     * 日 K 只拉一次就够用：资产走势、首页 sparkline 都读这一份缓存。
     * `force` 是用户点刷新的时候，重新拉一遍。
     */
    fun ensureDaily(stocks: List<Stock>, force: Boolean = false) {
        val targets = stocks.filter { stock ->
            force || chartAsked.add("day:" + stock.id)
        }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            val fetched = coroutineScope {
                targets.map { stock ->
                    async {
                        stock.id to runCatching { QuoteClient.fetchDaily(stock) }.getOrDefault(emptyList())
                    }
                }.awaitAll()
            }
            val hit = fetched.filter { it.second.isNotEmpty() }.toMap()
            if (hit.isNotEmpty()) _candles.value = _candles.value + hit
        }
    }

    /** 分时是当天的，进详情页拉一次；手点刷新会强制重拉。 */
    fun ensureMinute(stock: Stock, force: Boolean = false) {
        if (!force && !chartAsked.add("min:" + stock.id)) return
        viewModelScope.launch {
            val points = runCatching { QuoteClient.fetchMinute(stock) }.getOrDefault(emptyList())
            if (points.isNotEmpty()) _minutes.value = _minutes.value + (stock.id to points)
        }
    }

    /* ------------------------------ 数据 ------------------------------ */

    fun clearEntries() = store.update { LedgerOps.clearEntries(it) }

    fun resetAll() = store.update { LedgerOps.resetAll(it) }

    fun loadDemo() = store.replaceAll(LedgerOps.demo())

    fun importLedgerJson(text: String): ImportOutcome? {
        val incoming = store.parseJson(text) ?: return null
        val outcome = ImportEngine.merge(store.data.value, incoming)
        store.replaceAll(outcome.ledger)
        return outcome
    }

    fun exportJson(): String = store.exportJson()

    /* ------------------------------ 导入 ------------------------------ */

    suspend fun inspect(bytes: ByteArray, fileName: String): PickResult = withContext(Dispatchers.Default) {
        val text = runCatching { BillParser.decode(bytes) }.getOrDefault("")
        if (text.trimStart().startsWith("{")) {
            val incoming = store.parseJson(text)
                ?: return@withContext PickResult.Failed("这个 JSON 不像账本文件，读不出条目")
            return@withContext PickResult.LedgerFile(incoming, fileName)
        }
        val bill = BillParser.parseBytes(bytes)
        if (!bill.detected) {
            return@withContext PickResult.Failed("没认出这个文件的格式。目前支持微信账单 (.xlsx)、支付宝账单 (.csv)、iCost 导出和通用 CSV。")
        }
        PickResult.Bill(bill, ImportEngine.preview(store.data.value, bill, fileName))
    }

    fun commitImport(bill: ParsedBill, fileName: String): ImportOutcome {
        val outcome = ImportEngine.apply(store.data.value, bill, fileName)
        store.replaceAll(outcome.ledger)
        return outcome
    }

    fun commitLedger(incoming: Ledger): ImportOutcome {
        val outcome = ImportEngine.merge(store.data.value, incoming)
        store.replaceAll(outcome.ledger)
        return outcome
    }

    /* ------------------------------ 其它 ------------------------------ */

    fun setTheme(theme: String) = settings.setTheme(theme)

    fun setPalette(id: String) = settings.setPalette(id)

    fun setEditorAnim(id: String) = settings.setEditorAnim(id)

    /* ------------------------------- 图标 ------------------------------- */

    fun setIconStyle(id: String): Boolean = settings.setIconStyle(id)

    fun setIconMask(on: Boolean): Boolean = settings.setIconMask(on)

    /* --------------------------- 标题 / 备注整理 --------------------------- */

    /** 批量对调标题和备注；只改文本，不动金额与分类。 */
    fun swapTitles(ids: Set<String>) = store.update { LedgerOps.swapTitles(it, ids) }

    /** 记下「标题整理」里开着的那几个关键词。 */
    fun setSwapWords(words: Set<String>) = settings.setSwapWords(words)

    fun today(): String = Fmt.today()

    /** 退到后台时把内存里的账本立刻落盘，不等防抖。 */
    fun flush() = store.flushBlocking()

    override fun onCleared() {
        store.flushAsync()
        super.onCleared()
    }
}
