package cn.neeto.stockpot.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.contentOrNull

/**
 * 账本模型。字段名与桌面端 `ledger.json` 完全一致（camelCase），
 * 因此手机导出的文件可以直接丢回桌面端打开，反过来也一样。
 *
 * 金额一律用 **分**（Long）存储：浮点元在几千行汇总后必然漂移，
 * 差一分钱的账本没人敢信。
 */

object Kind {
    const val EXPENSE = "expense"
    const val INCOME = "income"
    const val TRANSFER = "transfer"
}

object Source {
    const val MANUAL = "manual"
    const val WECHAT = "wechat"
    const val ALIPAY = "alipay"
    const val ICOST = "icost"
    const val GENERIC = "generic"
    const val IMPORTED = "imported"
}

@Serializable
@Immutable
data class Entry(
    val id: String,
    /** `YYYY-MM-DD` */
    val date: String,
    /** `HH:MM`，手工记账通常为空 */
    val time: String = "",
    /** expense | income | transfer */
    val kind: String = Kind.EXPENSE,
    /** 分，永远为正 */
    val amount: Long = 0,
    val category: String = "",
    val account: String = "",
    val counterparty: String = "",
    val note: String = "",
    val source: String = Source.MANUAL,
    /** 平台交易单号，用于重复导入去重 */
    val externalId: String = "",
    /** 不计入合计：退款行、账户互转 */
    val excluded: Boolean = false,
    /** 被整理功能收起的行：仍保留在文件里，但不出现在列表和合计中 */
    val archived: Boolean = false,
    /** 已退回的金额（分），实际成本 = amount - refundedAmount */
    val refundedAmount: Long = 0,
    val refundedAt: String = "",
    /** 人工确认过的分类，自动重排不会覆盖 */
    val categoryLocked: Boolean = false,
    val projectId: String = "",
    val createdAt: String = "",
) {
    /** 计入统计时这一行真正花掉的钱。 */
    val effectiveAmount: Long
        get() = if (kind == Kind.EXPENSE) (amount - refundedAmount).coerceAtLeast(0) else amount

    val countsTowardTotals: Boolean
        get() = !excluded && !archived
}

@Serializable
@Immutable
data class CategoryDef(
    val name: String,
    /** 空表示顶级分类 */
    val parent: String = "",
    /** expense | income | 空（两者皆可） */
    val kind: String = "",
    val icon: String = "",
    val description: String = "",
) {
    val path: String
        get() = if (parent.isEmpty()) name else "$parent/$name"

    val isGroup: Boolean get() = parent.isEmpty()
}

/**
 * 老账本里分类是一个纯字符串数组（`["餐饮", "交通"]`），新格式是对象。
 * 两种都读得进来。
 */
object CategoryListSerializer :
    JsonTransformingSerializer<List<CategoryDef>>(ListSerializer(CategoryDef.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonArray) {
            JsonArray(
                element.map { item ->
                    (item as? JsonPrimitive)?.contentOrNull
                        ?.let { JsonObject(mapOf("name" to JsonPrimitive(it))) }
                        ?: item
                },
            )
        } else {
            element
        }
}

@Serializable
@Immutable
data class Rule(
    val id: String,
    val pattern: String,
    val category: String,
)

@Serializable
@Immutable
data class Project(
    val id: String,
    val name: String,
    val note: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val archived: Boolean = false,
    val createdAt: String = "",
)

@Serializable
@Immutable
data class Wallet(
    val id: String,
    val name: String,
    /** debit | credit | cash | investment | pension | provident | medical | other */
    val kind: String = "debit",
    val institution: String = "",
    /** 分。信用卡把欠款记成负数，净资产就不用特判 */
    val balance: Long = 0,
    val note: String = "",
    /** 关掉之后余额只在本页显示，不进净资产和资产构成 */
    val countInAssets: Boolean = true,
    /** 记账时的账户候选里是否出现 */
    val selectable: Boolean = true,
    val updatedAt: String = "",
)

@Serializable
@Immutable
data class ImportRecord(
    val id: String,
    val file: String,
    val source: String,
    val added: Int,
    val skipped: Int,
    val importedAt: String,
)

@Serializable
@Immutable
data class Ledger(
    val entries: List<Entry> = emptyList(),
    @Serializable(with = CategoryListSerializer::class)
    val categories: List<CategoryDef> = emptyList(),
    val accounts: List<String> = emptyList(),
    val imports: List<ImportRecord> = emptyList(),
    val wallets: List<Wallet> = emptyList(),
    val projects: List<Project> = emptyList(),
    val rules: List<Rule> = emptyList(),
    val ignoredDuplicates: List<String> = emptyList(),
    val ignoredRepayments: List<String> = emptyList(),
    /** 股票池与券商账户，结构对着桌面端的 stockpot.json */
    val stocks: List<Stock> = emptyList(),
    val stockAccounts: List<StockAccount> = emptyList(),
    val schemaVersion: Int = SCHEMA_VERSION,
) {
    fun categoryKind(path: String): String {
        val direct = categories.firstOrNull { it.path == path }?.kind.orEmpty()
        if (direct.isNotEmpty()) return direct
        val group = path.substringBefore('/')
        return categories.firstOrNull { it.name == group && it.parent.isEmpty() }?.kind.orEmpty()
    }

    fun projectOf(entry: Entry): Project? =
        if (entry.projectId.isEmpty()) null else projects.firstOrNull { it.id == entry.projectId }

    /** 分类树：支出分组在前、通用居中、收入最后，叶子跟在父级后面。 */
    fun categoryTree(): List<Pair<CategoryDef, List<CategoryDef>>> =
        categories.filter { it.parent.isEmpty() }
            .sortedBy { def -> when (def.kind) { Kind.EXPENSE -> 0; "" -> 1; else -> 2 } }
            .map { group -> group to categories.filter { it.parent == group.name } }

    companion object {
        const val SCHEMA_VERSION = 4
    }
}

/** 顶级分类的默认图标键，UI 把它映射成字形。 */
fun builtinIcon(name: String): String = when (name) {
    "餐饮" -> "food"
    "交通" -> "transport"
    "购物" -> "shopping"
    "居住" -> "home"
    "娱乐" -> "fun"
    "医疗" -> "health"
    "教育" -> "book"
    "通讯" -> "phone"
    "人情" -> "gift"
    "其他" -> "dots"
    "工资" -> "salary"
    "理财" -> "invest"
    "转账" -> "transfer"
    "退款" -> "refund"
    else -> ""
}

private val DEFAULT_CATEGORIES = listOf(
    Triple("餐饮", "", "expense"),
    Triple("早餐", "餐饮", "expense"),
    Triple("午餐", "餐饮", "expense"),
    Triple("晚餐", "餐饮", "expense"),
    Triple("咖啡", "餐饮", "expense"),
    Triple("外卖", "餐饮", "expense"),
    Triple("交通", "", "expense"),
    Triple("地铁公交", "交通", "expense"),
    Triple("打车", "交通", "expense"),
    Triple("加油", "交通", "expense"),
    Triple("购物", "", "expense"),
    Triple("日用", "购物", "expense"),
    Triple("服饰", "购物", "expense"),
    Triple("数码", "购物", "expense"),
    Triple("居住", "", "expense"),
    Triple("房租", "居住", "expense"),
    Triple("水电燃气", "居住", "expense"),
    Triple("娱乐", "", "expense"),
    Triple("影音", "娱乐", "expense"),
    Triple("旅行", "娱乐", "expense"),
    Triple("医疗", "", "expense"),
    Triple("教育", "", "expense"),
    Triple("通讯", "", "expense"),
    Triple("人情", "", "expense"),
    Triple("其他", "", ""),
    Triple("工资", "", "income"),
    Triple("奖金", "", "income"),
    Triple("理财收益", "", "income"),
    Triple("退款", "", "income"),
    Triple("转账", "", ""),
)

/** 内置关键词规则：导入时最先命中的最长模式胜出。 */
private val DEFAULT_RULES = listOf(
    "星巴克" to "餐饮/咖啡",
    "瑞幸" to "餐饮/咖啡",
    "咖啡" to "餐饮/咖啡",
    "麦当劳" to "餐饮",
    "肯德基" to "餐饮",
    "美团外卖" to "餐饮/外卖",
    "饿了么" to "餐饮/外卖",
    "外卖" to "餐饮/外卖",
    "餐厅" to "餐饮",
    "火锅" to "餐饮",
    "滴滴" to "交通/打车",
    "出租" to "交通/打车",
    "打车" to "交通/打车",
    "地铁" to "交通/地铁公交",
    "公交" to "交通/地铁公交",
    "高铁" to "交通",
    "机票" to "交通",
    "航空" to "交通",
    "加油" to "交通/加油",
    "停车" to "交通",
    "通行费" to "交通",
    "淘宝" to "购物",
    "天猫" to "购物",
    "京东" to "购物",
    "拼多多" to "购物",
    "超市" to "购物",
    "便利店" to "购物",
    "房租" to "居住/房租",
    "物业" to "居住",
    "水费" to "居住/水电燃气",
    "电费" to "居住/水电燃气",
    "燃气" to "居住/水电燃气",
    "电影" to "娱乐/影音",
    "游戏" to "娱乐",
    "会员" to "娱乐",
    "旅游" to "娱乐/旅行",
    "医院" to "医疗",
    "药房" to "医疗",
    "学费" to "教育",
    "书" to "教育",
    "话费" to "通讯",
    "工资" to "工资",
)

fun defaultCategories(): List<CategoryDef> = DEFAULT_CATEGORIES.map { (name, parent, kind) ->
    CategoryDef(name = name, parent = parent, kind = kind, icon = builtinIcon(name))
}

fun defaultRules(): List<Rule> = DEFAULT_RULES.mapIndexed { index, (pattern, category) ->
    Rule(id = "seed-$index", pattern = pattern, category = category)
}

/** 空账本带上默认分类与规则，新装应用即可用。 */
fun emptyLedger(): Ledger = Ledger(
    categories = defaultCategories(),
    rules = defaultRules(),
    schemaVersion = Ledger.SCHEMA_VERSION,
)

/**
 * 读入后的整理：
 * - 分类/规则为空时补默认值（老文件或首次启动）
 * - 补上顶级分类缺失的图标
 * - 过滤掉 id 为空的行
 */
fun Ledger.normalized(): Ledger {
    val seededCategories = if (categories.isEmpty()) defaultCategories() else categories
    val withIcons = seededCategories.map { def ->
        if (def.icon.isEmpty() && def.parent.isEmpty()) def.copy(icon = builtinIcon(def.name)) else def
    }
    // 去重（同名同父只留一个），保持原顺序
    val deduped = withIcons.distinctBy { it.parent + "\u0000" + it.name }
    return copy(
        entries = entries.filter { it.id.isNotEmpty() },
        categories = deduped,
        rules = if (rules.isEmpty()) defaultRules() else rules.filter { it.pattern.isNotEmpty() },
        schemaVersion = Ledger.SCHEMA_VERSION,
    )
}
