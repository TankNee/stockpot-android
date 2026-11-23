package cn.neeto.stockpot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.neeto.stockpot.PotApp
import cn.neeto.stockpot.core.data.EntryInput
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.data.LedgerOps
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
import cn.neeto.stockpot.core.stock.QuoteClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
