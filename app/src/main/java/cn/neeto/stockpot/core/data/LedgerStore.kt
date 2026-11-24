package cn.neeto.stockpot.core.data

import android.content.Context
import android.util.Log
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.emptyLedger
import cn.neeto.stockpot.core.model.normalized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 本地优先的存储：应用私有目录下的一份 SQLite 数据库（`ledger.db`）。
 *
 * 内存里始终留一份完整的 [Ledger]，界面照旧直接读它；改动落盘是增量的——
 * 只写这次真的动过的行，不是把整棵树重新序列化一遍。
 *
 * 早先版本把账本存成 `ledger.json`。第一次启动新版本时会把那份读进来灌进数据库，
 * 然后把原文件改名成 `ledger.imported.json` 留个档，不动它也不删它。
 *
 * 导出仍然是 JSON：`ledger.json` 是两端（桌面端、手机）交换用的格式，
 * 数据库只负责「存在本机」这件事。
 */
class LedgerStore(context: Context) {

    private val dir: File = context.filesDir
    private val legacyFile = File(dir, "ledger.json")
    private val legacyBackup = File(dir, "ledger.bak.json")

    private val db = LedgerDb(context)

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        isLenient = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()

    private val _data = MutableStateFlow(loadInitial())
    val data: StateFlow<Ledger> = _data.asStateFlow()

    private var saveJob: Job? = null

    /** 数据库里最后写进去的那一版，增量写靠它算出「哪些行变了」。 */
    @Volatile
    private var persisted: Ledger = _data.value

    /**
     * 本机改动的回调：把「改之前」和「改之后」两份都交出去，
     * 同步那边靠它记墓碑、安排推送。云端写回来的改动不会触发这里，
     * 否则同步自己会把自己再推一遍。
     */
    var onLocalChange: ((before: Ledger, after: Ledger) -> Unit)? = null

    /** 数据库文件路径，设置页展示用。 */
    val path: String get() = db.readableDatabase.path

    init {
        // 全新安装：库是空的，先把默认分类和规则写进去。
        // 从老 JSON 迁上来的那份在 loadInitial 里已经落过库，这里不会重复。
        if (runCatching { db.isEmpty() }.getOrDefault(false)) {
            scope.launch {
                saveMutex.withLock {
                    runCatching {
                        db.writeAll(_data.value)
                        persisted = _data.value
                    }.onFailure { Log.e(TAG, "初始化账本数据库失败", it) }
                }
            }
        }
    }

    private fun loadInitial(): Ledger {
        val stored = runCatching { db.load() }.onFailure { Log.e(TAG, "打开账本数据库失败", it) }.getOrNull()
        val hasRows = runCatching { !db.isEmpty() }.getOrDefault(false)
        if (stored != null && hasRows) {
            return stored.normalized()
        }
        // 空库：可能是新装，也可能是从 0.16 及以前升上来——老版本留着一份 JSON
        val legacy = readLegacy(legacyFile) ?: readLegacy(legacyBackup)
        if (legacy != null) {
            val ledger = legacy.normalized()
            runCatching { db.writeAll(ledger) }
                .onFailure { Log.e(TAG, "导入老账本失败", it) }
                .onSuccess {
                    // 改名而不是删除：万一迁移出了岔子，用户还能从文件里捞回来
                    runCatching { legacyFile.renameTo(File(dir, "ledger.imported.json")) }
                }
            return ledger
        }
        return emptyLedger()
    }

    private fun readLegacy(source: File): Ledger? {
        if (!source.exists()) return null
        return runCatching {
            json.decodeFromString<Ledger>(source.readText())
        }.onFailure { Log.w(TAG, "读取 ${source.name} 失败", it) }.getOrNull()
    }

    /** 所有修改都走这里：先更新内存快照，再防抖落盘。 */
    fun update(block: (Ledger) -> Ledger) {
        val before = _data.value
        val next = block(before)
        if (next === before) return
        _data.value = next
        scheduleSave()
        onLocalChange?.invoke(before, next)
    }

    /**
     * 整份替换：导入、合并、载入示例、删除撤销都走这里。
     * [fromRemote] 是云端写回来的那份——它不该再被当成"本地改动"推上去。
     */
    fun replaceAll(ledger: Ledger, fromRemote: Boolean = false) {
        val before = _data.value
        val next = ledger.normalized()
        if (next == before) return
        _data.value = next
        scheduleSave()
        if (!fromRemote) onLocalChange?.invoke(before, next)
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(180)
            persist(_data.value)
        }
    }

    private suspend fun persist(data: Ledger) = saveMutex.withLock {
        runCatching {
            val previous = persisted
            db.writeDiff(previous, data)
            persisted = data
        }.onFailure { Log.e(TAG, "保存账本失败", it) }
    }

    fun flushAsync() {
        scope.launch { persist(_data.value) }
    }

    /** 切到后台时调用：和后台写库共用同一把锁，不会互相踩。 */
    fun flushBlocking() {
        kotlinx.coroutines.runBlocking { persist(_data.value) }
    }

    /** 导出用：和桌面端互通的那份 JSON 文本。 */
    fun exportJson(): String = json.encodeToString(Ledger.serializer(), _data.value)

    fun parseJson(text: String): Ledger? =
        runCatching { json.decodeFromString<Ledger>(text).normalized() }.getOrNull()

    private companion object {
        const val TAG = "LedgerStore"
    }
}
