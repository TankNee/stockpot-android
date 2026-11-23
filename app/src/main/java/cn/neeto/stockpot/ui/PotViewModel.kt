package cn.neeto.stockpot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import cn.neeto.stockpot.PotApp
import cn.neeto.stockpot.core.data.EntryInput
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.data.LedgerOps
import cn.neeto.stockpot.core.importer.BillParser
import cn.neeto.stockpot.core.importer.ImportEngine
import cn.neeto.stockpot.core.importer.ImportOutcome
import cn.neeto.stockpot.core.importer.ImportPreview
import cn.neeto.stockpot.core.importer.ParsedBill
import cn.neeto.stockpot.core.model.CategoryDef
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Project
import cn.neeto.stockpot.core.model.Rule
import cn.neeto.stockpot.core.model.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
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

    fun restoreEntry(entry: Entry) = store.update { LedgerOps.withEntry(it, entry) }

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

    fun addWallet(name: String, kind: String, institution: String, balance: Long) = store.update {
        it.copy(wallets = LedgerOps.addWallet(it.wallets, name, kind, institution, balance))
    }

    fun updateWallet(wallet: Wallet) = store.update {
        it.copy(wallets = LedgerOps.updateWallet(it.wallets, wallet))
    }

    fun deleteWallet(id: String) = store.update {
        it.copy(wallets = LedgerOps.deleteWallet(it.wallets, id))
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

    fun today(): String = Fmt.today()

    /** 退到后台时把内存里的账本立刻落盘，不等防抖。 */
    fun flush() = store.flushBlocking()

    override fun onCleared() {
        store.flushAsync()
        super.onCleared()
    }
}
