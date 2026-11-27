package cn.neeto.stockpot.core.data

import android.content.Context
import android.os.SystemClock
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
import kotlinx.coroutines.flow.first
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

    private val _data = MutableStateFlow(emptyLedger())
    val data: StateFlow<Ledger> = _data.asStateFlow()

    private val _ready = MutableStateFlow(false)

    /**
     * 第一次读库读完了没有。
     *
     * 界面上的「账本还是空的」只能在这个值为 true、并且真的没有账目时出现：
     * 读库之前那个空是「还不知道」，不是「没有」。读 5 万笔要几十上百毫秒，
     * 冷启动的第一帧必定早于它——以前就是把那一帧当成了真没数据。
     */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var saveJob: Job? = null

    /** 读库期间进来的改动（用户刚好记了一笔、或同步先跑完了），读完再套上去。 */
    private val pending = mutableListOf<(Ledger) -> Ledger>()

    /** 护住 [pending]、[isReady] 和 `_data` 的一次性替换，读写都在这一把锁里。 */
    private val lock = Any()

    @Volatile
    private var isReady = false

    /** 数据库里最后写进去的那一版，增量写靠它算出「哪些行变了」。 */
    @Volatile
    private var persisted: Ledger = emptyLedger()

    /**
     * 本机改动的回调：把「改之前」和「改之后」两份都交出去，
     * 同步那边靠它记墓碑、安排推送。云端写回来的改动不会触发这里，
     * 否则同步自己会把自己再推一遍。
     */
    var onLocalChange: ((before: Ledger, after: Ledger) -> Unit)? = null

    /** 数据库文件路径，设置页展示用。 */
    val path: String get() = db.readableDatabase.path

    init {
        // 读库挪到 IO 线程：几万笔账在 Application.onCreate 里同步读，会把这个
        // 冷启动都堵住（主线程被占着，第一帧反而出不来）。谁要用账本，先看 [ready]。
        scope.launch { hydrate() }
    }

    /** 读库完成前先等着。同步要用——不然它可能拿一份还没读出来的空账本去合并。 */
    suspend fun awaitReady() {
        ready.first { it }
    }

    private suspend fun hydrate() {
        val started = SystemClock.elapsedRealtime()
        val loaded = runCatching { loadInitial() }
            .onFailure { Log.e(TAG, "打开账本数据库失败", it) }
            .getOrElse { emptyLedger() }

        // 全新安装：库是空的，先把默认分类和规则写进去，下次启动就不用再写一遍。
        // 从老 JSON 迁上来的那份在 loadInitial 里已经落过库，这里不会重复。
        if (runCatching { db.isEmpty() }.getOrDefault(false)) {
            saveMutex.withLock {
                runCatching { db.writeAll(loaded) }
                    .onFailure { Log.e(TAG, "初始化账本数据库失败", it) }
            }
        }

        val queued = synchronized(lock) {
            var base = loaded
            for (change in pending) base = change(base)
            val had = pending.isNotEmpty()
            pending.clear()
            persisted = loaded
            isReady = true
            _data.value = base
            _ready.value = true
            had
        }
        val cost = SystemClock.elapsedRealtime() - started
        Log.i(TAG, "读到 ${loaded.entries.size} 笔账，用时 ${cost}ms" + if (queued) "（期间有改动，已套上）" else "")
        // 读库期间进来的改动还没落盘，补一次
        if (queued) scheduleSave()
    }

    private fun loadInitial(): Ledger {
        val stored = runCatching { db.load() }.onFailure { Log.e(TAG, "打开账本数据库失败", it) }.getOrNull()
        val hasRows = runCatching { !db.isEmpty() }.getOrDefault(false)
        if (stored != null && hasRows) {
            val cleaned = stored.normalized()
            // 载入时收拾掉的东西（比如找不到股票的孤儿持仓）顺手写回库，
            // 不然每次启动都要再收拾一遍，推上云端的那份也一直带着它
            if (cleaned != stored) {
                runCatching { db.writeDiff(stored, cleaned) }
                    .onFailure { Log.w(TAG, "整理后的账本写回失败", it) }
            }
            return cleaned
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
        synchronized(lock) {
            if (!isReady) {
                // 库还没读出来：先记下这么改，读完套到读出来的那份上。
                // 直接拿空账本算的话，这一笔会把库里原有的账全「覆盖」掉。
                pending.add(block)
                val before = _data.value
                val next = block(before)
                if (next !== before) _data.value = next
                return
            }
        }
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
        val next = ledger.normalized()
        synchronized(lock) {
            if (!isReady) {
                pending.add { next }
                _data.value = next
                return
            }
        }
        val before = _data.value
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
        // 库还没读出来就别写：手上这份是空的，一写就把库里的账全删了
        if (!isReady) return@withLock
        runCatching {
            val previous = persisted
            db.writeDiff(previous, data)
            persisted = data
        }.onFailure { Log.e(TAG, "保存账本失败（${data.entries.size} 笔）", it) }
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
