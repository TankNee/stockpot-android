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

    /** 分类 / 对方 / 备注里出现这些词，就算一笔退款。 */
    private val REFUND_WORDS = listOf("退款", "退货", "退回", "退还", "返现", "补偿")

    /** 理财口袋：收益算收入，但那是本金在生息。 */
    private val FUND_WORDS = listOf(
        "余额宝", "余利宝", "零钱通", "理财通", "基金", "理财", "收益", "分红", "利息",
    )

    /** 搬钱：换口袋、还账、充值提现，钱进来了但没人退东西。 */
    private val MOVING_WORDS = listOf("转入", "转出", "还款", "充值", "提现", "转账")

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

    fun deleteEntry(ledger: Ledger, id: String): Ledger {
        val removed = ledger.entries.firstOrNull { it.id == id } ?: return ledger
        val partner = ledger.entries.firstOrNull { it.id == removed.refundLinkId }
        val rest = ledger.entries.filterNot { it.id == id }
        if (partner == null) return ledger.copy(entries = rest)
        // 删的是退款行：原消费把这一笔退回来的钱吐回去，否则这笔账就凭空少了一块
        if (removed.kind != Kind.EXPENSE && partner.kind == Kind.EXPENSE) {
            val refunded = (partner.refundedAmount - removed.amount).coerceAtLeast(0)
            return ledger.copy(
                entries = rest.map { entry ->
                    if (entry.id == partner.id) {
                        entry.copy(
                            refundedAmount = refunded,
                            refundedAt = if (refunded == 0L) "" else entry.refundedAt,
                            refundLinkId = "",
                        )
                    } else {
                        entry
                    }
                },
            )
        }
        // 删的是原消费：退款行留着当一条独立记录，只是不再指着已经没了的行
        return ledger.copy(
            entries = rest.map { entry ->
                if (entry.id == partner.id) entry.copy(refundLinkId = "") else entry
            },
        )
    }

    /**
     * 批量把标题和备注对调。只碰这两个字段，所以不需要重排、也不会影响合计；
     * 备注是空的跳过（换过去等于把原标题弄丢）。
     */
    fun swapTitles(ledger: Ledger, ids: Set<String>): Ledger {
        if (ids.isEmpty()) return ledger
        return ledger.copy(
            entries = ledger.entries.map { entry ->
                if (entry.id in ids) TitleSwap.swapped(entry) else entry
            },
        )
    }

    private val entryOrder: Comparator<Entry> =
        compareByDescending<Entry> { it.date }
            .thenByDescending { it.time }
            .thenByDescending { it.createdAt }

    /* ------------------------------ 退款配对 ------------------------------ */

    /**
     * 把一条支出和一条退款绑起来。
     *
     * 两边的 `refundLinkId` 互相指认，支出那边同时把退款金额记进
     * `refundedAmount`（历史数据里已经折进去的部分不回退，取两者较大值），
     * 退款行标成不计入合计——钱只算一次，但两条都能点开看对面。
     *
     * 任一边原来挂着别的，先松开旧的，避免一条记录指着两个人。
     */
    fun linkRefund(ledger: Ledger, expenseId: String, refundId: String): Ledger {
        if (expenseId == refundId) return ledger
        val expense = ledger.entries.firstOrNull { it.id == expenseId } ?: return ledger
        val refund = ledger.entries.firstOrNull { it.id == refundId } ?: return ledger
        if (expense.kind != Kind.EXPENSE || refund.kind == Kind.EXPENSE) return ledger

        val oldRefund = ledger.entries.firstOrNull { it.id == expense.refundLinkId && it.id != refund.id }
        val oldExpenseId = refund.refundLinkId
        // 旧的那笔先从支出的已退总额里扣回来，再叠上新的这一笔
        val base = if (oldRefund == null) {
            expense.refundedAmount
        } else {
            (expense.refundedAmount - oldRefund.amount).coerceAtLeast(0)
        }
        val refunded = minOf(expense.amount, base + refund.amount).coerceAtLeast(0)

        val entries = ledger.entries.map { entry ->
            when (entry.id) {
                oldRefund?.id -> entry.copy(refundLinkId = "", excluded = false)
                oldExpenseId.takeIf { it.isNotEmpty() && it != expenseId } -> entry.copy(refundLinkId = "")
                expenseId -> entry.copy(
                    refundedAmount = refunded,
                    refundedAt = refund.date.ifEmpty { expense.refundedAt },
                    refundLinkId = refundId,
                )
                refundId -> entry.copy(refundLinkId = expenseId, excluded = true)
                else -> entry
            }
        }
        return ledger.copy(entries = entries.sortedWith(entryOrder))
    }

    /**
     * 松开一对退款关联：支出把这一笔的金额还回去，退款行重新当普通记录计入，
     * 净额前后一致（关联时钱退了一次，解除后又作为独立收入算了一次）。
     */
    fun unlinkRefund(ledger: Ledger, id: String): Ledger {
        val entry = ledger.entries.firstOrNull { it.id == id } ?: return ledger
        val partner = ledger.entries.firstOrNull { it.id == entry.refundLinkId }
        if (partner == null) {
            return ledger.copy(
                entries = ledger.entries.map { if (it.id == id) it.copy(refundLinkId = "") else it },
            )
        }
        val expense = if (entry.kind == Kind.EXPENSE) entry else partner
        val refund = if (expense.id == entry.id) partner else entry
        if (expense.kind != Kind.EXPENSE || refund.kind == Kind.EXPENSE) {
            return ledger.copy(
                entries = ledger.entries.map {
                    if (it.id == entry.id || it.id == partner.id) it.copy(refundLinkId = "") else it
                },
            )
        }
        val refunded = (expense.refundedAmount - refund.amount).coerceAtLeast(0)
        val entries = ledger.entries.map { row ->
            when (row.id) {
                expense.id -> row.copy(
                    refundedAmount = refunded,
                    refundedAt = if (refunded == 0L) "" else row.refundedAt,
                    refundLinkId = "",
                )
                refund.id -> row.copy(refundLinkId = "", excluded = false)
                else -> row
            }
        }
        return ledger.copy(entries = entries.sortedWith(entryOrder))
    }

    /**
     * 挑候选时金额放宽到哪一档。
     *
     * 以前只有「严 / 松」两个开关，严的那个其实是「退款不超过消费余额」，
     * 于是「只看金额对得上」的列表里全是十几块的零星退款，看着像没筛。
     */
    enum class RefundMatch(val id: String, val label: String, val hint: String) {
        /** 金额分毫不差：退回来多少，这笔就少花多少 */
        Exact("exact", "金额一致", "差一分都不出现"),
        /** 只允许少退：部分退款、手续费吃掉一点都在这档 */
        Partial("partial", "允许少退", "退的钱不超过这笔花的"),
        /** 都摆出来，自己认 */
        Any("any", "全部", "差多少会写在行里"),
        ;

        companion object {
            fun of(id: String): RefundMatch = entries.firstOrNull { it.id == id } ?: Exact
        }
    }

    /**
     * 手动配对时的候选：支出找退款行，退款行找支出。
     *
     * 已经配了别人的不出现（先解开再点）。金额按 [match] 放行，默认只认
     * 分毫不差的；剩下的按「金额是否正好 → 花得更多 → 日期更近」排，
     * 最可能是它排最前。日期不做硬过滤——跨月、跨季度的退款很常见，
     * 筛选交给页面上的时间范围。
     *
     * 退款去找消费时还有一条底线：那笔消费本身不能比退款还小，连「全部」
     * 档也一样。退两百块不可能是从五十块的那笔里退出来的。
     */
    fun refundCandidates(
        ledger: Ledger,
        entry: Entry,
        limit: Int = 24,
        match: RefundMatch = RefundMatch.Exact,
    ): List<Entry> {
        val pool = ledger.entries.filter { it.id != entry.id && !it.archived }
        val target = entry.kind == Kind.EXPENSE
        // 这笔还没退掉的余额：支出看 amount - refundedAmount，退款行就是它自己
        val remaining = entry.amount - entry.refundedAmount
        return pool.asSequence()
            .filter { candidate ->
                if (target) {
                    // 只认收入：余额宝、零钱通那种「收益」也是收入，但它是理财的口袋在动，
                    // 跟退款不是一回事，混进来就永远配不掉了
                    candidate.kind == Kind.INCOME && candidate.refundLinkId.isEmpty() && !isFundRow(candidate)
                } else {
                    candidate.kind == Kind.EXPENSE && !candidate.excluded && candidate.refundLinkId.isEmpty()
                }
            }
            .filter { candidate ->
                when (match) {
                    RefundMatch.Any -> if (target) {
                        true
                    } else {
                        // 退回 200 的那笔不可能出在只花了 50 的消费上。
                        // 「全部」只是把金额放宽，这条底线不能破，否则挑起来全是废选项。
                        candidate.amount >= entry.amount
                    }
                    // 支出这边：退款要正好等于这笔还没退的余额；
                    // 退款这边：挑的那笔消费，剩下的余额要正好等于这笔退款
                    RefundMatch.Exact -> if (target) {
                        candidate.amount == remaining
                    } else {
                        candidate.amount - candidate.refundedAmount == entry.amount
                    }
                    // 部分退款：退回来的比花掉的少，剩下的钱还是花了
                    RefundMatch.Partial -> if (target) {
                        candidate.amount <= remaining
                    } else {
                        candidate.amount - candidate.refundedAmount >= entry.amount
                    }
                }
            }
            .sortedWith(
                compareByDescending<Entry> { candidate ->
                    val exact = if (target) {
                        candidate.amount == remaining
                    } else {
                        candidate.amount - candidate.refundedAmount == entry.amount
                    }
                    if (exact) 1 else 0
                }
                    // 挑消费的时候，花得多的排前面：一笔 200 的退款更像来自 300 那笔，
                    // 而不是刚好也有 200 的零星小账。支出侧不看这个，全是 0。
                    .thenByDescending { if (target) 0L else it.amount }
                    .thenBy { distance(it.date, entry.date) },
            )
            .take(limit)
            .toList()
    }

    /**
     * 这条是不是「退款」那一侧。
     *
     * 账本里没有专门的是否退款字段，只能靠约定认。条款收紧过一次：
     * 以前是「只要不是支出、又标了不计入合计」就算，结果余额宝的收益发放、
     * 零钱通的转入也被当成待配对的退款堆在那一页上，金额永远凑不上，看着像坏账。
     * 现在必须是**收入**，而且得真的像退款：分类以「退款」开头、备注里写了退，
     * 或者已经配过对。理财类的一律不算——那是换个口袋放钱，不是钱退回来。
     */
    fun isRefundRow(entry: Entry): Boolean {
        if (entry.kind != Kind.INCOME) return false
        val hay = "${entry.category} ${entry.counterparty} ${entry.note}"
        if (isFundRow(entry)) return false
        return entry.refundLinkId.isNotEmpty() ||
            entry.category.startsWith("退款") ||
            REFUND_WORDS.any { hay.contains(it) }
    }

    /**
     * 理财口袋的钱：余额宝、零钱通、基金申赎……收益算收入，
     * 但那是本金在生息，不是哪笔消费退回来的。搬钱的（转入转出、还款、充值）
     * 也归进来，它们在账单里同样长得像「一笔进账」。
     */
    fun isFundRow(entry: Entry): Boolean {
        val hay = "${entry.category} ${entry.counterparty} ${entry.note}"
        return FUND_WORDS.any { hay.contains(it) } || MOVING_WORDS.any { hay.contains(it) }
    }

    /** 已配对的一对：支出 + 退款。 */
    data class RefundPair(val expense: Entry, val refund: Entry)

    /** 退款页那三组列表。 */
    data class RefundOverview(
        /** 还没配对的退款（只有收入类，理财进出的不算） */
        val pendingRefunds: List<Entry>,
        /** 还能退的支出（amount - refundedAmount > 0） */
        val openExpenses: List<Entry>,
        /** 已经配好的对 */
        val pairs: List<RefundPair>,
    )

    fun refundOverview(ledger: Ledger): RefundOverview {
        val live = ledger.entries.filter { !it.archived }
        val pending = live.filter { isRefundRow(it) && it.refundLinkId.isEmpty() }
            .sortedWith(entryOrder)
        val openExpenses = live.filter {
            it.kind == Kind.EXPENSE && !it.excluded && it.amount - it.refundedAmount > 0
        }.sortedWith(entryOrder)
        // 只从支出那一侧收一遍，省得同一对算两次
        val byId = live.associateBy { it.id }
        val pairs = live.asSequence()
            .filter { it.kind == Kind.EXPENSE && it.refundLinkId.isNotEmpty() }
            .mapNotNull { expense ->
                byId[expense.refundLinkId]?.let { RefundPair(expense, it) }
            }
            .sortedWith(compareByDescending<RefundPair> { it.refund.date })
            .toList()
        return RefundOverview(pending, openExpenses, pairs)
    }

    /**
     * 一键配对：只认「金额正好、日期最近且唯一」的那种，拿不准的留给人挑。
     *
     * 同一笔支出可能被退好几次，一笔一笔往账本里叠，剩下的余额不够就跳过；
     * 退款比余额还大也不自动配——那是要人来判断的账。
     */
    fun autoPairRefunds(ledger: Ledger, maxDays: Long = 120): Pair<Ledger, Int> {
        var next = ledger
        var linked = 0
        val pending = refundOverview(ledger).pendingRefunds.sortedBy { it.date }
        for (refund in pending) {
            // 只认分毫不差的：差一点的留给人在挑对面那页看
            val candidates = refundCandidates(next, refund, limit = 8, match = RefundMatch.Exact)
                .filter { distance(it.date, refund.date) <= maxDays }
            // 只有一个候选才动手；两个都能对上说明这笔账得人看
            val target = candidates.singleOrNull() ?: continue
            if (next.entries.firstOrNull { it.id == refund.id }?.refundLinkId?.isNotEmpty() == true) continue
            next = linkRefund(next, target.id, refund.id)
            linked++
        }
        return if (linked == 0) ledger to 0 else next to linked
    }

    /** 两个 `YYYY-MM-DD` 差多少天；解析不了就排到最后。 */
    private fun distance(a: String, b: String): Long = runCatching {
        kotlin.math.abs(
            java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(a), LocalDate.parse(b)),
        )
    }.getOrDefault(Long.MAX_VALUE / 2)

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
