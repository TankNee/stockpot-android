package cn.neeto.stockpot.core.importer

import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.data.LedgerOps
import cn.neeto.stockpot.core.model.CategoryDef
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.ImportRecord
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Rule
import cn.neeto.stockpot.core.model.Source
import java.time.LocalDate

/** 关键词归类：最长命中的规则胜出，方向不符的规则跳过。 */
object Categorize {

    fun matchRule(rules: List<Rule>, categories: List<CategoryDef>, text: String, kind: String): Rule? {
        val haystack = text.lowercase()
        return rules
            .filter { rule ->
                if (rule.pattern.isBlank() || rule.category.isBlank()) return@filter false
                if (!haystack.contains(rule.pattern.lowercase())) return@filter false
                if (kind != Kind.EXPENSE && kind != Kind.INCOME) return@filter true
                val target = categoryKind(categories, rule.category)
                target.isEmpty() || target == kind
            }
            .maxByOrNull { it.pattern.length }
    }

    fun categoryKind(categories: List<CategoryDef>, path: String): String {
        val direct = categories.firstOrNull { it.path == path }?.kind.orEmpty()
        if (direct.isNotEmpty()) return direct
        if (!path.contains('/')) return ""
        val group = path.substringBefore('/')
        return categories.firstOrNull { it.name == group && it.parent.isEmpty() }?.kind.orEmpty()
    }

    /**
     * 平台自己的分类（支付宝写「交通出行」「餐饮美食」）比关键词更可信；
     * 其余靠关键词，都不中就归「其他」。故意保守：猜错比猜泛更糟。
     */
    fun guess(text: String, kind: String, hint: String): String {
        val hinted = when {
            hint.contains("交通") -> "交通"
            hint.contains("餐饮") || hint.contains("美食") -> "餐饮"
            hint.contains("购物") || hint.contains("日用") -> "购物"
            hint.contains("居住") || hint.contains("生活") -> "居住"
            hint.contains("娱乐") || hint.contains("文化") -> "娱乐"
            hint.contains("医疗") || hint.contains("健康") -> "医疗"
            hint.contains("教育") || hint.contains("学习") -> "教育"
            hint.contains("通讯") || hint.contains("通信") -> "通讯"
            hint.contains("转账") || hint.contains("红包") || hint.contains("人情") -> "人情"
            hint.contains("美容") -> "其他"
            else -> null
        }
        if (hinted != null) return hinted

        fun has(vararg needles: String) = needles.any { text.contains(it, ignoreCase = true) }

        if (kind == Kind.INCOME) {
            if (has("工资", "薪资", "代发", "劳务")) return "工资"
            if (has("退款", "报销", "红包", "转入")) return "其他"
            if (has("利息", "基金", "理财", "收益", "余额宝")) return "理财收益"
            return "其他"
        }
        if (has("餐", "饭", "美食", "咖啡", "coffee", "luckin", "奶茶", "星巴克", "麦当劳", "肯德基", "外卖", "饿了么", "美团", "小吃", "烧烤", "火锅", "茶", "饼", "堂食", "面馆")) return "餐饮"
        if (has("地铁", "公交", "出租", "滴滴", "高铁", "火车", "机票", "航空", "加油", "停车", "充电", "通行费", "高速", "打车", "ETC")) return "交通"
        if (has("淘宝", "天猫", "京东", "拼多多", "超市", "便利店", "商场", "购物", "小米", "苹果", "商店")) return "购物"
        if (has("话费", "宽带", "通讯")) return "通讯"
        if (has("房租", "水费", "电费", "燃气", "物业")) return "居住"
        if (has("电影", "游戏", "视频", "音乐", "会员", "Steam", "演出", "门票", "景区")) return "娱乐"
        if (has("医院", "药", "诊所", "体检", "医保")) return "医疗"
        if (has("学费", "培训", "课程", "书店", "图书", "教育")) return "教育"
        if (has("红包", "转账", "礼金", "份子")) return "人情"
        return "其他"
    }
}

data class PreviewSample(
    val date: String,
    val time: String,
    val kind: String,
    val amount: Long,
    val counterparty: String,
    val account: String,
    val note: String,
)

data class ImportPreview(
    val source: String,
    val rows: Int,
    val duplicates: Int,
    val ignored: Int,
    val expense: Long,
    val income: Long,
    val firstDate: String,
    val lastDate: String,
    val sample: List<PreviewSample>,
    val fileName: String,
)

data class ImportOutcome(
    val ledger: Ledger,
    val added: Int,
    val duplicates: Int,
    val ignored: Int,
    val refundsLinked: Int,
    val source: String,
)

object ImportEngine {

    fun duplicateKey(source: String, externalId: String, date: String, amount: Long, counterparty: String): String =
        if (externalId.isNotEmpty()) "id:$externalId" else "$source:$date:$amount:$counterparty"

    private fun keyOf(entry: Entry): String =
        duplicateKey(entry.source, entry.externalId, entry.date, entry.amount, entry.counterparty)

    /** 只看不改：导入弹窗靠它告诉用户会发生什么。 */
    fun preview(ledger: Ledger, bill: ParsedBill, fileName: String): ImportPreview {
        val seen = ledger.entries.map { keyOf(it) }.toHashSet()
        val fresh = HashSet<String>()
        var duplicates = 0
        var expense = 0L
        var income = 0L
        val dates = ArrayList<String>()
        val sample = ArrayList<PreviewSample>()

        for (row in bill.rows) {
            val key = duplicateKey(bill.source, row.externalId, row.date, row.amount, row.counterparty)
            if (seen.contains(key) || !fresh.add(key)) {
                duplicates++
                continue
            }
            if (row.kind == Kind.EXPENSE) expense += row.amount
            if (row.kind == Kind.INCOME) income += row.amount
            if (row.date.isNotEmpty()) dates += row.date
            if (sample.size < 6) {
                sample += PreviewSample(
                    date = row.date,
                    time = row.time,
                    kind = row.kind,
                    amount = row.amount,
                    counterparty = row.counterparty,
                    account = row.account,
                    note = row.note,
                )
            }
        }

        return ImportPreview(
            source = bill.source,
            rows = bill.rows.size,
            duplicates = duplicates,
            ignored = bill.ignored,
            expense = expense,
            income = income,
            firstDate = dates.minOrNull().orEmpty(),
            lastDate = dates.maxOrNull().orEmpty(),
            sample = sample,
            fileName = fileName,
        )
    }

    fun apply(ledger: Ledger, bill: ParsedBill, fileName: String): ImportOutcome {
        var next = ledger
        val seen = ledger.entries.map { keyOf(it) }.toHashSet()
        var added = 0
        var duplicates = 0
        var refundsLinked = 0
        val pendingRefunds = ArrayList<ParsedRow>()
        val newEntries = ArrayList<Entry>()

        // 退款先收起来：退款行可能出现在它对应的消费之前
        for (row in bill.rows) {
            val key = duplicateKey(bill.source, row.externalId, row.date, row.amount, row.counterparty)
            if (row.refundIncoming) {
                if (!seen.add(key)) duplicates++ else pendingRefunds += row
                continue
            }
            if (!seen.add(key)) {
                duplicates++
                continue
            }

            val refunded = when {
                !row.selfRefunded -> 0L
                row.refundedAmount > 0 -> minOf(row.refundedAmount, row.amount)
                else -> row.amount
            }

            val category = when {
                bill.source == Source.ICOST && row.category.isNotEmpty() &&
                    ledger.categories.any { it.path == row.category } -> row.category
                bill.source == Source.ICOST -> Categorize.matchRule(ledger.rules, ledger.categories, row.haystack, row.kind)
                    ?.category ?: if (row.kind == Kind.TRANSFER) "转账" else "其他"
                row.kind == Kind.TRANSFER -> "转账"
                else -> Categorize.matchRule(ledger.rules, ledger.categories, row.haystack, row.kind)
                    ?.category ?: Categorize.guess(row.haystack, row.kind, row.hint)
            }

            newEntries += Entry(
                id = LedgerOps.newEntryId(),
                date = row.date,
                time = row.time,
                kind = row.kind,
                amount = row.amount,
                category = category,
                account = row.account,
                counterparty = row.counterparty,
                note = row.note,
                source = bill.source,
                externalId = row.externalId,
                refundedAmount = refunded,
                refundedAt = if (row.selfRefunded) row.date else "",
                createdAt = Fmt.stamp(),
            )
            added++
        }

        // 退款行：找同一平台上 45 天内、余额足够的消费
        val all = (ledger.entries + newEntries).toMutableList()
        for (refund in pendingRefunds) {
            val target = findRefundTarget(all, bill.source, refund)
            if (target != null) {
                val index = all.indexOfFirst { it.id == target.id }
                if (index >= 0) {
                    val entry = all[index]
                    all[index] = entry.copy(
                        refundedAmount = minOf(entry.amount, entry.refundedAmount + refund.amount),
                        refundedAt = refund.date,
                    )
                    refundsLinked++
                }
            } else {
                // 找不到原始消费：保留这条资金流动，但不计入收支
                all += Entry(
                    id = LedgerOps.newEntryId(),
                    date = refund.date,
                    time = refund.time,
                    kind = Kind.TRANSFER,
                    amount = refund.amount,
                    category = refund.category,
                    account = refund.account,
                    counterparty = refund.counterparty,
                    note = refund.note,
                    source = bill.source,
                    externalId = refund.externalId,
                    excluded = true,
                    createdAt = Fmt.stamp(),
                )
                added++
            }
        }

        next = next.copy(
            entries = all.sortedWith(
                compareByDescending<Entry> { it.date }
                    .thenByDescending { it.time }
                    .thenByDescending { it.createdAt },
            ),
        )

        // 导入可能带来还没登记的标签
        for (entry in next.entries) {
            next = next.copy(categories = LedgerOps.learnCategory(next.categories, entry.category, entry.kind))
        }
        for (entry in next.entries) {
            if (entry.account.isNotEmpty() && !next.accounts.contains(entry.account)) {
                next = next.copy(accounts = next.accounts + entry.account)
            }
        }

        next = next.copy(
            imports = listOf(
                ImportRecord(
                    id = LedgerOps.newId("imp"),
                    file = fileName,
                    source = bill.source,
                    added = added,
                    skipped = duplicates + bill.ignored,
                    importedAt = Fmt.stamp(),
                ),
            ) + next.imports,
        )

        return ImportOutcome(
            ledger = next,
            added = added,
            duplicates = duplicates,
            ignored = bill.ignored,
            refundsLinked = refundsLinked,
            source = bill.source,
        )
    }

    private fun findRefundTarget(entries: List<Entry>, source: String, refund: ParsedRow): Entry? {
        val refundDate = runCatching { LocalDate.parse(refund.date) }.getOrNull() ?: return null
        var best: Entry? = null
        var bestExact = Int.MAX_VALUE
        var bestGap = Long.MAX_VALUE
        for (entry in entries) {
            if (entry.source != source || entry.kind != Kind.EXPENSE || entry.excluded) continue
            val remaining = entry.amount - entry.refundedAmount
            if (remaining < refund.amount || remaining <= 0) continue
            val entryDate = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: continue
            val days = java.time.temporal.ChronoUnit.DAYS.between(entryDate, refundDate)
            if (days < 0 || days > 45) continue
            val exact = if (remaining == refund.amount) 0 else 1
            if (exact < bestExact || (exact == bestExact && days < bestGap)) {
                best = entry
                bestExact = exact
                bestGap = days
            }
        }
        return best
    }

    /**
     * 把另一份 `ledger.json`（比如桌面端的）并进来。
     * 按 id 和去重键双重过滤，重复导入同一份文件不会产生重复行。
     */
    fun merge(current: Ledger, incoming: Ledger): ImportOutcome {
        val knownIds = current.entries.map { it.id }.toHashSet()
        val knownKeys = current.entries.map { keyOf(it) }.toHashSet()
        val addedEntries = ArrayList<Entry>()
        var duplicates = 0
        for (entry in incoming.entries) {
            if (entry.id in knownIds || keyOf(entry) in knownKeys) {
                duplicates++
                continue
            }
            knownIds += entry.id
            knownKeys += keyOf(entry)
            addedEntries += entry
        }

        var next = current.copy(
            entries = (current.entries + addedEntries).sortedWith(
                compareByDescending<Entry> { it.date }
                    .thenByDescending { it.time }
                    .thenByDescending { it.createdAt },
            ),
        )
        for (entry in addedEntries) {
            next = next.copy(categories = LedgerOps.learnCategory(next.categories, entry.category, entry.kind))
        }

        val categoryPaths = next.categories.map { it.path }.toHashSet()
        val newCategories = incoming.categories.filter { it.path !in categoryPaths }
        val ruleKeys = next.rules.map { it.pattern + "\u0000" + it.category }.toHashSet()
        val newRules = incoming.rules.filter { (it.pattern + "\u0000" + it.category) !in ruleKeys }
        val projectIds = next.projects.map { it.id }.toHashSet()
        val walletIds = next.wallets.map { it.id }.toHashSet()
        val importIds = next.imports.map { it.id }.toHashSet()
        val accounts = (next.accounts + incoming.accounts).distinct()

        next = next.copy(
            categories = next.categories + newCategories,
            rules = next.rules + newRules,
            projects = next.projects + incoming.projects.filter { it.id !in projectIds },
            wallets = next.wallets + incoming.wallets.filter { it.id !in walletIds },
            imports = next.imports + incoming.imports.filter { it.id !in importIds },
            accounts = accounts,
        )

        return ImportOutcome(
            ledger = next,
            added = addedEntries.size,
            duplicates = duplicates,
            ignored = 0,
            refundsLinked = 0,
            source = Source.IMPORTED,
        )
    }
}
