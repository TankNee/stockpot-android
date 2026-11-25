package cn.neeto.stockpot.core.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import cn.neeto.stockpot.core.model.CategoryDef
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Holding
import cn.neeto.stockpot.core.model.ImportRecord
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Project
import cn.neeto.stockpot.core.model.Rule
import cn.neeto.stockpot.core.model.Stock
import cn.neeto.stockpot.core.model.StockAccount
import cn.neeto.stockpot.core.model.Wallet

/**
 * 账本落在 SQLite 里。
 *
 * 之前整份账本是一份 JSON 文件，每次改动都重写全文——几千行的时候要序列化整棵树，
 * 写盘时还要留一份滚动备份。换成表以后：一次编辑只碰动过的那几行，
 * 汇总、按月筛选这类以后可以直接交给 SQL，不用每次把全部账目读进内存再算。
 *
 * 两张表存的是「列表顺序」而不是数据：分类树、规则、账户候选、忽略名单都是有序的小表，
 * 顺序变了就整表重写一遍，比逐行对 position 便宜。
 *
 * 只有 `entries` 走增量：按 id 比对新旧两份，只 upsert 改过的、只删真的没了的。
 */
class LedgerDb(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE entries (
                id TEXT PRIMARY KEY NOT NULL,
                date TEXT NOT NULL,
                time TEXT NOT NULL,
                kind TEXT NOT NULL,
                amount INTEGER NOT NULL,
                category TEXT NOT NULL,
                account TEXT NOT NULL,
                counterparty TEXT NOT NULL,
                note TEXT NOT NULL,
                source TEXT NOT NULL,
                external_id TEXT NOT NULL,
                excluded INTEGER NOT NULL,
                archived INTEGER NOT NULL,
                refunded_amount INTEGER NOT NULL,
                refunded_at TEXT NOT NULL,
                refund_link_id TEXT NOT NULL,
                category_locked INTEGER NOT NULL,
                project_id TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX entries_date ON entries(date)")
        db.execSQL("CREATE INDEX entries_category ON entries(category)")

        db.execSQL(
            """
            CREATE TABLE categories (
                name TEXT NOT NULL,
                parent TEXT NOT NULL,
                kind TEXT NOT NULL,
                icon TEXT NOT NULL,
                description TEXT NOT NULL,
                position INTEGER NOT NULL,
                PRIMARY KEY (parent, name)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE TABLE rules (id TEXT PRIMARY KEY NOT NULL, pattern TEXT NOT NULL, category TEXT NOT NULL, position INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE accounts (name TEXT PRIMARY KEY NOT NULL, position INTEGER NOT NULL)")
        db.execSQL(
            """
            CREATE TABLE imports (
                id TEXT PRIMARY KEY NOT NULL,
                file TEXT NOT NULL,
                source TEXT NOT NULL,
                added INTEGER NOT NULL,
                skipped INTEGER NOT NULL,
                imported_at TEXT NOT NULL,
                position INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE wallets (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                kind TEXT NOT NULL,
                institution TEXT NOT NULL,
                balance INTEGER NOT NULL,
                note TEXT NOT NULL,
                count_in_assets INTEGER NOT NULL,
                selectable INTEGER NOT NULL,
                updated_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE projects (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                note TEXT NOT NULL,
                start_date TEXT NOT NULL,
                end_date TEXT NOT NULL,
                archived INTEGER NOT NULL,
                created_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE TABLE ignored_duplicates (value TEXT PRIMARY KEY NOT NULL, position INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE ignored_repayments (value TEXT PRIMARY KEY NOT NULL, position INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE stocks (id TEXT PRIMARY KEY NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, market TEXT NOT NULL, updated_at TEXT NOT NULL)")
        db.execSQL(
            """
            CREATE TABLE stock_accounts (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                note TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE holdings (
                account_id TEXT NOT NULL,
                stock_id TEXT NOT NULL,
                cost_e4 INTEGER NOT NULL,
                shares REAL NOT NULL,
                PRIMARY KEY (account_id, stock_id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
    }

    /**
     * 目前是第 2 版：第 1 版 holdings.shares 声明成 INTEGER，
     * 场外基金的份额是小数（1234.56 份），读的时候会被告成整数。
     * SQLite 是动态类型，旧库那一列照样能存 REAL，不用 ALTER，
     * 只把 `cursor.long` 换成 `getDouble` 就够。
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 -> v2 只改了 shares 的读取方式，旧数据不用搬
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // 外键先开着：holdings 指到 stock_accounts，将来往上加更容易
        db.setForeignKeyConstraintsEnabled(true)
    }

    /* ------------------------------ 读 ------------------------------ */

    fun load(): Ledger {
        val db = readableDatabase
        return Ledger(
            entries = db.rawQuery("SELECT * FROM entries ORDER BY date, time, id", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.toEntry()) }
            },
            categories = db.rawQuery("SELECT * FROM categories ORDER BY position", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            CategoryDef(
                                name = cursor.text("name"),
                                parent = cursor.text("parent"),
                                kind = cursor.text("kind"),
                                icon = cursor.text("icon"),
                                description = cursor.text("description"),
                            ),
                        )
                    }
                }
            },
            accounts = db.rawQuery("SELECT name FROM accounts ORDER BY position", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            },
            imports = db.rawQuery("SELECT * FROM imports ORDER BY position", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            ImportRecord(
                                id = cursor.text("id"),
                                file = cursor.text("file"),
                                source = cursor.text("source"),
                                added = cursor.int("added"),
                                skipped = cursor.int("skipped"),
                                importedAt = cursor.text("imported_at"),
                            ),
                        )
                    }
                }
            },
            wallets = db.rawQuery("SELECT * FROM wallets", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            Wallet(
                                id = cursor.text("id"),
                                name = cursor.text("name"),
                                kind = cursor.text("kind"),
                                institution = cursor.text("institution"),
                                balance = cursor.long("balance"),
                                note = cursor.text("note"),
                                countInAssets = cursor.bool("count_in_assets"),
                                selectable = cursor.bool("selectable"),
                                updatedAt = cursor.text("updated_at"),
                            ),
                        )
                    }
                }
            },
            projects = db.rawQuery("SELECT * FROM projects", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            Project(
                                id = cursor.text("id"),
                                name = cursor.text("name"),
                                note = cursor.text("note"),
                                startDate = cursor.text("start_date"),
                                endDate = cursor.text("end_date"),
                                archived = cursor.bool("archived"),
                                createdAt = cursor.text("created_at"),
                            ),
                        )
                    }
                }
            },
            rules = db.rawQuery("SELECT * FROM rules ORDER BY position", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            Rule(
                                id = cursor.text("id"),
                                pattern = cursor.text("pattern"),
                                category = cursor.text("category"),
                            ),
                        )
                    }
                }
            },
            ignoredDuplicates = db.rawQuery("SELECT value FROM ignored_duplicates ORDER BY position", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            },
            ignoredRepayments = db.rawQuery("SELECT value FROM ignored_repayments ORDER BY position", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            },
            stocks = db.rawQuery("SELECT * FROM stocks", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            Stock(
                                id = cursor.text("id"),
                                code = cursor.text("code"),
                                name = cursor.text("name"),
                                market = cursor.text("market"),
                                updatedAt = cursor.text("updated_at"),
                            ),
                        )
                    }
                }
            },
            stockAccounts = db.rawQuery("SELECT * FROM stock_accounts", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            StockAccount(
                                id = cursor.text("id"),
                                name = cursor.text("name"),
                                note = cursor.text("note"),
                                updatedAt = cursor.text("updated_at"),
                            ),
                        )
                    }
                }
            },
            schemaVersion = Ledger.SCHEMA_VERSION,
        ).let { ledger ->
            // 持仓单独一张表，读完账户再挂上去：一个账户里的票数不多，一次查全表就够
            val holdings = mutableMapOf<String, MutableMap<String, Holding>>()
            db.rawQuery("SELECT * FROM holdings", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val account = cursor.text("account_id")
                    holdings.getOrPut(account) { mutableMapOf() }[cursor.text("stock_id")] = Holding(
                        costE4 = cursor.long("cost_e4"),
                        shares = cursor.double("shares"),
                    )
                }
            }
            if (holdings.isEmpty()) {
                ledger
            } else {
                ledger.copy(
                    stockAccounts = ledger.stockAccounts.map { account ->
                        holdings[account.id]?.let { account.copy(entries = it.toMap()) } ?: account
                    },
                )
            }
        }
    }

    /** 数据库里到底有没有东西。首次启动要据此决定要不要去捞老的 ledger.json。 */
    fun isEmpty(): Boolean {
        val db = readableDatabase
        db.rawQuery("SELECT (SELECT COUNT(*) FROM entries) + (SELECT COUNT(*) FROM categories)", null).use { cursor ->
            return cursor.moveToFirst() && cursor.getLong(0) == 0L
        }
    }

    /* ------------------------------ 写 ------------------------------ */

    /** 整份覆盖：老 JSON 迁移、载入示例这类一次性的写入。 */
    fun writeAll(ledger: Ledger) = transaction { db -> replaceEverything(db, ledger) }

    /**
     * 增量写：账目按 id 比对，只动真的有变化的行；
     * 其余小表只在内容变了的时候整表重写。
     */
    fun writeDiff(before: Ledger, after: Ledger) = transaction { db ->
        if (before.entries != after.entries) diffEntries(db, before.entries, after.entries)
        if (before.categories != after.categories) {
            db.delete("categories", null, null)
            after.categories.forEachIndexed { index, def ->
                db.insertOrThrow(
                    "categories",
                    null,
                    ContentValues().apply {
                        put("name", def.name)
                        put("parent", def.parent)
                        put("kind", def.kind)
                        put("icon", def.icon)
                        put("description", def.description)
                        put("position", index)
                    },
                )
            }
        }
        if (before.rules != after.rules) {
            db.delete("rules", null, null)
            after.rules.forEachIndexed { index, rule ->
                db.insertOrThrow(
                    "rules",
                    null,
                    ContentValues().apply {
                        put("id", rule.id)
                        put("pattern", rule.pattern)
                        put("category", rule.category)
                        put("position", index)
                    },
                )
            }
        }
        if (before.accounts != after.accounts) {
            db.delete("accounts", null, null)
            after.accounts.forEachIndexed { index, name ->
                db.insertOrThrow("accounts", null, ContentValues().apply {
                    put("name", name)
                    put("position", index)
                })
            }
        }
        if (before.imports != after.imports) {
            db.delete("imports", null, null)
            after.imports.forEachIndexed { index, record ->
                db.insertOrThrow(
                    "imports",
                    null,
                    ContentValues().apply {
                        put("id", record.id)
                        put("file", record.file)
                        put("source", record.source)
                        put("added", record.added)
                        put("skipped", record.skipped)
                        put("imported_at", record.importedAt)
                        put("position", index)
                    },
                )
            }
        }
        if (before.wallets != after.wallets) {
            db.delete("wallets", null, null)
            after.wallets.forEach { putWallet(db, it) }
        }
        if (before.projects != after.projects) {
            db.delete("projects", null, null)
            after.projects.forEach { putProject(db, it) }
        }
        if (before.ignoredDuplicates != after.ignoredDuplicates) {
            db.delete("ignored_duplicates", null, null)
            after.ignoredDuplicates.forEachIndexed { index, value ->
                db.insertOrThrow("ignored_duplicates", null, ContentValues().apply {
                    put("value", value)
                    put("position", index)
                })
            }
        }
        if (before.ignoredRepayments != after.ignoredRepayments) {
            db.delete("ignored_repayments", null, null)
            after.ignoredRepayments.forEachIndexed { index, value ->
                db.insertOrThrow("ignored_repayments", null, ContentValues().apply {
                    put("value", value)
                    put("position", index)
                })
            }
        }
        if (before.stocks != after.stocks) {
            db.delete("stocks", null, null)
            after.stocks.forEach { putStock(db, it) }
        }
        if (before.stockAccounts != after.stockAccounts) {
            db.delete("holdings", null, null)
            db.delete("stock_accounts", null, null)
            after.stockAccounts.forEach { putStockAccount(db, it) }
        }
    }

    private fun replaceEverything(db: SQLiteDatabase, ledger: Ledger) {
        for (table in TABLES) db.delete(table, null, null)
        ledger.entries.forEach { putEntry(db, it) }
        ledger.categories.forEachIndexed { index, def ->
            db.insertOrThrow(
                "categories",
                null,
                ContentValues().apply {
                    put("name", def.name)
                    put("parent", def.parent)
                    put("kind", def.kind)
                    put("icon", def.icon)
                    put("description", def.description)
                    put("position", index)
                },
            )
        }
        ledger.rules.forEachIndexed { index, rule ->
            db.insertOrThrow(
                "rules",
                null,
                ContentValues().apply {
                    put("id", rule.id)
                    put("pattern", rule.pattern)
                    put("category", rule.category)
                    put("position", index)
                },
            )
        }
        ledger.accounts.forEachIndexed { index, name ->
            db.insertOrThrow("accounts", null, ContentValues().apply {
                put("name", name)
                put("position", index)
            })
        }
        ledger.imports.forEachIndexed { index, record ->
            db.insertOrThrow(
                "imports",
                null,
                ContentValues().apply {
                    put("id", record.id)
                    put("file", record.file)
                    put("source", record.source)
                    put("added", record.added)
                    put("skipped", record.skipped)
                    put("imported_at", record.importedAt)
                    put("position", index)
                },
            )
        }
        ledger.wallets.forEach { putWallet(db, it) }
        ledger.projects.forEach { putProject(db, it) }
        ledger.ignoredDuplicates.forEachIndexed { index, value ->
            db.insertOrThrow("ignored_duplicates", null, ContentValues().apply {
                put("value", value)
                put("position", index)
            })
        }
        ledger.ignoredRepayments.forEachIndexed { index, value ->
            db.insertOrThrow("ignored_repayments", null, ContentValues().apply {
                put("value", value)
                put("position", index)
            })
        }
        ledger.stocks.forEach { putStock(db, it) }
        ledger.stockAccounts.forEach { putStockAccount(db, it) }
        db.insertWithOnConflict(
            "meta",
            null,
            ContentValues().apply {
                put("key", "schemaVersion")
                put("value", ledger.schemaVersion.toString())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun diffEntries(db: SQLiteDatabase, before: List<Entry>, after: List<Entry>) {
        val old = before.associateBy { it.id }
        val current = after.associateBy { it.id }
        for (entry in after) {
            if (old[entry.id] != entry) putEntry(db, entry)
        }
        val removed = old.keys - current.keys
        for (id in removed) db.delete("entries", "id = ?", arrayOf(id))
    }

    private fun putEntry(db: SQLiteDatabase, entry: Entry) {
        db.insertWithOnConflict(
            "entries",
            null,
            ContentValues().apply {
                put("id", entry.id)
                put("date", entry.date)
                put("time", entry.time)
                put("kind", entry.kind)
                put("amount", entry.amount)
                put("category", entry.category)
                put("account", entry.account)
                put("counterparty", entry.counterparty)
                put("note", entry.note)
                put("source", entry.source)
                put("external_id", entry.externalId)
                put("excluded", if (entry.excluded) 1 else 0)
                put("archived", if (entry.archived) 1 else 0)
                put("refunded_amount", entry.refundedAmount)
                put("refunded_at", entry.refundedAt)
                put("refund_link_id", entry.refundLinkId)
                put("category_locked", if (entry.categoryLocked) 1 else 0)
                put("project_id", entry.projectId)
                put("created_at", entry.createdAt)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun putWallet(db: SQLiteDatabase, wallet: Wallet) {
        db.insertWithOnConflict(
            "wallets",
            null,
            ContentValues().apply {
                put("id", wallet.id)
                put("name", wallet.name)
                put("kind", wallet.kind)
                put("institution", wallet.institution)
                put("balance", wallet.balance)
                put("note", wallet.note)
                put("count_in_assets", if (wallet.countInAssets) 1 else 0)
                put("selectable", if (wallet.selectable) 1 else 0)
                put("updated_at", wallet.updatedAt)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun putProject(db: SQLiteDatabase, project: Project) {
        db.insertWithOnConflict(
            "projects",
            null,
            ContentValues().apply {
                put("id", project.id)
                put("name", project.name)
                put("note", project.note)
                put("start_date", project.startDate)
                put("end_date", project.endDate)
                put("archived", if (project.archived) 1 else 0)
                put("created_at", project.createdAt)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun putStock(db: SQLiteDatabase, stock: Stock) {
        db.insertWithOnConflict(
            "stocks",
            null,
            ContentValues().apply {
                put("id", stock.id)
                put("code", stock.code)
                put("name", stock.name)
                put("market", stock.market)
                put("updated_at", stock.updatedAt)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun putStockAccount(db: SQLiteDatabase, account: StockAccount) {
        db.insertWithOnConflict(
            "stock_accounts",
            null,
            ContentValues().apply {
                put("id", account.id)
                put("name", account.name)
                put("note", account.note)
                put("updated_at", account.updatedAt)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        account.entries.forEach { (stockId, holding) ->
            db.insertWithOnConflict(
                "holdings",
                null,
                ContentValues().apply {
                    put("account_id", account.id)
                    put("stock_id", stockId)
                    put("cost_e4", holding.costE4)
                    put("shares", holding.shares)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    private inline fun transaction(block: (SQLiteDatabase) -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            block(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun Cursor.text(column: String): String = getString(getColumnIndexOrThrow(column)).orEmpty()
    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
    private fun Cursor.double(column: String): Double = getDouble(getColumnIndexOrThrow(column))
    private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
    private fun Cursor.bool(column: String): Boolean = getInt(getColumnIndexOrThrow(column)) != 0

    private fun Cursor.toEntry(): Entry = Entry(
        id = text("id"),
        date = text("date"),
        time = text("time"),
        kind = text("kind"),
        amount = long("amount"),
        category = text("category"),
        account = text("account"),
        counterparty = text("counterparty"),
        note = text("note"),
        source = text("source"),
        externalId = text("external_id"),
        excluded = bool("excluded"),
        archived = bool("archived"),
        refundedAmount = long("refunded_amount"),
        refundedAt = text("refunded_at"),
        refundLinkId = text("refund_link_id"),
        categoryLocked = bool("category_locked"),
        projectId = text("project_id"),
        createdAt = text("created_at"),
    )

    companion object {
        /** 应用私有目录下的文件，`getDatabasePath` 拼出完整路径。 */
        const val NAME = "ledger.db"
        const val VERSION = 2

        private val TABLES = listOf(
            "entries",
            "categories",
            "rules",
            "accounts",
            "imports",
            "wallets",
            "projects",
            "ignored_duplicates",
            "ignored_repayments",
            "stocks",
            "holdings",
            "stock_accounts",
        )
    }
}
