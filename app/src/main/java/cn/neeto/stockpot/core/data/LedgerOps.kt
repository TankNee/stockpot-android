package cn.neeto.stockpot.core.data

import cn.neeto.stockpot.core.model.CategoryDef
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Holding
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Project
import cn.neeto.stockpot.core.model.Rule
import cn.neeto.stockpot.core.model.Source
import cn.neeto.stockpot.core.model.Stock
import cn.neeto.stockpot.core.model.StockAccount
import cn.neeto.stockpot.core.model.Wallet
import cn.neeto.stockpot.core.model.builtinIcon
import cn.neeto.stockpot.core.model.defaultCategories
import cn.neeto.stockpot.core.model.defaultRules
import java.time.LocalDate
import java.util.UUID

/** 手工录入一行的输入。 */
data class EntryInput(
    val date: String,
    val time: String = "",
    val kind: String = Kind.EXPENSE,
    val amount: Long,
    val category: String = "",
    val account: String = "",
    val counterparty: String = "",
    val note: String = "",
    val excluded: Boolean = false,
    val projectId: String = "",
)

/**
 * 账本的所有编辑操作都是纯函数：输入旧账本，返回新账本。
 * 存储层只负责把它落盘，测试可以直接断言结果。
 */
object LedgerOps {

    fun newId(prefix: String): String =
        prefix + "_" + UUID.randomUUID().toString().replace("-", "").take(18)

    fun newEntryId(): String = UUID.randomUUID().toString()

    fun entry(input: EntryInput): Entry = Entry(
        id = newEntryId(),
        date = input.date,
        time = input.time,
        kind = input.kind,
        amount = input.amount.coerceAtLeast(0),
        category = input.category,
        account = input.account.trim(),
        counterparty = input.counterparty.trim(),
        note = input.note.trim(),
        source = Source.MANUAL,
        excluded = input.excluded,
        projectId = input.projectId,
        createdAt = Fmt.stamp(),
    )

    fun withEntry(ledger: Ledger, entry: Entry): Ledger {
        val categories = learnCategory(ledger.categories, entry.category, entry.kind)
        val accounts = addAccount(ledger.accounts, entry.account)
        val entries = (ledger.entries + entry).sortedWith(entryOrder)
        return ledger.copy(entries = entries, categories = categories, accounts = accounts)
    }

    fun updateEntry(ledger: Ledger, entry: Entry): Ledger {
        val entries = ledger.entries.map { if (it.id == entry.id) entry else it }
            .sortedWith(entryOrder)
        return ledger.copy(
            entries = entries,
            categories = learnCategory(ledger.categories, entry.category, entry.kind),
            accounts = addAccount(ledger.accounts, entry.account),
        )
    }

    fun deleteEntry(ledger: Ledger, id: String): Ledger =
        ledger.copy(entries = ledger.entries.filterNot { it.id == id })

    private val entryOrder: Comparator<Entry> =
        compareByDescending<Entry> { it.date }
            .thenByDescending { it.time }
            .thenByDescending { it.createdAt }

    private fun addAccount(accounts: List<String>, account: String): List<String> {
        val name = account.trim()
        return if (name.isEmpty() || accounts.contains(name)) accounts else accounts + name
    }

    /* ------------------------------ 分类 ------------------------------ */

    /** 把 `组/叶` 注册进分类树，缺父级就补一个。 */
    fun learnCategory(categories: List<CategoryDef>, path: String, kind: String): List<CategoryDef> {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return categories
        val parent = trimmed.substringBefore('/', "")
        val leaf = trimmed.substringAfter('/', trimmed)
        if (parent.isEmpty()) {
            if (categories.any { it.name == leaf && it.parent.isEmpty() }) return categories
            return categories + CategoryDef(name = leaf, kind = kind, icon = builtinIcon(leaf))
        }
        var next = categories
        if (next.none { it.name == parent && it.parent.isEmpty() }) {
            next = next + CategoryDef(
                name = parent,
                kind = kind,
                icon = builtinIcon(parent),
            )
        }
        if (next.none { it.name == leaf && it.parent == parent }) {
            next = next + CategoryDef(
                name = leaf,
                parent = parent,
                kind = kind,
                icon = builtinIcon(parent),
            )
        }
        return next
    }

    fun addCategory(categories: List<CategoryDef>, name: String, parent: String, kind: String): List<CategoryDef> {
        val clean = name.trim()
        if (clean.isEmpty()) return categories
        if (categories.any { it.name == clean && it.parent == parent }) return categories
        var next = categories
        if (parent.isNotEmpty() && next.none { it.name == parent && it.parent.isEmpty() }) {
            next = next + CategoryDef(name = parent, kind = kind, icon = builtinIcon(parent))
        }
        return next + CategoryDef(
            name = clean,
            parent = parent,
            kind = kind,
            icon = builtinIcon(parent.ifEmpty { clean }),
        )
    }

    /** 改名/换父级时把账目和规则里的路径一起改掉，图表和筛选才不会指向不存在的分类。 */
    fun renameCategory(ledger: Ledger, old: CategoryDef, name: String, parent: String, kind: String): Ledger {
        val clean = name.trim()
        if (clean.isEmpty()) return ledger
        // 只允许两层：分组永远在顶层，子分类可以换分组
        val newParent = if (old.parent.isEmpty()) "" else parent
        val oldPath = old.path
        val newPath = if (newParent.isEmpty()) clean else "$newParent/$clean"
        val categories = ledger.categories.map { def ->
            when {
                def.parent == old.parent && def.name == old.name ->
                    def.copy(
                        name = clean,
                        parent = newParent,
                        kind = kind.ifEmpty { def.kind },
                        icon = def.icon.ifEmpty { builtinIcon(clean) },
                    )
                old.parent.isEmpty() && def.parent == old.name ->
                    def.copy(parent = clean, kind = kind.ifEmpty { def.kind })
                else -> def
            }
        }

        fun remap(path: String): String = when {
            path == oldPath -> newPath
            old.parent.isEmpty() && path.startsWith("$oldPath/") -> newPath + path.removePrefix(oldPath)
            else -> path
        }

        return ledger.copy(
            categories = categories,
            entries = ledger.entries.map { entry ->
                if (entry.category == oldPath || (old.parent.isEmpty() && entry.category.startsWith("$oldPath/"))) {
                    entry.copy(category = remap(entry.category))
                } else {
                    entry
                }
            },
            rules = ledger.rules.map { rule ->
                if (rule.category == oldPath || (old.parent.isEmpty() && rule.category.startsWith("$oldPath/"))) {
                    rule.copy(category = remap(rule.category))
                } else {
                    rule
                }
            },
        )
    }

    /** 删除分类：叶子回到父级分组，分组下的行回到「其他」。 */
    fun deleteCategory(ledger: Ledger, def: CategoryDef): Ledger {
        val fallback = if (def.parent.isEmpty()) "其他" else def.parent
        val categories = ledger.categories.filterNot {
            (it.parent == def.parent && it.name == def.name) ||
                (def.parent.isEmpty() && it.parent == def.name)
        }.let { remaining ->
            if (remaining.any { it.path == fallback }) remaining
            else remaining + CategoryDef(name = fallback, kind = "", icon = builtinIcon(fallback))
        }
        val entries = ledger.entries.map { entry ->
            if (entry.category == def.path || entry.category.startsWith(def.path + "/")) {
                entry.copy(category = fallback)
            } else {
                entry
            }
        }
        val rules = ledger.rules.map { rule ->
            if (rule.category == def.path || rule.category.startsWith(def.path + "/")) rule.copy(category = fallback) else rule
        }
        return ledger.copy(categories = categories, entries = entries, rules = rules)
    }

    /* ------------------------------ 规则 ------------------------------ */

    fun addRule(rules: List<Rule>, pattern: String, category: String): List<Rule> {
        val clean = pattern.trim()
        if (clean.isEmpty() || category.isEmpty()) return rules
        if (rules.any { it.pattern == clean && it.category == category }) return rules
        return rules + Rule(id = newId("rule"), pattern = clean, category = category)
    }

    fun updateRule(rules: List<Rule>, rule: Rule): List<Rule> =
        rules.map { if (it.id == rule.id) rule else it }

    fun deleteRule(rules: List<Rule>, id: String): List<Rule> = rules.filterNot { it.id == id }

    /* ------------------------------ 项目 ------------------------------ */

    fun addProject(projects: List<Project>, name: String, note: String, start: String, end: String): Pair<List<Project>, String> {
        val clean = name.trim()
        if (clean.isEmpty()) return projects to ""
        val project = Project(
            id = newId("prj"),
            name = clean,
            note = note.trim(),
            startDate = start,
            endDate = end,
            createdAt = Fmt.stamp(),
        )
        return (projects + project) to project.id
    }

    fun updateProject(projects: List<Project>, project: Project): List<Project> =
        projects.map { if (it.id == project.id) project else it }

    /** 删除项目只解除分组，账目一条都不会少。 */
    fun deleteProject(ledger: Ledger, id: String): Ledger = ledger.copy(
        projects = ledger.projects.filterNot { it.id == id },
        entries = ledger.entries.map { if (it.projectId == id) it.copy(projectId = "") else it },
    )

    fun attach(ledger: Ledger, projectId: String, ids: Set<String>): Ledger = ledger.copy(
        entries = ledger.entries.map { if (it.id in ids) it.copy(projectId = projectId) else it },
    )

    fun detach(ledger: Ledger, ids: Set<String>): Ledger = ledger.copy(
        entries = ledger.entries.map { if (it.id in ids) it.copy(projectId = "") else it },
    )

    /* ------------------------------ 钱包 ------------------------------ */

    fun addWallet(wallets: List<Wallet>, draft: Wallet): List<Wallet> {
        val clean = draft.name.trim()
        if (clean.isEmpty()) return wallets
        return wallets + draft.copy(
            id = if (draft.id.isEmpty()) newId("wlt") else draft.id,
            name = clean,
            institution = draft.institution.trim(),
            note = draft.note.trim(),
            updatedAt = Fmt.stamp(),
        )
    }

    fun updateWallet(wallets: List<Wallet>, wallet: Wallet): List<Wallet> =
        wallets.map { if (it.id == wallet.id) wallet.copy(updatedAt = Fmt.stamp()) else it }

    fun deleteWallet(wallets: List<Wallet>, id: String): List<Wallet> = wallets.filterNot { it.id == id }

    /* ------------------------------ 股票 ------------------------------ */

    fun addStockAccount(accounts: List<StockAccount>, name: String): List<StockAccount> {
        val clean = name.trim()
        if (clean.isEmpty()) return accounts
        return accounts + StockAccount(id = newId("sacc"), name = clean, updatedAt = Fmt.stamp())
    }

    fun updateStockAccount(accounts: List<StockAccount>, account: StockAccount): List<StockAccount> =
        accounts.map { if (it.id == account.id) account.copy(updatedAt = Fmt.stamp()) else it }

    fun deleteStockAccount(accounts: List<StockAccount>, id: String): List<StockAccount> =
        accounts.filterNot { it.id == id }

    /** 股票池按 id 合并：同一只票只留一条，名字以传入的为准。 */
    fun upsertStock(stocks: List<Stock>, stock: Stock): List<Stock> =
        if (stocks.any { it.id == stock.id }) {
            stocks.map { if (it.id == stock.id) stock.copy(name = stock.name.ifEmpty { it.name }) else it }
        } else {
            stocks + stock
        }

    fun putHolding(account: StockAccount, stockId: String, holding: Holding): StockAccount =
        account.copy(entries = account.entries + (stockId to holding), updatedAt = Fmt.stamp())

    fun removeHolding(account: StockAccount, stockId: String): StockAccount =
        account.copy(entries = account.entries - stockId, updatedAt = Fmt.stamp())

    /** 行情回来时把股票名补齐，用户添加时只输了代码也能显示中文名。 */
    fun fillStockNames(stocks: List<Stock>, names: Map<String, String>): List<Stock> =
        stocks.map { stock ->
            val fresh = names[stock.id]
            if (fresh.isNullOrBlank() || fresh == stock.name) stock else stock.copy(name = fresh)
        }

    /** 股票池里已经没人持有的票清掉，避免下拉里堆垃圾。 */
    fun pruneStocks(stocks: List<Stock>, accounts: List<StockAccount>): List<Stock> {
        val used = accounts.flatMap { it.entries.filter { e -> e.value.shares > 0 }.keys }.toSet()
        return stocks.filter { it.id in used }
    }

    /* ------------------------------ 其它 ------------------------------ */

    fun clearEntries(ledger: Ledger): Ledger = ledger.copy(entries = emptyList(), imports = emptyList())

    /** 只恢复内置分类与规则，账目、项目、钱包都留着。 */
    fun resetAll(ledger: Ledger): Ledger = ledger.copy(
        categories = defaultCategories(),
        rules = defaultRules(),
    )

    /** 首次安装/点了「示例数据」时给一份像样的账，方便立刻看到图表。 */
    fun demo(): Ledger {
        var ledger = Ledger(categories = defaultCategories(), rules = defaultRules())
        val today = LocalDate.now()
        val projectId = newId("prj")

        data class Row(
            val dayOffset: Long,
            val amount: Long,
            val kind: String,
            val category: String,
            val counterparty: String,
            val account: String,
            val note: String = "",
            val time: String = "",
        )

        val rows = listOf(
            Row(0, 1850, Kind.EXPENSE, "餐饮/午餐", "楼下小馆", "微信零钱", time = "12:20"),
            Row(0, 2680, Kind.EXPENSE, "交通/打车", "滴滴出行", "微信零钱", time = "09:05"),
            Row(0, 3200, Kind.EXPENSE, "餐饮/咖啡", "瑞幸咖啡", "微信零钱", time = "08:40"),
            Row(-1, 12800, Kind.EXPENSE, "购物/日用", "盒马鲜生", "支付宝", time = "19:12"),
            Row(-1, 4500, Kind.EXPENSE, "餐饮/外卖", "美团外卖", "微信零钱", time = "12:31"),
            Row(-2, 6800, Kind.EXPENSE, "娱乐/影音", "万达影城", "支付宝", time = "20:05"),
            Row(-2, 2200, Kind.EXPENSE, "交通/地铁公交", "广州地铁", "羊城通", time = "08:12"),
            Row(-3, 268000, Kind.EXPENSE, "居住/房租", "房东", "招商银行", "11 月房租", "10:00"),
            Row(-3, 15800, Kind.EXPENSE, "居住/水电燃气", "广州供电局", "支付宝", "电费", "21:30"),
            Row(-4, 5600, Kind.EXPENSE, "医疗/药品", "大参林药房", "微信零钱", time = "18:20"),
            Row(-4, 3900, Kind.EXPENSE, "餐饮/晚餐", "太二酸菜鱼", "微信零钱", time = "19:40"),
            Row(-5, 19900, Kind.EXPENSE, "购物/服饰", "优衣库", "支付宝", time = "15:22"),
            Row(-5, 1500, Kind.EXPENSE, "交通/地铁公交", "广州地铁", "羊城通", time = "08:02"),
            Row(-6, 8800, Kind.EXPENSE, "娱乐/旅行", "携程旅行", "招商银行", "周末短途", "22:11"),
            Row(-7, 4500, Kind.EXPENSE, "餐饮/咖啡", "星巴克", "微信零钱", time = "10:15"),
            Row(-8, 12600, Kind.EXPENSE, "购物/日用", "永辉超市", "支付宝", time = "17:48"),
            Row(-9, 3000, Kind.EXPENSE, "通讯/话费", "中国移动", "支付宝", time = "09:00"),
            Row(-10, 24000, Kind.EXPENSE, "教育/书籍", "当当网", "支付宝", "两本技术书", time = "21:02"),
            Row(-11, 6600, Kind.EXPENSE, "人情/红包", "同事婚礼", "微信零钱", "份子钱", "11:30"),
            Row(-12, 3600, Kind.EXPENSE, "餐饮/早餐", "肠粉店", "微信零钱", time = "08:05"),
            Row(-13, 25900, Kind.EXPENSE, "购物/数码", "小米之家", "招商银行", "充电宝", "16:40"),
            Row(-14, 7200, Kind.EXPENSE, "餐饮/晚餐", "潮汕牛肉火锅", "微信零钱", time = "19:55"),
            Row(-16, 4200, Kind.EXPENSE, "医疗", "社区医院", "医保卡", "感冒", "10:26"),
            Row(-18, 15500, Kind.EXPENSE, "娱乐/影音", "网易云音乐", "支付宝", "年费", "00:20"),
            Row(-20, 21900, Kind.EXPENSE, "购物/日用", "名创优品", "支付宝", time = "20:07"),
            Row(-22, 6100, Kind.EXPENSE, "餐饮/外卖", "饿了么", "微信零钱", time = "12:44"),
            Row(-25, 3800, Kind.EXPENSE, "交通/加油", "中石化", "招商银行", time = "18:30"),
            Row(-28, 9000, Kind.EXPENSE, "娱乐", "健身房月卡", "支付宝", time = "19:00"),
            Row(-2, 2856000, Kind.INCOME, "工资/基本工资", "公司代发", "招商银行", "11 月工资", "10:05"),
            Row(-4, 12800, Kind.INCOME, "理财收益", "余额宝", "支付宝", "收益发放", "06:00"),
            Row(-12, 8800, Kind.INCOME, "其他", "闲鱼", "支付宝", "卖旧键盘", "13:20"),
            Row(-30, 2750000, Kind.INCOME, "工资/基本工资", "公司代发", "招商银行", "10 月工资", "10:05"),
        )

        for (row in rows) {
            val date = today.plusDays(row.dayOffset)
            ledger = withEntry(
                ledger,
                entry(
                    EntryInput(
                        date = date.toString(),
                        time = row.time,
                        kind = row.kind,
                        amount = row.amount,
                        category = row.category,
                        account = row.account,
                        counterparty = row.counterparty,
                        note = row.note,
                    ),
                ),
            )
        }

        // 一条「出差」项目，演示项目只是给账目加一层分组
        val trip = Project(
            id = projectId,
            name = "杭州出差",
            note = "三天，含交通和住宿",
            startDate = today.minusDays(10).toString(),
            endDate = today.minusDays(8).toString(),
            createdAt = Fmt.stamp(),
        )
        val tripIds = ledger.entries.filter { it.date >= trip.startDate && it.date <= trip.endDate }
            .take(4)
            .map { it.id }
            .toSet()

        ledger = ledger.copy(
            projects = listOf(trip),
            entries = ledger.entries.map { if (it.id in tripIds) it.copy(projectId = projectId) else it },
            wallets = listOf(
                Wallet(id = newId("wlt"), name = "招商银行", kind = "debit", institution = "招商银行", balance = 4_286_500, updatedAt = Fmt.stamp()),
                Wallet(id = newId("wlt"), name = "微信零钱", kind = "cash", institution = "微信支付", balance = 126_800, updatedAt = Fmt.stamp()),
                Wallet(id = newId("wlt"), name = "信用卡", kind = "credit", institution = "广发银行", balance = -238_600, updatedAt = Fmt.stamp()),
            ),
        )
        return ledger
    }
}
